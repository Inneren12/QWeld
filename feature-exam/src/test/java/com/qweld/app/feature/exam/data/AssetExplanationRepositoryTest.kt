package com.qweld.app.feature.exam.data

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class AssetExplanationRepositoryTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `loadExplanation returns parsed data when asset exists`() {
    val capturedPaths = mutableListOf<String>()
    val repository = AssetExplanationRepository(
      assetReader = AssetExplanationRepository.AssetReader { path ->
        capturedPaths += path
        if (path == "explanations/en/A-1/A-1_sample__explain_en.json") {
          ByteArrayInputStream(sampleExplanationJson.toByteArray())
        } else {
          null
        }
      },
      json = json,
    )

    val explanation = repository.loadExplanation(locale = "en", taskId = "A-1", questionId = "Q-A-1_sample")

    assertEquals(listOf("explanations/en/A-1/A-1_sample__explain_en.json"), capturedPaths)
    assertNotNull(explanation)
    assertEquals("A-1_sample", explanation.id)
    assertEquals("Summary text", explanation.summary)
    assertEquals(2, explanation.steps.size)
    assertEquals("Step 1", explanation.steps[0].title)
    assertEquals("Do the thing", explanation.steps[0].text)
    assertEquals(1, explanation.whyNot.size)
    assertEquals("A", explanation.whyNot[0].choiceId)
    assertEquals("Because it's unsafe", explanation.whyNot[0].text)
    assertEquals(listOf("Tip one", "Tip two"), explanation.tips)
  }

  @Test
  fun `loadExplanation returns null when asset is missing`() {
    val repository = AssetExplanationRepository(
      assetReader = AssetExplanationRepository.AssetReader { null },
      json = json,
    )

    val explanation = repository.loadExplanation(locale = "ru", taskId = "B-2", questionId = "Q-B-2_unknown")

    assertNull(explanation)
  }

  private val sampleExplanationJson =
    """
    {
      "id": "A-1_sample",
      "summary": "Summary text",
      "steps": [
        {
          "title": "Step 1",
          "text": "Do the thing"
        },
        {
          "title": "Step 2",
          "text": "Then do another thing"
        }
      ],
      "why_not": [
        {
          "choiceId": "A",
          "text": "Because it's unsafe"
        }
      ],
      "tips": [
        "Tip one",
        "Tip two"
      ]
    }
    """
}
