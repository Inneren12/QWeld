package com.qweld.app.data.content

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ContentManifestDiagnosticsTest {

  private class FakeAssetLoader(private val assets: Map<String, String>) : ContentIndexReader.AssetLoader {
    override fun open(path: String): InputStream {
      val payload = assets[path] ?: error("Missing asset for path=$path")
      return ByteArrayInputStream(payload.toByteArray())
    }

    override fun list(path: String): List<String> = emptyList()
  }

  @Test
  fun `load returns OK when manifest has required locales and tasks`() {
    val manifest =
      """
      {
        "schema": "questions-index-v1",
        "generatedAt": "2024-06-01T00:00:00Z",
        "version": "build-123",
        "locales": {
          "en": {
            "total": 10,
            "tasks": { "A-1": 1, "A-2": 1, "A-3": 1, "A-4": 1, "A-5": 1, "B-6": 1, "B-7": 1, "C-8": 1, "C-9": 1, "C-10": 1, "C-11": 1, "D-12": 1, "D-13": 1, "D-14": 1, "D-15": 1 }
          },
          "ru": {
            "total": 10,
            "tasks": { "A-1": 1, "A-2": 1, "A-3": 1, "A-4": 1, "A-5": 1, "B-6": 1, "B-7": 1, "C-8": 1, "C-9": 1, "C-10": 1, "C-11": 1, "D-12": 1, "D-13": 1, "D-14": 1, "D-15": 1 }
          }
        }
      }
      """
    val loader = FakeAssetLoader(mapOf("questions/index.json" to manifest))
    val diagnostics = ContentManifestDiagnostics(loader).load()

    assertEquals(ContentManifestDiagnostics.ManifestStatus.OK, diagnostics.diagnostics.status)
    assertEquals("build-123", diagnostics.summary?.version)
    assertEquals("2024-06-01T00:00:00Z", diagnostics.summary?.generatedAt)
    assertTrue(diagnostics.diagnostics.missingLocales.isEmpty())
    assertTrue(diagnostics.diagnostics.missingTasks.isEmpty())
  }

  @Test
  fun `load returns error when manifest is missing locales`() {
    val manifest =
      """
      {
        "schema": "questions-index-v1",
        "generatedAt": "2024-06-01T00:00:00Z",
        "locales": {
          "en": { "tasks": { "A-1": 1 } }
        }
      }
      """
    val loader = FakeAssetLoader(mapOf("questions/index.json" to manifest))
    val diagnostics = ContentManifestDiagnostics(loader, Json { ignoreUnknownKeys = true }).load()

    assertEquals(ContentManifestDiagnostics.ManifestStatus.ERROR, diagnostics.diagnostics.status)
    assertEquals(listOf("ru"), diagnostics.diagnostics.missingLocales)
    assertTrue(diagnostics.diagnostics.missingTasks.containsKey("en"))
  }
}
