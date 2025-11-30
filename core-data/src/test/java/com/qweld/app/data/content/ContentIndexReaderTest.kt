package com.qweld.app.data.content

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ContentIndexReaderTest {

  private class FakeAssetLoader(
    private val assets: Map<String, ByteArray>,
    private val listings: Map<String, List<String>>,
  ) : ContentIndexReader.AssetLoader {

    override fun open(path: String): InputStream {
      val normalized = path.replace('\\', '/')
      val bytes = assets[normalized] ?: throw IllegalArgumentException("Missing asset $path")
      return ByteArrayInputStream(bytes)
    }

    override fun list(path: String): List<String> {
      val normalized = path.replace('\\', '/')
      return listings[normalized] ?: emptyList()
    }
  }

  @Test
  fun `read locale info from assets without index`() {
    val assets =
      mapOf(
        "questions/en/bank.v1.json" to "[]".toByteArray(),
        "questions/en/tasks/A-1.json" to "[]".toByteArray(),
        "questions/en/meta/task_labels.json" to "{}".toByteArray(),
      )
    val listings =
      mapOf(
        "questions" to listOf("en"),
        "questions/en" to listOf("tasks", "meta", "bank.v1.json"),
        "questions/en/tasks" to listOf("A-1.json"),
        "questions/en/meta" to listOf("task_labels.json"),
      )
    val reader = ContentIndexReader(FakeAssetLoader(assets, listings), Json { ignoreUnknownKeys = true })

    val result = reader.read()

    assertNotNull(result)
    val locale = result!!.locales["en"]
    assertNotNull(locale)
    assertEquals(listOf("A-1"), locale!!.taskIds)
    assertEquals(3, locale.filesCount)
  }

  @Test
  fun `parse blueprint and bank version from optional index`() {
    val indexJson =
      """
      {
        "blueprintId": "welder_bp",
        "bankVersion": "v1"
      }
      """
        .trimIndent()
    val assets =
      mapOf(
        "questions/ru/index.json" to indexJson.toByteArray(),
        "questions/ru/bank.v1.json" to "[]".toByteArray(),
      )
    val listings =
      mapOf(
        "questions" to listOf("ru"),
        "questions/ru" to listOf("index.json", "bank.v1.json"),
        "questions/ru/tasks" to emptyList(),
      )

    val reader = ContentIndexReader(FakeAssetLoader(assets, listings), Json { ignoreUnknownKeys = true })

    val result = reader.read()

    assertEquals("welder_bp", result!!.locales["ru"]?.blueprintId)
    assertEquals("v1", result.locales["ru"]?.bankVersion)
  }
}
