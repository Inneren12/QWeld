package com.qweld.app.feature.exam.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class AssetRepoLocaleSwitchTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun switchesLocaleWithoutCacheLeak() {
    val payloads = mapOf(
      "questions/en/tasks/D-13.json" to perTaskPayload(locale = "en", stem = "Stem EN"),
      "questions/ru/tasks/D-13.json" to perTaskPayload(locale = "ru", stem = "Stem RU"),
    )

    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(
            open = { path -> payloads[path]?.byteInputStream() },
          ),
        localeResolver = { "en" },
        json = json,
      )

    val enResult = repository.loadQuestions(locale = "en", tasks = setOf("D-13"))
    val enSuccess = assertIs<AssetQuestionRepository.Result.Success>(enResult)
    assertEquals("en", enSuccess.questions.first().locale)
    assertEquals("\"Stem EN\"", enSuccess.questions.first().stem.toString())

    val ruResult = repository.loadQuestions(locale = "ru", tasks = setOf("D-13"))
    val ruSuccess = assertIs<AssetQuestionRepository.Result.Success>(ruResult)
    assertEquals("ru", ruSuccess.questions.first().locale)
    assertEquals("\"Stem RU\"", ruSuccess.questions.first().stem.toString())
  }

  private fun perTaskPayload(locale: String, stem: String): String {
    return """
      [
        {
          "id": "Q-1",
          "taskId": "D-13",
          "locale": "$locale",
          "stem": "$stem",
          "choices": [
            { "id": "C-1", "text": "Choice" }
          ],
          "correctId": "C-1"
        }
      ]
    """.trimIndent()
  }
}
