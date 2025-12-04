package com.qweld.app.feature.exam.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlossaryParserTest {

  @Test
  fun parseGlossaryFile_containsEntries() {
    val payload = readString("/docs/glossary/glossary_ru.json")

    val entries = parseGlossaryJson(payload)

    assertTrue(entries.isNotEmpty())
    val first = entries.first()
    assertEquals("Shielded metal arc welding", first.term)
    assertEquals("Ручная дуговая сварка", first.translation)
  }
}

private fun readString(resourcePath: String): String {
  val stream = {}::class.java.getResourceAsStream(resourcePath)
    ?: error("Test resource not found: $resourcePath")
  return stream.bufferedReader().use { it.readText() }
}
