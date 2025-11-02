package com.qweld.app.feature.exam.data

import kotlinx.serialization.json.Json
import com.qweld.app.feature.exam.data.TestIntegrity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.test.assertIs
import org.junit.Test

class AssetRepoLocaleCacheClearTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun clearLocaleCacheRemovesOnlySelectedLocale() {
    val payloads = mapOf(
      "questions/en/tasks/D-13.json" to perTaskPayload(locale = "en"),
      "questions/ru/tasks/D-13.json" to perTaskPayload(locale = "ru"),
    )
    val assets = TestIntegrity.addIndexes(payloads.mapValues { it.value.toByteArray() })
    val opened = mutableListOf<String>()
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(
            open = { path ->
              if (!path.endsWith("/index.json")) opened += path
              assets[path]?.inputStream()
            },
          ),
        localeResolver = { "en" },
        json = json,
      )

    val enResult = repository.loadQuestions(locale = "en", tasks = setOf("D-13"))
    assertIs<AssetQuestionRepository.LoadResult.Success>(enResult)
    val ruResult = repository.loadQuestions(locale = "ru", tasks = setOf("D-13"))
    assertIs<AssetQuestionRepository.LoadResult.Success>(ruResult)

    opened.clear()
    repository.loadQuestions(locale = "en", tasks = setOf("D-13"))
    assertTrue(opened.isEmpty())

    repository.clearLocaleCache("en")

    opened.clear()
    repository.loadQuestions(locale = "en", tasks = setOf("D-13"))
    assertEquals(listOf("questions/en/tasks/D-13.json"), opened)

    opened.clear()
    repository.loadQuestions(locale = "ru", tasks = setOf("D-13"))
    assertTrue(opened.isEmpty())
  }

  private fun perTaskPayload(locale: String): String {
    return """
      [
        {
          "id": "Q-1",
          "taskId": "D-13",
          "locale": "$locale",
          "stem": "Stem $locale",
          "choices": [
            { "id": "C-1", "text": "Choice" }
          ],
          "correctId": "C-1"
        }
      ]
    """.trimIndent()
  }
}
