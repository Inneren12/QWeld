import groovy.json.JsonSlurper
import org.gradle.api.GradleException

plugins {
  id("java")
  kotlin("jvm")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("me.champeau.jmh")
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":core-domain"))
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

val reportsDir = layout.buildDirectory.dir("reports/jmh")
val jmhJson = reportsDir.map { it.file("jmh-result.json") }
val jmhCsv = reportsDir.map { it.file("jmh-result.csv") }
val jmhText = reportsDir.map { it.file("jmh-result.txt") }

jmh {
  jvmArgsAppend.set(listOf("-Xms64m", "-Xmx64m"))
  warmupIterations.set(5)
  warmup.set("1s")
  iterations.set(5)
  timeOnIteration.set("1s")
  fork.set(1)
  includes.set(listOf(".*"))
  resultFormat.set("JSON")
  resultsFile.set(jmhJson)
  humanOutputFile.set(jmhText)
}

val generateJmhCsv by
  tasks.registering {
    group = "verification"
    description = "Generate a CSV summary from the JMH JSON output"
    dependsOn(tasks.named("jmh"))
    inputs.file(jmhJson)
    outputs.file(jmhCsv)
    doLast {
      val jsonFile = jmhJson.get().asFile
      if (!jsonFile.exists()) {
        throw GradleException("JMH JSON results not found: ${jsonFile.path}")
      }
      val csvFile = jmhCsv.get().asFile
      val jsonText = jsonFile.readText()
      val parser = JsonSlurper()
      val parsed = parser.parseText(jsonText)
      val rows =
        (parsed as? List<*>)
          ?.mapNotNull { element ->
            val map = element as? Map<*, *> ?: return@mapNotNull null
            val benchmark = map["benchmark"] as? String ?: return@mapNotNull null
            val mode = map["mode"]?.toString() ?: ""
            val metric = map["primaryMetric"] as? Map<*, *> ?: emptyMap<String, Any?>()
            val score = metric["score"]?.toString() ?: ""
            val error = metric["scoreError"]?.toString() ?: ""
            val unit = metric["scoreUnit"]?.toString() ?: ""
            listOf(benchmark, mode, score, error, unit)
          }
          .orEmpty()
      val lines = buildString {
        appendLine("Benchmark,Mode,Score,Score Error (99.9%),Units")
        rows.forEach { row -> appendLine(row.joinToString(",")) }
      }
      csvFile.parentFile.mkdirs()
      csvFile.writeText(lines)
    }
  }

tasks.named("jmh").configure { finalizedBy(generateJmhCsv) }
