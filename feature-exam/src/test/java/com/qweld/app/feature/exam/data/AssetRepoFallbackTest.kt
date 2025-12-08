package com.qweld.app.feature.exam.data

import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Ignore("Pending asset fallback alignment")
class AssetRepoFallbackTest {
  @Test
  fun perTaskTakesPriorityOverBankAndRaw() {
    val (repository, recorder) =
      repository(
        payloads =
          mapOf(
            perTaskPath("en", "A-1") to questionArray("A-1" to 2),
            bankPath("en") to questionArray("A-1" to 2),
            rawPath("en", "A-1", "one.json") to questionObject("A-1", 1),
          ),
        listings =
          mapOf(
            baseLocalePath("en") to listOf("A-1"),
            rawTaskPath("en", "A-1") to listOf("one.json"),
          ),
      )

    val result = repository.loadQuestions(locale = "en", tasks = setOf("A-1"))
    val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
    assertEquals(2, success.questions.size)
    assertEquals(1, recorder.openCount(perTaskPath("en", "A-1")))
    assertFalse(recorder.openCounts.containsKey(bankPath("en")))
    assertFalse(recorder.openCounts.containsKey(rawPath("en", "A-1", "one.json")))
  }

  @Test
  fun bankFallbackUsedWhenPerTaskMissing() {
    val (repository, recorder) =
      repository(
        payloads = mapOf(bankPath("en") to questionArray("A-1" to 1)),
      )

    val result = repository.loadQuestions(locale = "en", tasks = setOf("A-1"))
    val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
    assertEquals(1, success.questions.size)
    assertEquals(1, recorder.openCount(bankPath("en")))
  }

  @Test
  fun rawFallbackUsedWhenBankMissing() {
    val (repository, recorder) =
      repository(
        payloads = mapOf(rawPath("en", "A-1", "one.json") to questionObject("A-1", 1)),
        listings =
          mapOf(
            baseLocalePath("en") to listOf("A-1"),
            rawTaskPath("en", "A-1") to listOf("one.json"),
          ),
      )

    val result = repository.loadQuestions(locale = "en")
    val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
    assertEquals(1, success.questions.size)
    assertEquals(1, recorder.openCount(rawPath("en", "A-1", "one.json")))
    assertEquals(1, recorder.listCount(baseLocalePath("en")))
  }

  @Test
  fun missingBankAndRawReturnsMissing() {
    val (repository, _) = repository(payloads = emptyMap())

    val result = repository.loadQuestions(locale = "en")

    assertTrue(result === AssetQuestionRepository.LoadResult.Missing)
  }

  @Test
  fun lruEvictsLeastRecentlyUsedTask() {
    val (repository, recorder) =
      repository(
        payloads =
          mapOf(
            perTaskPath("en", "T1") to questionArray("T1" to 1),
            perTaskPath("en", "T2") to questionArray("T2" to 1),
            perTaskPath("en", "T3") to questionArray("T3" to 1),
          ),
        cacheCapacity = 2,
      )

    fun load(task: String) {
      val result = repository.loadQuestions(locale = "en", tasks = setOf(task))
      val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
      assertEquals(1, success.questions.size)
    }

    load("T1")
    assertEquals(1, recorder.openCount(perTaskPath("en", "T1")))

    load("T1")
    assertEquals(1, recorder.openCount(perTaskPath("en", "T1")))

    load("T2")
    load("T3")

    load("T1")
    assertEquals(2, recorder.openCount(perTaskPath("en", "T1")))
  }

  @Test
  fun supportsRuLocaleAndDefaultsToResolver() {
    val (repository, recorder) =
      repository(
        payloads =
          mapOf(
            perTaskPath("ru", "A-1") to questionArray("A-1" to 1, locale = "ru"),
            perTaskPath("en", "A-1") to questionArray("A-1" to 1, locale = "en"),
          ),
        localeResolver = { "en" },
      )

    val ruResult = repository.loadQuestions(locale = "RU", tasks = setOf("A-1"))
    val ruSuccess = assertIs<AssetQuestionRepository.LoadResult.Success>(ruResult)
    assertEquals("ru", ruSuccess.locale)
    assertEquals("ru", ruSuccess.questions.first().locale)

    recorder.clear()

    val defaultResult = repository.loadQuestions(tasks = setOf("A-1"))
    val defaultSuccess = assertIs<AssetQuestionRepository.LoadResult.Success>(defaultResult)
    assertEquals("en", defaultSuccess.locale)
    assertEquals(1, recorder.openCount(perTaskPath("en", "A-1")))
  }

  @Test
  fun fallsBackToDefaultLocaleWhenRequestedLocaleIsMissing() {
    val (repository, recorder) =
      repository(
        payloads = mapOf(perTaskPath("en", "A-1") to questionArray("A-1" to 1, locale = "en")),
        localeResolver = { "en" },
      )

    val result = repository.loadQuestions(locale = "ru", tasks = setOf("A-1"))
    val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
    assertEquals("en", success.locale)
    assertEquals(1, success.questions.size)
    assertEquals(1, recorder.openCount(perTaskPath("en", "A-1")))
    assertEquals(0, recorder.openCount(perTaskPath("ru", "A-1")))
  }

  private fun repository(
    payloads: Map<String, String>,
    listings: Map<String, List<String>> = emptyMap(),
    localeResolver: () -> String = { "en" },
    cacheCapacity: Int = 8,
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
        cacheCapacity = cacheCapacity,
      )
    return repository to recorder
  }

  private class CallRecorder(
    private val payloads: Map<String, ByteArray>,
    private val listings: Map<String, List<String>>,
  ) {
    val openCounts = mutableMapOf<String, Int>()
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

    fun listCount(path: String): Int = listCounts.getOrDefault(path, 0)

    fun clear() {
      openCounts.clear()
      listCounts.clear()
    }
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

  private fun questionObject(taskId: String, index: Int, locale: String = "en"): String {
    val id = "Q-${taskId}-${index}"
    return questionJson(id, taskId, locale)
  }

  private fun questionJson(id: String, taskId: String, locale: String): String {
    val blockId = taskId.substringBefore("-", taskId)
    val choiceId = "${id}-A"
    return """
      {
        \"id\": \"$id\",
        \"taskId\": \"$taskId\",
        \"blockId\": \"$blockId\",
        \"locale\": \"$locale\",
        \"stem\": { \"$locale\": \"Stem $id\" },
        \"choices\": [
          { \"id\": \"$choiceId\", \"text\": { \"$locale\": \"Option\" } }
        ],
        \"correctId\": \"$choiceId\"
      }
    """.trimIndent()
  }

  private fun perTaskPath(locale: String, taskId: String) = "questions/$locale/tasks/$taskId.json"

  private fun bankPath(locale: String) = "questions/$locale/bank.v1.json"

  private fun rawPath(locale: String, taskId: String, file: String) = "questions/$locale/$taskId/$file"

  private fun baseLocalePath(locale: String) = "questions/$locale"

  private fun rawTaskPath(locale: String, taskId: String) = "questions/$locale/$taskId"
}
