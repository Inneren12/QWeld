package com.qweld.app.data.content

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class ContentIndexReaderTest {

  private class FakeAssetLoader(private val assets: Map<String, String>) : ContentIndexReader.AssetLoader {
    override fun open(path: String) =
      assets[path]?.let { ByteArrayInputStream(it.toByteArray()) } ?: throw FileNotFoundException(path)
  }

  private val json = Json { ignoreUnknownKeys = false }

  @Test
  fun `read parses index payload`() {
    val payload =
      """
      {
        "schema": "qweld.dist.index.v1",
        "generatedAt": "2024-10-10T12:00:00Z",
        "locales": {
          "en": {
            "total": 3,
            "tasks": {"A-1": 2, "B-4": 1},
            "sha256": {
              "bank": "abc",
              "tasks": {"A-1": "def", "B-4": "ghi"}
            }
          }
        }
      }
      """
        .trimIndent()
    val reader = ContentIndexReader(FakeAssetLoader(mapOf("questions/index.json" to payload)), json)

    val result = reader.read()

    assertNotNull(result)
    assertEquals(payload, result.rawJson.trim())
    assertEquals("qweld.dist.index.v1", result.index.schema)
    assertEquals("2024-10-10T12:00:00Z", result.index.generatedAt)
    val enLocale = result.index.locales.getValue("en")
    assertEquals(3, enLocale.total)
    assertEquals(2, enLocale.tasks.getValue("A-1"))
    assertEquals("abc", enLocale.sha256.bank)
  }

  @Test
  fun `read returns null when asset missing`() {
    val reader = ContentIndexReader(FakeAssetLoader(emptyMap()), json)

    val result = reader.read()

    assertNull(result)
  }

  @Test
  fun `verify returns empty list when hashes match`() {
    val bankContent = "bank"
    val taskContent = "task"
    val indexPayload =
      """
      {
        "schema": "qweld.dist.index.v1",
        "generatedAt": "2024-10-10T12:00:00Z",
        "locales": {
          "en": {
            "total": 1,
            "tasks": {"A-1": 1},
            "sha256": {
              "bank": "${sha256(bankContent)}",
              "tasks": {"A-1": "${sha256(taskContent)}"}
            }
          }
        }
      }
      """
        .trimIndent()

    val assets =
      mapOf(
        "questions/index.json" to indexPayload,
        "questions/en/bank.v1.json" to bankContent,
        "questions/en/tasks/A-1.json" to taskContent,
      )
    val reader = ContentIndexReader(FakeAssetLoader(assets), json)

    val result = reader.read()
    val mismatches = reader.verify(result)

    assertNotNull(result)
    assertEquals(emptyList(), mismatches)
  }

  @Test
  fun `verify reports missing assets and hash mismatches`() {
    val bankContent = "bank"
    val taskContent = "task"
    val indexPayload =
      """
      {
        "schema": "qweld.dist.index.v1",
        "generatedAt": "2024-10-10T12:00:00Z",
        "locales": {
          "en": {
            "total": 1,
            "tasks": {"A-1": 1},
            "sha256": {
              "bank": "${sha256(bankContent)}",
              "tasks": {"A-1": "${sha256(taskContent)}"}
            }
          }
        }
      }
      """
        .trimIndent()

    val assets =
      mapOf(
        "questions/index.json" to indexPayload,
        "questions/en/bank.v1.json" to "other",
      )
    val reader = ContentIndexReader(FakeAssetLoader(assets), json)

    val mismatches = reader.verify(reader.read())

    assertEquals(2, mismatches.size)
    val hashMismatch = mismatches.first { it.path == "questions/en/bank.v1.json" }
    assertEquals(ContentIndexReader.Mismatch.Reason.HASH_MISMATCH, hashMismatch.reason)
    val missingTask = mismatches.first { it.path == "questions/en/tasks/A-1.json" }
    assertEquals(ContentIndexReader.Mismatch.Reason.FILE_MISSING, missingTask.reason)
  }

  @Test
  fun `verify reports missing index`() {
    val reader = ContentIndexReader(FakeAssetLoader(emptyMap()), json)

    val mismatches = reader.verify()

    assertEquals(1, mismatches.size)
    val mismatch = mismatches.single()
    assertEquals("questions/index.json", mismatch.path)
    assertEquals(ContentIndexReader.Mismatch.Reason.INDEX_MISSING, mismatch.reason)
  }

  private fun sha256(content: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(content.toByteArray())
    return bytes.joinToString(separator = "") { "%02x".format(it) }
  }
}
