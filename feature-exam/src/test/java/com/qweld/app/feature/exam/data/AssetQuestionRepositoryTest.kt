package com.qweld.app.feature.exam.data

import kotlinx.serialization.json.Json
import com.qweld.app.data.content.questions.IndexParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.test.assertIs
import org.junit.Test
import java.security.MessageDigest

/**
 * Tests for AssetQuestionRepository with focus on error handling and content integrity.
 *
 * ## Test Coverage:
 * - ✓ Happy path: Valid bank loading
 * - ✓ Happy path: Per-task loading
 * - ✓ Error: Missing manifest
 * - ✓ Error: Missing bank file
 * - ✓ Error: Integrity mismatch (corrupted file)
 * - ✓ Error: Invalid JSON (malformed syntax)
 * - ✓ Error: Invalid JSON schema (missing required fields)
 * - ✓ Error: Missing task file (per-task loading)
 * - ✓ Error: Corrupted task file (integrity failure)
 * - ✓ Fallback: Bank loading when per-task fails
 */
class AssetQuestionRepositoryTest {

  // ============================================================================
  // Happy Path Tests
  // ============================================================================

  @Test
  fun `loadQuestions succeeds with valid bank file`() {
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

    val assets =
      TestIntegrity.addIndexes(
        mapOf("questions/en/bank.v1.json" to payload.toByteArray()),
      )
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
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

  @Test
  fun `loadQuestions succeeds with per-task loading`() {
    val task1Payload = """
      [
        {
          "id": "Q-A1-1",
          "taskId": "A-1",
          "stem": "Question 1",
          "choices": [
            { "id": "C-1", "text": "Choice A" },
            { "id": "C-2", "text": "Choice B" }
          ],
          "correctId": "C-1"
        }
      ]
    """.trimIndent()

    val task2Payload = """
      [
        {
          "id": "Q-A2-1",
          "taskId": "A-2",
          "stem": "Question 2",
          "choices": [
            { "id": "C-1", "text": "Choice A" }
          ],
          "correctId": "C-1"
        }
      ]
    """.trimIndent()

    val assets =
      TestIntegrity.addIndexes(
        mapOf(
          "questions/en/tasks/A-1.json" to task1Payload.toByteArray(),
          "questions/en/tasks/A-2.json" to task2Payload.toByteArray(),
        ),
      )
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val result = repository.loadQuestions("en", tasks = setOf("A-1", "A-2"))
    val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
    assertEquals(2, success.questions.size)
    assertEquals("Q-A1-1", success.questions[0].id)
    assertEquals("Q-A2-1", success.questions[1].id)
  }

  // ============================================================================
  // Error Path Tests: Manifest Issues
  // ============================================================================

  @Test
  fun `loadQuestions returns Missing when manifest is absent`() {
    // No index.json, but bank file exists
    val bankPayload = """[{"id":"Q-1","taskId":"A-1","stem":"Q","choices":[{"id":"C-1","text":"A"}],"correctId":"C-1"}]"""
    val assets = mapOf("questions/en/bank.v1.json" to bankPayload.toByteArray())
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val result = repository.loadQuestions("en")
    // When manifest is missing, the repository cannot verify integrity and will fail
    // This should be Missing or Corrupt depending on how far it gets
    assertTrue(
      "Expected Missing or Corrupt, got $result",
      result is AssetQuestionRepository.LoadResult.Missing ||
        result is AssetQuestionRepository.LoadResult.Corrupt
    )
  }

  // ============================================================================
  // Error Path Tests: Missing Content
  // ============================================================================

  @Test
  fun `loadQuestions returns Missing when bank file is absent`() {
    // Empty asset map with just an index (no actual content)
    val emptyIndex = """
      {
        "blueprintId": "test",
        "bankVersion": "v1",
        "files": {}
      }
    """.trimIndent()
    val assets = mapOf("questions/en/index.json" to emptyIndex.toByteArray())
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val result = repository.loadQuestions("en")
    assertIs<AssetQuestionRepository.LoadResult.Missing>(result)
  }

  @Test
  fun `loadQuestions returns Missing when task file is absent`() {
    // Provide manifest but no task files
    val emptyIndex = """
      {
        "blueprintId": "test",
        "bankVersion": "v1",
        "files": {}
      }
    """.trimIndent()
    val assets = mapOf("questions/en/index.json" to emptyIndex.toByteArray())
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val result = repository.loadQuestions("en", tasks = setOf("A-1"))
    assertIs<AssetQuestionRepository.LoadResult.Missing>(result)
  }

  // ============================================================================
  // Error Path Tests: Integrity Failures
  // ============================================================================

  @Test
  fun `loadQuestions returns Corrupt with IntegrityMismatch when hash does not match`() {
    val correctPayload = """[{"id":"Q-1","taskId":"A-1","stem":"Q","choices":[{"id":"C-1","text":"A"}],"correctId":"C-1"}]"""
    val corruptedPayload = """[{"id":"Q-1","taskId":"A-1","stem":"MODIFIED","choices":[{"id":"C-1","text":"A"}],"correctId":"C-1"}]"""

    // Create index with correct hash
    val correctBytes = correctPayload.toByteArray()
    val correctHash = sha256(correctBytes)
    val index = """
      {
        "blueprintId": "test",
        "bankVersion": "v1",
        "files": {
          "questions/en/bank.v1.json": { "sha256": "$correctHash" }
        }
      }
    """.trimIndent()

    // But provide corrupted content
    val assets = mapOf(
      "questions/en/index.json" to index.toByteArray(),
      "questions/en/bank.v1.json" to corruptedPayload.toByteArray()
    )

    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val result = repository.loadQuestions("en")
    val corrupt = assertIs<AssetQuestionRepository.LoadResult.Corrupt>(result)
    assertIs<ContentLoadError.IntegrityMismatch>(corrupt.error)
  }

  // ============================================================================
  // Error Path Tests: Invalid JSON
  // ============================================================================

  @Test
  fun `loadQuestions returns Corrupt with InvalidJson when JSON is malformed`() {
    val malformedPayload = """[{"id":"Q-1","taskId":"A-1" MISSING_COMMA "stem":"Q"}]"""

    val assets =
      TestIntegrity.addIndexes(
        mapOf("questions/en/bank.v1.json" to malformedPayload.toByteArray()),
      )
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val result = repository.loadQuestions("en")
    val corrupt = assertIs<AssetQuestionRepository.LoadResult.Corrupt>(result)
    // Should be InvalidJson or Unknown wrapping serialization error
    assertTrue(
      "Expected InvalidJson or Unknown, got ${corrupt.error::class.simpleName}",
      corrupt.error is ContentLoadError.InvalidJson || corrupt.error is ContentLoadError.Unknown
    )
  }

  @Test
  fun `loadQuestions returns Corrupt when required fields are missing`() {
    // Missing 'correctId' field - schema violation
    val invalidSchemaPayload = """
      [
        {
          "id": "Q-1",
          "taskId": "A-1",
          "stem": "Question",
          "choices": [
            { "id": "C-1", "text": "Choice" }
          ]
        }
      ]
    """.trimIndent()

    val assets =
      TestIntegrity.addIndexes(
        mapOf("questions/en/bank.v1.json" to invalidSchemaPayload.toByteArray()),
      )
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val result = repository.loadQuestions("en")
    val corrupt = assertIs<AssetQuestionRepository.LoadResult.Corrupt>(result)
    // Should be InvalidJson or Unknown due to serialization failure
    assertTrue(
      "Expected error wrapping serialization failure",
      corrupt.error is ContentLoadError.InvalidJson || corrupt.error is ContentLoadError.Unknown
    )
  }

  // ============================================================================
  // Error Path Tests: Per-Task Loading Failures
  // ============================================================================

  @Test
  fun `loadQuestions falls back to bank when one task file is missing`() {
    val task1Payload = """[{"id":"Q-A1","taskId":"A-1","stem":"Q1","choices":[{"id":"C-1","text":"A"}],"correctId":"C-1"}]"""
    val bankPayload = """[{"id":"Q-B","taskId":"B-1","stem":"QB","choices":[{"id":"C-1","text":"A"}],"correctId":"C-1"}]"""

    val assets =
      TestIntegrity.addIndexes(
        mapOf(
          "questions/en/tasks/A-1.json" to task1Payload.toByteArray(),
          // A-2 is missing
          "questions/en/bank.v1.json" to bankPayload.toByteArray(),
        ),
      )
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val result = repository.loadQuestions("en", tasks = setOf("A-1", "A-2"))
    // Should fall back to bank loading
    val success = assertIs<AssetQuestionRepository.LoadResult.Success>(result)
    // Bank has different questions than the task files
    assertEquals(1, success.questions.size)
    assertEquals("Q-B", success.questions[0].id)
  }

  @Test
  fun `loadTaskIntoCache throws TaskAssetMissingException when task file is absent`() {
    val emptyIndex = """{"blueprintId":"test","bankVersion":"v1","files":{}}"""
    val assets = mapOf("questions/en/index.json" to emptyIndex.toByteArray())
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val exception = try {
      kotlinx.coroutines.runBlocking {
        repository.loadTaskIntoCache("en", "A-1")
      }
      null
    } catch (e: AssetQuestionRepository.TaskAssetMissingException) {
      e
    }

    assertTrue("Expected TaskAssetMissingException", exception != null)
    assertEquals("A-1", exception?.taskId)
  }

  @Test
  fun `loadTaskIntoCache throws TaskAssetReadException when task file is corrupted`() {
    val malformedPayload = """[{INVALID JSON]"""
    val assets =
      TestIntegrity.addIndexes(
        mapOf("questions/en/tasks/A-1.json" to malformedPayload.toByteArray()),
      )
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )

    val exception = try {
      kotlinx.coroutines.runBlocking {
        repository.loadTaskIntoCache("en", "A-1")
      }
      null
    } catch (e: AssetQuestionRepository.TaskAssetReadException) {
      e
    } catch (e: AssetQuestionRepository.TaskLoadException) {
      e as? AssetQuestionRepository.TaskAssetReadException
    }

    assertTrue("Expected TaskAssetReadException", exception is AssetQuestionRepository.TaskAssetReadException)
    if (exception is AssetQuestionRepository.TaskAssetReadException) {
      assertEquals("A-1", exception.taskId)
    }
  }

  // ============================================================================
  // Utility Methods
  // ============================================================================

  private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
  }
}
