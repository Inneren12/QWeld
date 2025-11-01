import groovy.json.JsonSlurper
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
