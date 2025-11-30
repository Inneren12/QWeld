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

  private class FakeAssetLoader(
    private val assets: Map<String, String>,
    private val listings: Map<String, List<String>> = emptyMap(),
  ) : ContentIndexReader.AssetLoader {
    override fun open(path: String) =
      assets[path]?.let { ByteArrayInputStream(it.toByteArray()) } ?: throw FileNotFoundException(path)

    override fun list(path: String): List<String> = listings[path] ?: emptyList()
  }

  private val json = Json { ignoreUnknownKeys = false }

  @Test
  fun `read parses locale manifest`() {
    val payload =
      """
      {
        "blueprintId": "welder_bp",
        "bankVersion": "v1",
        "files": {
          "questions/en/bank.v1.json": {"sha256": "abc"},
          "questions/en/tasks/A-1.json": {"sha256": "def"}
        }
      }
      """
        .trimIndent()
    val listings = mapOf("questions" to listOf("en"))
    val assets = mapOf("questions/en/index.json" to payload)
    val reader = ContentIndexReader(FakeAssetLoader(assets, listings), json)

    val result = reader.read()

    assertNotNull(result)
    val locale = result.locales.getValue("en")
    assertEquals("welder_bp", locale.blueprintId)
    assertEquals("v1", locale.bankVersion)
    assertEquals(2, locale.files.size)
    assertEquals("questions/en/bank.v1.json", locale.files.first().path)
    assertEquals("abc", locale.files.first().sha256)
    val expectedJson =
      json.parseToJsonElement(
        """
        {
          "en": {
            "blueprintId": "welder_bp",
            "bankVersion": "v1",
            "files": {
              "questions/en/bank.v1.json": {"sha256": "abc"},
              "questions/en/tasks/A-1.json": {"sha256": "def"}
            }
          }
        }
        """
          .trimIndent()
      )
    assertEquals(expectedJson, json.parseToJsonElement(result.rawJson))
  }

  @Test
  fun `read returns null when locale list empty`() {
    val reader = ContentIndexReader(FakeAssetLoader(emptyMap(), emptyMap()), json)

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
        "blueprintId": "welder_bp",
        "bankVersion": "v1",
        "files": {
          "questions/en/bank.v1.json": {"sha256": "${sha256(bankContent)}"},
          "questions/en/tasks/A-1.json": {"sha256": "${sha256(taskContent)}"}
        }
      }
      """
        .trimIndent()

    val assets =
      mapOf(
        "questions/en/index.json" to indexPayload,
        "questions/en/bank.v1.json" to bankContent,
        "questions/en/tasks/A-1.json" to taskContent,
      )
    val listings = mapOf("questions" to listOf("en"))
    val reader = ContentIndexReader(FakeAssetLoader(assets, listings), json)

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
        "blueprintId": "welder_bp",
        "bankVersion": "v1",
        "files": {
          "questions/en/bank.v1.json": {"sha256": "${sha256(bankContent)}"},
          "questions/en/tasks/A-1.json": {"sha256": "${sha256(taskContent)}"}
        }
      }
      """
        .trimIndent()

    val assets =
      mapOf(
        "questions/en/index.json" to indexPayload,
        "questions/en/bank.v1.json" to "other",
      )
    val listings = mapOf("questions" to listOf("en"))
    val reader = ContentIndexReader(FakeAssetLoader(assets, listings), json)

    val mismatches = reader.verify(reader.read())

    assertEquals(2, mismatches.size)
    val hashMismatch = mismatches.first { it.path == "questions/en/bank.v1.json" }
    assertEquals(ContentIndexReader.Mismatch.Reason.HASH_MISMATCH, hashMismatch.reason)
    val missingTask = mismatches.first { it.path == "questions/en/tasks/A-1.json" }
    assertEquals(ContentIndexReader.Mismatch.Reason.FILE_MISSING, missingTask.reason)
  }

  @Test
  fun `verify reports missing manifest`() {
    val listings = mapOf("questions" to listOf("en"))
    val reader = ContentIndexReader(FakeAssetLoader(emptyMap(), listings), json)

    val mismatches = reader.verify()

    assertEquals(1, mismatches.size)
    val mismatch = mismatches.single()
    assertEquals("questions/en/index.json", mismatch.path)
    assertEquals(ContentIndexReader.Mismatch.Reason.INDEX_MISSING, mismatch.reason)
  }

  private fun sha256(content: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(content.toByteArray())
    return bytes.joinToString(separator = "") { "%02x".format(it) }
  }
}
