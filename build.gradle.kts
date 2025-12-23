import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
  id("com.android.application") version "8.5.2" apply false
  id("com.android.library") version "8.5.2" apply false
  id("org.jetbrains.kotlin.android") version "2.0.20" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
  id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
  id("com.diffplug.spotless")
  id("io.gitlab.arturbosch.detekt") version "1.23.6"
  id("org.jetbrains.kotlinx.kover") version "0.7.6"
  id("com.google.devtools.ksp") version "2.0.20-1.0.24" apply false
  id("com.google.dagger.hilt.android") version "2.52" apply false
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

fun Project.registerAdbDevicePreflight() = tasks.register("adbDevicePreflight") {
  group = "verification"
  description = "Fail fast if no online ADB devices are available for connectedAndroidTest."

  doLast {
    val adbExecutable = providers.environmentVariable("ADB").orNull ?: "adb"
    val output = ByteArrayOutputStream()
    val error = ByteArrayOutputStream()
    val result =
      try {
        exec {
          commandLine(adbExecutable, "devices")
          standardOutput = output
          errorOutput = error
          isIgnoreExitValue = true
        }
      } catch (ex: Exception) {
        throw GradleException(
          "Failed to run '$adbExecutable devices'. Ensure Android SDK platform-tools are installed and adb is on PATH.",
          ex,
        )
      }

    val outputText = output.toString().trim()
    if (result.exitValue != 0) {
      val errorText = error.toString().trim()
      throw GradleException(
        "adb devices failed (exit ${result.exitValue}).\n${errorText.ifBlank { outputText }}",
      )
    }

    val lines =
      outputText
        .lineSequence()
        .drop(1)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()

    val online = lines.filter { it.endsWith("\tdevice") && !it.contains("\toffline") }
    val offline = lines.filter { it.endsWith("\toffline") }
    val unauthorized = lines.filter { it.endsWith("\tunauthorized") }

    if (online.isEmpty()) {
      val diagnostics = buildString {
        appendLine("No online ADB devices detected.")
        if (lines.isNotEmpty()) {
          appendLine("adb devices output:")
          lines.forEach { appendLine("  $it") }
        }
        appendLine()
        appendLine("Fixes:")
        appendLine("  - adb kill-server && adb start-server")
        appendLine("  - Restart the emulator or reconnect the device")
        appendLine("  - Ensure the device is authorized (not 'unauthorized')")
      }
      throw GradleException(diagnostics)
    }

    if (offline.isNotEmpty() || unauthorized.isNotEmpty()) {
      logger.warn(
        buildString {
          appendLine("⚠️  ADB reported offline/unauthorized devices.")
          offline.forEach { appendLine("  offline: $it") }
          unauthorized.forEach { appendLine("  unauthorized: $it") }
        },
      )
    }
  }
}

subprojects {
  configurations.all {
    resolutionStrategy.eachDependency {
      if (requested.group == "org.xerial" && requested.name == "sqlite-jdbc") {
        // Можно взять актуальную версию, например 3.46.0.0
        useVersion("3.46.0.0")
        because("Room schema verifier + Java 21 на Windows требуют более свежий sqlite-jdbc")
      }
      if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-coroutines-")) {
        useVersion("1.8.1")
        because("Align coroutines artifacts for unit tests and dispatchers (StandardTestDispatcher)")
      }
    }
  }
  plugins.withId("org.jetbrains.kotlin.android") {
    apply(plugin = "org.jetbrains.kotlinx.kover")
  }

  plugins.withId("org.jetbrains.kotlin.jvm") {
    apply(plugin = "org.jetbrains.kotlinx.kover")
  }

  plugins.withId("com.android.application") {
    val preflight = registerAdbDevicePreflight()
    tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }
      .configureEach { dependsOn(preflight) }
  }

  plugins.withId("com.android.library") {
    val preflight = registerAdbDevicePreflight()
    tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }
      .configureEach { dependsOn(preflight) }
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
