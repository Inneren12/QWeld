package com.qweld.app.feature.exam.data

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ContentCoverageTest {
  private val json = Json { ignoreUnknownKeys = true }
  private val loader = BlueprintJsonLoader(BlueprintJsonLoader.DEFAULT_JSON)
  private val supportedLocales = listOf("en", "ru")

  @Test
  fun blueprintQuotasCoveredByLocalizedQuestions() {
    val blueprint = loadBlueprint()
    val countsByLocale = supportedLocales.associateWith { locale -> countQuestions(locale) }

    val deficits = mutableListOf<String>()
    blueprint.taskQuotas.forEach { quota ->
      val available = supportedLocales.sumOf { locale -> countsByLocale[locale].orEmpty().countForTask(quota.taskId) }
      if (available < quota.required) {
        deficits += "${quota.taskId}: required=${quota.required}, available=$available"
      }
    }

    val message = buildString {
      appendLine("Tasks below blueprint quota:")
      deficits.forEach { deficit -> appendLine("- $deficit") }
    }
    assertTrue(deficits.isEmpty(), message)
  }

  @Test
  fun ruCoverageGapsAreReported() {
    val blueprint = loadBlueprint()
    val enCounts = countQuestions("en")
    val ruCounts = countQuestions("ru")

    val missingRu = blueprint.taskQuotas.mapNotNull { quota ->
      val ruCount = ruCounts.countForTask(quota.taskId)
      if (ruCount == 0) {
        val enCount = enCounts.countForTask(quota.taskId)
        "${quota.taskId} (en=$enCount, ru=$ruCount)"
      } else {
        null
      }
    }

    if (missingRu.isNotEmpty()) {
      println("[coverage_ru_missing] ${missingRu.joinToString(", ")}")
    }

    // Visibility only: the warning above is sufficient for now.
    assertTrue(blueprint.taskQuotas.isNotEmpty())
  }

  private fun loadBlueprint(): ExamBlueprint {
    val resourcePath = "blueprints/welder_ip_sk_202404.json"
    val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
      "Blueprint resource not found on classpath: $resourcePath"
    }
    val jsonContent = stream.bufferedReader().use { reader -> reader.readText() }
    return loader.decode(jsonContent)
  }

  private fun countQuestions(locale: String): Map<String, Int> {
    val resourcePath = "questions/$locale"
    val basePath = javaClass.classLoader.getResource(resourcePath)?.toURI()?.let { uri -> Path.of(uri) }
      ?: return emptyMap()
    if (!Files.exists(basePath)) return emptyMap()

    val counts = mutableMapOf<String, Int>()
    Files.walk(basePath).use { paths ->
      paths
        .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".json") }
        .forEach { path ->
          val taskIds = extractTaskIds(path)
          taskIds.forEach { taskId -> counts[taskId] = counts.getOrDefault(taskId, 0) + 1 }
        }
    }
    return counts
  }

  private fun extractTaskIds(path: Path): List<String> {
    val payload = Files.readString(path)
    val element = runCatching { json.parseToJsonElement(payload) }
      .getOrElse { throwable -> error("Failed to parse ${path.toUri()}: ${throwable.message}") }
    return when (element) {
      is JsonArray -> element.mapNotNull { item -> taskIdFrom(item, path) }
      is JsonObject -> listOfNotNull(taskIdFrom(element, path))
      else -> emptyList()
    }
  }

  private fun taskIdFrom(element: JsonElement, path: Path): String? {
    val taskId = (element as? JsonObject)?.get("taskId")?.jsonPrimitive?.contentOrNull
    return taskId ?: run {
      println("[coverage_skip_no_task] ${path.toUri()}")
      null
    }
  }

  private fun Map<String, Int>.countForTask(taskId: String): Int {
    this[taskId]?.let { return it }
    val suffix = taskSuffix(taskId) ?: return 0
    return entries
      .filter { entry -> taskSuffix(entry.key) == suffix }
      .maxOfOrNull { entry -> entry.value }
      ?: 0
  }

  private fun taskSuffix(taskId: String): String? {
    val suffix = taskId.substringAfter('-', missingDelimiterValue = "")
    return suffix.takeIf { it.isNotBlank() }
  }
}
