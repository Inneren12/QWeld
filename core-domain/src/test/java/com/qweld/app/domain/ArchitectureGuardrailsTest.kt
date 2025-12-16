package com.qweld.app.domain

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureGuardrailsTest {
  @Test
  fun domainSourcesDoNotReferenceAndroidApis() {
    val sourceRoot = Path.of("src/main/java")
    val forbiddenPrefixes = listOf("import android.", "import androidx.")

    val violations =
      Files.walk(sourceRoot)
        .filter { it.toString().endsWith(".kt") }
        .flatMap { path ->
          Files.readAllLines(path).withIndex().stream().filter { indexedLine ->
            forbiddenPrefixes.any { prefix -> indexedLine.value.trimStart().startsWith(prefix) }
          }.map { indexedLine ->
            "${path.toString()}:L${indexedLine.index + 1} contains '${indexedLine.value.trim()}'"
          }
        }
        .collect(Collectors.toList())

    assertTrue(violations.isEmpty()) {
      buildString {
        appendLine("Domain layer must remain Android-free. Found disallowed imports:")
        violations.forEach { appendLine(" - $it") }
      }
    }
  }
}
