package com.qweld.app.feature.exam.data

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class LocaleCoverageTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun ruCoverageLoggedAndOptionallyGated() {
    val enQuestions = loadQuestions("en")
    val ruQuestions = loadQuestions("ru")

    val coverage = computeCoverage(enQuestions, ruQuestions)

    logCoverage(coverage, enQuestions.size, ruQuestions.size)

    threshold()?.let { minCoverage ->
      val message = buildString {
        appendLine(
          "RU coverage ${formatPercent(coverage.overall)} is below required ${formatPercent(minCoverage)} " +
            "(en=${enQuestions.size}, ru=${ruQuestions.size}, missing=${coverage.missingByTask.values.sumOf { it.size }})"
        )
        appendLine("Tasks missing translations:")
        coverage.missingByTask
          .toSortedMap()
          .forEach { (taskId, missing) -> appendLine("- $taskId: ${missing.joinToString()}") }
      }

      assertTrue(coverage.overall >= minCoverage, message)
    }
  }

  private fun computeCoverage(
    enQuestions: List<QuestionRef>,
    ruQuestions: List<QuestionRef>,
  ): LocaleCoverage {
    val enByTask = enQuestions.groupBy { it.taskId }
    val ruByTask = ruQuestions.groupBy { it.taskId }.mapValues { entry -> entry.value.associateBy { it.id } }

    var translatedCount = 0
    val perTask = mutableMapOf<String, Double>()
    val missingByTask = mutableMapOf<String, List<String>>()

    enByTask.toSortedMap().forEach { (taskId, questions) ->
      val ruIds = ruByTask[taskId].orEmpty()
      val missing = questions.mapNotNull { question -> question.id.takeUnless { id -> ruIds.containsKey(id) } }

      translatedCount += questions.size - missing.size
      perTask[taskId] = if (questions.isEmpty()) 1.0 else (questions.size - missing.size).toDouble() / questions.size
      if (missing.isNotEmpty()) {
        missingByTask[taskId] = missing.sorted()
      }
    }

    val overall = if (enQuestions.isEmpty()) 1.0 else translatedCount.toDouble() / enQuestions.size
    return LocaleCoverage(overall = overall, perTask = perTask, missingByTask = missingByTask)
  }

  private fun loadQuestions(locale: String): List<QuestionRef> {
    val resourcePath = "questions/$locale"
    val basePath = javaClass.classLoader.getResource(resourcePath)?.toURI()?.let { uri -> Path.of(uri) }
      ?: return emptyList()
    if (!Files.exists(basePath)) return emptyList()

    val questions = mutableListOf<QuestionRef>()
    Files.walk(basePath).use { paths ->
      paths
        .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".json") }
        .forEach { path -> questions += parseQuestions(path) }
    }
    return questions
  }

  private fun parseQuestions(path: Path): List<QuestionRef> {
    val payload = path.readText()
    val element = runCatching { json.parseToJsonElement(payload) }
      .getOrElse { throwable -> error("Failed to parse ${path.toUri()}: ${throwable.message}") }
    return when (element) {
      is JsonArray -> element.mapNotNull { item -> questionRef(item, path) }
      is JsonObject -> listOfNotNull(questionRef(element, path))
      else -> emptyList()
    }
  }

  private fun questionRef(element: JsonElement, path: Path): QuestionRef? {
    val jsonObject = element as? JsonObject ?: return null
    val id = jsonObject["id"]?.jsonPrimitive?.contentOrNull
    val taskId = jsonObject["taskId"]?.jsonPrimitive?.contentOrNull
    if (id.isNullOrBlank() || taskId.isNullOrBlank()) {
      println("[locale_coverage_skip] Missing id/taskId in ${path.toUri()}")
      return null
    }
    return QuestionRef(id = id, taskId = taskId)
  }

  private fun logCoverage(coverage: LocaleCoverage, enCount: Int, ruCount: Int) {
    val missingTotal = coverage.missingByTask.values.sumOf { it.size }
    println("RU coverage overall: ${formatPercent(coverage.overall)} (en=$enCount, ru=$ruCount, missing=$missingTotal)")

    coverage.perTask
      .filter { (taskId, _) -> coverage.missingByTask.containsKey(taskId) }
      .toSortedMap()
      .forEach { (taskId, value) ->
        val missing = coverage.missingByTask[taskId].orEmpty()
        println(" - $taskId: ${formatPercent(value)} missing=${missing.size} ids=${missing.joinToString()}")
      }
  }

  private fun threshold(): Double? {
    val candidates = listOfNotNull(
      System.getProperty("localeCoverage.ru.min"),
      System.getProperty("org.gradle.project.localeCoverage.ru.min"),
      System.getProperty("qweld.localeCoverage.ru.min"),
      System.getenv("LOCALE_COVERAGE_RU_MIN"),
    )

    return candidates.firstNotNullOfOrNull { candidate -> candidate.toDoubleOrNull() }
  }

  private fun formatPercent(value: Double): String {
    return "%.1f%%".format(value * 100.0)
  }

  private data class QuestionRef(val id: String, val taskId: String)

  private data class LocaleCoverage(
    val overall: Double,
    val perTask: Map<String, Double>,
    val missingByTask: Map<String, List<String>>,
  )
}
