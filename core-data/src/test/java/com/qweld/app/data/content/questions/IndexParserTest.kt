package com.qweld.app.data.content.questions

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class IndexParserTest {

  private val parser = IndexParser()

  @Test
  fun parsesFilesObjectFormat() {
    val json = """
      {
        "blueprintId": "welder_bp",
        "bankVersion": "2024.1",
        "files": {
          "questions/en/bank.v1.json": { "sha256": "abc" },
          "questions/en/tasks/A-1.json": { "sha256": "def" }
        }
      }
    """.trimIndent()

    val manifest = parser.parse(json)

    assertEquals("welder_bp", manifest.blueprintId)
    assertEquals("2024.1", manifest.bankVersion)
    assertEquals("abc", manifest.expectedFor("questions/en/bank.v1.json"))
    assertEquals("abc", manifest.expectedFor("en/bank.v1.json"))
    assertEquals("def", manifest.expectedFor("questions/en/tasks/A-1.json"))
    assertNull(manifest.expectedFor("questions/en/tasks/B-1.json"))
  }

  @Test
  fun parsesEntriesArrayFormat() {
    val json = """
      {
        "metadata": {
          "blueprint": { "id": "welder_bp" },
          "bankVersion": "2024.2"
        },
        "entries": [
          { "path": "questions/en/bank.v1.json", "sha256": "abc" },
          { "path": "questions/en/bank.v1.json.gz", "sha256": "def" }
        ]
      }
    """.trimIndent()

    val manifest = parser.parse(json)

    assertEquals("welder_bp", manifest.blueprintId)
    assertEquals("2024.2", manifest.bankVersion)
    assertEquals("abc", manifest.expectedFor("questions/en/bank.v1.json"))
    assertEquals("def", manifest.expectedFor("questions/en/bank.v1.json.gz"))
    assertEquals(setOf("questions/en/bank.v1.json", "questions/en/bank.v1.json.gz"), manifest.paths())
  }

  @Test
  fun fallsBackToLocaleFiles() {
    val json = """
      {
        "locales": {
          "en": {
            "files": {
              "questions/en/bank.v1.json": { "sha256": "abc" }
            }
          }
        }
      }
    """.trimIndent()

    val manifest = parser.parse(json)

    assertNull(manifest.blueprintId)
    assertNull(manifest.bankVersion)
    assertEquals("abc", manifest.expectedFor("questions/en/bank.v1.json"))
  }
}
