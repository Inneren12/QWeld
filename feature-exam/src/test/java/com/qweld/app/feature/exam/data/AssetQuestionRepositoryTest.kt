package com.qweld.app.feature.exam.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class AssetQuestionRepositoryTest {
  @Test
  fun decodeQuestionsFromJsonPayload() {
    val payload = """
      [
        {
          "id": "Q-1",
          "taskId": "A-1",
          "stem": "Stem",
          "choices": [
            { "id": "C-1", "text": "Choice" }
          ],
          "correctId": "C-1"
        }
      ]
    """.trimIndent()

    val repository =
      AssetQuestionRepository(
        assetReader = AssetQuestionRepository.AssetReader(open = { payload.byteInputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val result = repository.loadQuestions("en")
    val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
    val questions = success.questions
    assertEquals(1, questions.size)
    assertEquals("Q-1", questions.first().id)
    assertEquals("C-1", questions.first().choices.first().id)
  }
}
