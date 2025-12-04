package com.qweld.app.feature.exam.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AssetRepoIoPoolTest {
  @Test
  fun perTaskAndBankReadsUseIoPool() {
    val testThreadName = Thread.currentThread().name
    val threadByPath = mutableMapOf<String, String>()

    val perTaskPayload = """
      [
        {
          "id": "Q-PT-1",
          "taskId": "D-13",
          "stem": "Stem",
          "choices": [
            { "id": "C-1", "text": "Choice" }
          ],
          "correctId": "C-1"
        }
      ]
    """.trimIndent()

    val bankPayload = """
      [
        {
          "id": "Q-B-1",
          "taskId": "BANK",
          "stem": "Stem",
          "choices": [
            { "id": "C-1", "text": "Choice" }
          ],
          "correctId": "C-1"
        }
      ]
    """.trimIndent()

    val assets =
      TestIntegrity.addIndexes(
        mapOf(
          "questions/en/tasks/D-13.json" to perTaskPayload.toByteArray(),
          "questions/en/bank.v1.json" to bankPayload.toByteArray(),
        ),
      )
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(
            opener = { path ->
              threadByPath[path] = Thread.currentThread().name
              assets[path]?.inputStream()
            },
            lister = { _ -> emptyList() },
          ),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    repository.loadQuestions(locale = "en", tasks = setOf("D-13"))
    repository.loadQuestions(locale = "en")

    val perTaskThread = threadByPath["questions/en/tasks/D-13.json"]
    val bankThread = threadByPath["questions/en/bank.v1.json"]

    assertNotNull(perTaskThread)
    assertNotNull(bankThread)
    assertNotEquals(testThreadName, perTaskThread)
    assertNotEquals(testThreadName, bankThread)
  }
}
