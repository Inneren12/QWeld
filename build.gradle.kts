import groovy.json.JsonSlurper
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

buildscript {
  repositories {
    google()
    mavenCentral()
  }
  dependencies {
    classpath("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
  }
}

@OptIn(ExperimentalSerializationApi::class)
private class QuestionAssetTools(
  private val rootDir: File,
  private val logger: Logger,
) {
  private val jsonPretty =
    Json {
      prettyPrint = true
      prettyPrintIndent = "  "
      ignoreUnknownKeys = true
    }

  private val jsonLenient = Json { ignoreUnknownKeys = true }

  private val blueprintPath = File(rootDir, "app-android/src/main/assets/blueprints/welder_ip_sk_202404.json")

  private fun blueprintTasks(): List<String> {
    if (!blueprintPath.exists()) {
      throw GradleException("Missing blueprint: ${blueprintPath.relativeTo(rootDir)}")
    }

    val root = jsonLenient.parseToJsonElement(blueprintPath.readText()).jsonObject
    val blocks = (root["blocks"] as? JsonArray).orEmpty()
    val tasks = mutableListOf<String>()
    blocks.forEach { blockElement ->
      val block = blockElement as? JsonObject ?: return@forEach
      val blockTasks = block["tasks"] as? JsonArray ?: return@forEach
      blockTasks.forEach { taskElement ->
        val task = taskElement as? JsonObject ?: return@forEach
        val id = task["id"] as? JsonPrimitive
        id?.contentOrNull?.takeIf { it.isNotBlank() }?.let { tasks.add(it) }
      }
    }
    if (tasks.isEmpty()) {
      throw GradleException("No tasks defined in blueprint: ${blueprintPath.relativeTo(rootDir)}")
    }
    return tasks
  }

  private fun availableTaskIds(locale: String): Set<String> {
    val taskDir = File(rootDir, "app-android/src/main/assets/questions/$locale/tasks")
    val fromAggregated = taskDir.listFiles()?.filter { it.isFile && it.extension == "json" }?.map {
      it.nameWithoutExtension
    }?.toSet().orEmpty()

    val rawRoot = File(rootDir, "content/questions/$locale")
    val fromRaw = rawRoot.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet().orEmpty()

    return fromAggregated + fromRaw
  }

  private fun loadTaskArray(locale: String, taskId: String): JsonArray? {
    val aggregated = File(rootDir, "app-android/src/main/assets/questions/$locale/tasks/$taskId.json")
    parseArray(aggregated)?.let { return it }

    val rawDir = File(rootDir, "content/questions/$locale/$taskId")
    val fromRaw = parseRawQuestions(rawDir)
    if (fromRaw != null) {
      writeArray(aggregated, fromRaw)
    }
    return fromRaw
  }

  private fun parseArray(file: File): JsonArray? {
    if (!file.exists()) return null
    return runCatching { jsonLenient.parseToJsonElement(file.readText()).jsonArray }.getOrNull()
  }

  private fun parseRawQuestions(dir: File): JsonArray? {
    if (!dir.exists() || !dir.isDirectory) return null
    val files = dir.listFiles()?.filter { it.isFile && it.extension == "json" } ?: return null
    if (files.isEmpty()) return null

    val elements = files.mapNotNull { file ->
      runCatching { jsonLenient.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
        ?.let { file to it }
    }

    val sorted = elements.sortedWith(
      compareBy({ it.second["id"]?.jsonPrimitive?.contentOrNull ?: "" }, { it.first.name }),
    )
    return JsonArray(sorted.map { it.second })
  }

  private fun writeArray(file: File, payload: JsonArray) {
    file.parentFile?.mkdirs()
    file.writeText(jsonPretty.encodeToString(JsonArray.serializer(), payload) + "\n")
  }

  private fun regenerateBank(locale: String): List<String> {
    val blueprintTasks = blueprintTasks()
    val available = availableTaskIds(locale)

    val missingFromBlueprint = blueprintTasks.filter { it !in available }
    if (missingFromBlueprint.isNotEmpty()) {
      logger.warn("[questions:$locale] Missing tasks from blueprint: ${missingFromBlueprint.joinToString()}")
    }

    val extraTasks = (available - blueprintTasks.toSet()).sorted()
    if (extraTasks.isNotEmpty()) {
      logger.warn("[questions:$locale] Tasks not listed in blueprint: ${extraTasks.joinToString()}")
    }

    val orderedTasks = blueprintTasks.filter { it in available } + extraTasks
    val bankEntries = mutableListOf<JsonElement>()
    orderedTasks.forEach { taskId ->
      val questions = loadTaskArray(locale, taskId)
      if (questions == null) {
        logger.warn("[questions:$locale] Skipping task $taskId — no data found")
        return@forEach
      }
      writeArray(File(rootDir, "app-android/src/main/assets/questions/$locale/tasks/$taskId.json"), questions)
      bankEntries.addAll(questions)
    }

    val bankArray = JsonArray(bankEntries)
    val bankFile = File(rootDir, "app-android/src/main/assets/questions/$locale/bank.v1.json")
    writeArray(bankFile, bankArray)
    logger.lifecycle(
      "[questions:$locale] bank generated: ${bankEntries.size} questions across ${orderedTasks.size} tasks",
    )
    return orderedTasks
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = file.readBytes()
    digest.update(bytes)
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
  }

  private fun updateIntegrityIndex(locale: String, blueprintId: String, bankVersion: String, tasks: List<String>) {
    val indexFile = File(rootDir, "app-android/src/main/assets/questions/$locale/index.json")
    val labelsFile = File(rootDir, "app-android/src/main/assets/questions/$locale/meta/task_labels.json")
    val bankFile = File(rootDir, "app-android/src/main/assets/questions/$locale/bank.v1.json")

    if (!labelsFile.exists()) {
      throw GradleException("Missing task labels for $locale: ${labelsFile.relativeTo(rootDir)}")
    }
    if (!bankFile.exists()) {
      throw GradleException("Missing bank for $locale: ${bankFile.relativeTo(rootDir)}")
    }

    val existing = indexFile.takeIf { it.exists() }?.let { runCatching { jsonLenient.parseToJsonElement(it.readText()).jsonObject }.getOrNull() }
    val filesObj = LinkedHashMap<String, JsonElement>()
    existing?.get("files")?.jsonObject?.let { obj -> filesObj.putAll(obj) }

    val paths = mutableListOf<String>()
    paths += "questions/$locale/meta/task_labels.json"
    paths += "questions/$locale/bank.v1.json"
    tasks.forEach { taskId -> paths += "questions/$locale/tasks/$taskId.json" }

    paths.forEach { path ->
      val file = File(rootDir, "app-android/src/main/assets/$path")
      if (!file.exists()) {
        throw GradleException("Integrity input missing: ${file.relativeTo(rootDir)}")
      }
      val hash = sha256(file)
      filesObj[path] = JsonObject(mapOf("sha256" to JsonPrimitive(hash)))
    }

    val rootEntries = LinkedHashMap<String, JsonElement>()
    rootEntries.putAll(existing.orEmpty())
    rootEntries["blueprintId"] = JsonPrimitive(blueprintId)
    rootEntries["bankVersion"] = JsonPrimitive(bankVersion)
    rootEntries["files"] = JsonObject(filesObj)

    val updated = JsonObject(rootEntries)
    indexFile.parentFile?.mkdirs()
    indexFile.writeText(jsonPretty.encodeToString(JsonObject.serializer(), updated) + "\n")
    logger.lifecycle("[questions:$locale] integrity index updated: ${filesObj.size} files")
  }

  fun regenerate(locale: String, blueprintId: String, bankVersion: String) {
    val tasks = regenerateBank(locale)
    updateIntegrityIndex(locale, blueprintId, bankVersion, tasks)
  }
}

val generateQuestionBankEn by tasks.registering {
  group = "questions"
  description = "Regenerate EN per-task bundles and bank from content"
  doLast {
    QuestionAssetTools(rootDir, logger)
      .regenerate(locale = "en", blueprintId = "welder_ip_sk_202404", bankVersion = "v1")
  }
}

val generateQuestionBankRu by tasks.registering {
  group = "questions"
  description = "Regenerate RU per-task bundles and bank from content"
  doLast {
    QuestionAssetTools(rootDir, logger)
      .regenerate(locale = "ru", blueprintId = "welder_ip_sk_202404", bankVersion = "v1")
  }
}

tasks.register("generateIntegrityIndexEn") {
  group = "questions"
  description = "Regenerate EN question bank and integrity index"
  dependsOn(generateQuestionBankEn)
}

tasks.register("generateIntegrityIndexRu") {
  group = "questions"
  description = "Regenerate RU question bank and integrity index"
  dependsOn(generateQuestionBankRu)
}

plugins {
  id("com.android.application") version "8.5.2" apply false
  id("com.android.library") version "8.5.2" apply false
  id("org.jetbrains.kotlin.android") version "2.0.20" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
  id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
  id("com.diffplug.spotless")
  id("io.gitlab.arturbosch.detekt") version "1.23.6"
  id("org.jetbrains.kotlinx.kover") version "0.7.6"
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**")
    ktfmt().googleStyle()
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/build/**")
    ktfmt().googleStyle()
  }
}

detekt {
  toolVersion = "1.23.6"
  buildUponDefaultConfig = true
  config.setFrom(files("$rootDir/detekt.yml"))
}

subprojects {
  plugins.withId("org.jetbrains.kotlin.android") {
    apply(plugin = "org.jetbrains.kotlinx.kover")
  }

  plugins.withId("org.jetbrains.kotlin.jvm") {
    apply(plugin = "org.jetbrains.kotlinx.kover")
  }
}

koverReport {
  defaults {
    xml {
      setReportFile(layout.buildDirectory.file("reports/kover/xml/report.xml"))
    }
    verify {
      rule("minimum-line-coverage") {
        bound {
          minValue = 60
        }
      }
    }
  }
}

abstract class GenerateThirdPartyNoticesTask : DefaultTask() {
  @get:InputFile
  abstract val licenseeReport: RegularFileProperty

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @TaskAction
  fun generate() {
    val report = licenseeReport.asFile.get()
    if (!report.exists()) {
      throw GradleException("Licensee report not found. Run :app-android:licensee before generating notices.")
    }

    val parsed = JsonSlurper().parse(report)
    @Suppress("UNCHECKED_CAST")
    val artifacts = when (parsed) {
      is List<*> -> parsed.filterIsInstance<Map<String, Any?>>()
      is Map<*, *> -> (parsed["artifacts"] as? List<Map<String, Any?>>).orEmpty()
      else -> emptyList()
    }

    val entries = artifacts.map { artifact ->
      val groupId = artifact["groupId"] as? String ?: ""
      val artifactId = artifact["artifactId"] as? String ?: ""
      val version = artifact["version"] as? String ?: ""
      val spdxLicenses = (artifact["spdxLicenses"] as? List<Map<String, Any?>>).orEmpty()
        .mapNotNull { it["identifier"] as? String }
      val unknownLicenses = (artifact["unknownLicenses"] as? List<Map<String, Any?>>).orEmpty()
        .mapNotNull { license ->
          (license["name"] as? String)?.takeIf { it.isNotBlank() }
            ?: (license["url"] as? String)
        }
      val licenses = (spdxLicenses + unknownLicenses).filter { it.isNotBlank() }.ifEmpty { listOf("Unknown") }
      Pair("$groupId:$artifactId:$version", licenses.joinToString(separator = ", "))
    }.sortedBy { it.first }

    val builder = StringBuilder()
    builder.appendLine("# Third-party notices")
    builder.appendLine()
    builder.appendLine("This summary is generated from the `:app-android:licensee` compliance report.")
    builder.appendLine()
    builder.appendLine("| Dependency | Licenses |")
    builder.appendLine("| --- | --- |")

    entries.forEach { (coordinate, licenses) ->
      builder.appendLine("| `$coordinate` | $licenses |")
    }

    builder.appendLine()
    builder.appendLine("For the full license reports and CycloneDX SBOM artifacts, refer to the CI run artifacts.")

    outputFile.asFile.get().writeText(builder.toString())
  }
}

val generateThirdPartyNotices by tasks.registering(GenerateThirdPartyNoticesTask::class) {
  group = "documentation"
  description = "Generate THIRD_PARTY_NOTICES.md from licensee report"
  licenseeReport.set(layout.projectDirectory.file("app-android/build/reports/licensee/release/artifacts.json"))
  outputFile.set(layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"))
  dependsOn(":app-android:licensee")
}

val verifyReleaseAssets by tasks.registering {
  group = "verification"
  description = "Verify that localized question bundles ship with index manifests"

  doLast {
    val questionsDir = layout.projectDirectory.dir("app-android/src/main/assets/questions").asFile
    if (!questionsDir.exists()) {
      throw GradleException("Questions assets directory is missing: ${questionsDir.relativeTo(projectDir)}")
    }

    val localeDirs = questionsDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
    if (localeDirs.isEmpty()) {
      throw GradleException("No locale bundles found under ${questionsDir.relativeTo(projectDir)}")
    }

    val slurper = JsonSlurper()
    localeDirs.forEach { localeDir ->
      val indexFile = localeDir.resolve("index.json")
      if (!indexFile.exists()) {
        throw GradleException("Missing index manifest: ${indexFile.relativeTo(projectDir)}")
      }

      val parsed = slurper.parse(indexFile)
      val data = parsed as? Map<*, *> ?: throw GradleException("Index manifest must be an object: ${indexFile.relativeTo(projectDir)}")
      val blueprintId = data["blueprintId"] as? String
      val bankVersion = data["bankVersion"] as? String
      val files = data["files"] as? Map<*, *>

      if (blueprintId.isNullOrBlank()) {
        throw GradleException("Missing blueprintId in ${indexFile.relativeTo(projectDir)}")
      }
      if (bankVersion.isNullOrBlank()) {
        throw GradleException("Missing bankVersion in ${indexFile.relativeTo(projectDir)}")
      }
      if (files.isNullOrEmpty()) {
        throw GradleException("No files declared in ${indexFile.relativeTo(projectDir)}")
      }

      files.forEach { (path, value) ->
        if (path !is String || path.isBlank()) {
          throw GradleException("Invalid file entry in ${indexFile.relativeTo(projectDir)}: $path")
        }
        val hash =
          when (value) {
            is Map<*, *> -> value["sha256"] ?: value["hash"]
            else -> value
          } as? String
        if (hash.isNullOrBlank()) {
          throw GradleException("Missing sha256 for $path in ${indexFile.relativeTo(projectDir)}")
        }
      }
    }
  }
}
