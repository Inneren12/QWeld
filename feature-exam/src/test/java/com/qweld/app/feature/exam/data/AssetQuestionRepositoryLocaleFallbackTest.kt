package com.qweld.app.feature.exam.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AssetQuestionRepositoryLocaleFallbackTest {
  @Test
  fun ruLocaleUsedWhenPresent() {
    val ruPath = perTaskPath(locale = "ru", taskId = "A-1")
    val enPath = perTaskPath(locale = "en", taskId = "A-1")
    val (repository, recorder) =
      repository(
        payloads =
          mapOf(
            ruPath to questionArray("A-1" to 1, locale = "ru"),
            enPath to questionArray("A-1" to 1, locale = "en"),
          ),
      )

    val result = repository.loadQuestions(locale = "ru", tasks = setOf("A-1"))

    val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
    assertEquals("ru", success.locale)
    assertEquals("ru", success.questions.first().locale)
    assertEquals(1, recorder.openCount(ruPath))
    assertEquals(0, recorder.openCount(enPath))
  }

  @Test
  fun ruMissingFallsBackToEn() {
    val enPath = perTaskPath(locale = "en", taskId = "A-1")
    val (repository, recorder) =
      repository(payloads = mapOf(enPath to questionArray("A-1" to 1, locale = "en")))

    val result = repository.loadQuestions(locale = "ru", tasks = setOf("A-1"))

    val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
    assertEquals("en", success.locale)
    assertEquals(1, success.questions.size)
    assertEquals(1, recorder.openCount(enPath))
  }

  @Test
  fun ruMissingBankFallsBackToEnBank() {
    val enBank = bankPath(locale = "en")
    val (repository, recorder) =
      repository(
        payloads = mapOf(enBank to questionArray("A-1" to 2, locale = "en")),
      )

    val result = repository.loadQuestions(locale = "ru")

    val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
    assertEquals("en", success.locale)
    assertEquals(2, success.questions.size)
    assertEquals(listOf("en"), success.questions.map { it.locale }.distinct())
    assertEquals(1, recorder.openCount(enBank))
  }

  @Test
  fun ruCorruptBankDoesNotSilentlyFallback() {
    val ruBank = bankPath(locale = "ru")
    val enBank = bankPath(locale = "en")
    val (repository, recorder) =
      repository(
        payloads =
          mapOf(
            ruBank to "{", // malformed JSON to trigger corruption
            enBank to questionArray("A-1" to 1, locale = "en"),
          ),
      )

    val result = repository.loadQuestions(locale = "ru")

    assertIs<AssetQuestionRepository.LoadResult.Corrupt>(result)
    assertEquals(0, recorder.openCount(enBank))
  }

  private fun repository(
    payloads: Map<String, String>,
    listings: Map<String, List<String>> = emptyMap(),
    localeResolver: () -> String = { "en" },
  ): Pair<AssetQuestionRepository, CallRecorder> {
    val bytes = payloads.mapValues { it.value.toByteArray() }
    val assets = TestIntegrity.addIndexes(bytes)
    val recorder = CallRecorder(assets, listings)
    val assetReader =
      AssetQuestionRepository.AssetReader(
        opener = recorder::open,
        lister = recorder::list,
      )
    val repository =
      AssetQuestionRepository(
        assetReader = assetReader,
        localeResolver = localeResolver,
        json = Json { ignoreUnknownKeys = true },
      )
    return repository to recorder
  }

  private class CallRecorder(
    private val payloads: Map<String, ByteArray>,
    private val listings: Map<String, List<String>>,
  ) {
    private val openCounts = mutableMapOf<String, Int>()
    private val listCounts = mutableMapOf<String, Int>()

    fun open(path: String) =
      payloads[path]?.inputStream()?.also {
        openCounts[path] = openCounts.getOrDefault(path, 0) + 1
      }

    fun list(path: String): List<String>? {
      listCounts[path] = listCounts.getOrDefault(path, 0) + 1
      return listings[path]
    }

    fun openCount(path: String): Int = openCounts.getOrDefault(path, 0)
  }

  private fun questionArray(vararg tasks: Pair<String, Int>, locale: String = "en"): String {
    val questions = mutableListOf<String>()
    tasks.forEach { (taskId, count) ->
      repeat(count) { index ->
        val id = "Q-${taskId}-${index + 1}"
        questions += questionJson(id, taskId, locale)
      }
    }
    return questions.joinToString(prefix = "[", postfix = "]")
  }

  private fun questionJson(id: String, taskId: String, locale: String): String {
    val blockId = taskId.substringBefore("-", taskId)
    val choiceId = "${id}-A"
      return """
              {
      "id": "$id",
      "taskId": "$taskId",
      "blockId": "$blockId",
      "locale": "$locale",
      "stem": { "$locale": "Stem $id" },
      "choices": [
        { "id": "$choiceId", "text": { "$locale": "Option" } }
      ],
      "correctId": "$choiceId"
    }
  """.trimIndent()
  }

  private fun perTaskPath(locale: String, taskId: String) = "questions/$locale/tasks/$taskId.json"

  private fun bankPath(locale: String) = "questions/$locale/bank.v1.json"
}
