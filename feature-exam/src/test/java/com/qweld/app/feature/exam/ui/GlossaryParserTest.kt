package com.qweld.app.feature.exam.ui

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlossaryParserTest {

  @Test
  fun parseGlossaryFile_containsEntries() {
    val path = Paths.get("docs", "glossary", "glossary_ru.json")
    val payload = Files.readString(path)

    val entries = parseGlossaryJson(payload)

    assertTrue(entries.isNotEmpty())
    val first = entries.first()
    assertEquals("Shielded metal arc welding", first.term)
    assertEquals("Ручная дуговая сварка", first.translation)
  }
}
