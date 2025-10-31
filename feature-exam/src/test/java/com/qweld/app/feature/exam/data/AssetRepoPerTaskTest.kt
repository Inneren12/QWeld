package com.qweld.app.feature.exam.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.test.assertIs
import org.junit.Test

class AssetRepoPerTaskTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun loadsOnlyRequestedPerTaskFiles() {
    val payloads = mapOf(
      "questions/en/tasks/D-13.json" to perTaskPayload("D-13", "13"),
      "questions/en/tasks/D-14.json" to perTaskPayload("D-14", "14"),
    )
    val opened = mutableListOf<String>()
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(
            open = { path ->
              opened += path
              payloads[path]?.byteInputStream()
            },
          ),
        localeResolver = { "en" },
        json = json,
      )

    val result = repository.loadQuestions(locale = "en", tasks = setOf("D-13", "D-14"))
    val success = assertIs<AssetQuestionRepository.Result.Success>(result)
    assertEquals(2, success.questions.size)
    assertEquals(setOf("questions/en/tasks/D-13.json", "questions/en/tasks/D-14.json"), opened.toSet())
  }

  @Test
  fun fallsBackToBankWhenPerTaskMissing() {
    val bankPayload = """
      [
        {
          "id": "Q-1",
          "taskId": "D-13",
          "stem": "Stem",
          "choices": [
            { "id": "C-1", "text": "Choice" }
          ],
          "correctId": "C-1"
        }
      ]
    """.trimIndent()

    val payloads = mapOf("questions/en/bank.v1.json" to bankPayload)
    val opened = mutableListOf<String>()
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(
            open = { path ->
              opened += path
              payloads[path]?.byteInputStream()
            },
          ),
        localeResolver = { "en" },
        json = json,
      )

    val result = repository.loadQuestions(locale = "en", tasks = setOf("D-13"))
    val success = assertIs<AssetQuestionRepository.Result.Success>(result)
    assertEquals(1, success.questions.size)
    assertTrue(opened.contains("questions/en/tasks/D-13.json"))
    assertTrue(opened.contains("questions/en/bank.v1.json"))
  }

  private fun perTaskPayload(taskId: String, suffix: String): String {
    return """
      [
        {
          "id": "Q-$suffix",
          "taskId": "$taskId",
          "stem": "Stem",
          "choices": [
            { "id": "C-1", "text": "Choice" }
          ],
          "correctId": "C-1"
        }
      ]
    """.trimIndent()
  }
}
