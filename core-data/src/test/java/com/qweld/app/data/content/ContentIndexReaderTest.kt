package com.qweld.app.data.content

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class ContentIndexReaderTest {

  private class FakeAssetLoader(private val content: String?) : ContentIndexReader.AssetLoader {
    override fun open(path: String) =
      content?.let { ByteArrayInputStream(it.toByteArray()) } ?: throw FileNotFoundException(path)
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
    val reader = ContentIndexReader(FakeAssetLoader(payload), json)

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
    val reader = ContentIndexReader(FakeAssetLoader(null), json)

    val result = reader.read()

    assertNull(result)
  }
}
