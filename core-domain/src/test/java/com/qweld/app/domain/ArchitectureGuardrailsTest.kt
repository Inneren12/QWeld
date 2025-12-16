package com.qweld.app.domain

import java.nio.file.Files
import java.nio.file.Path
import kotlin.sequences.asSequence
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureGuardrailsTest {
  @Test
  fun domainSourcesDoNotReferenceAndroidApis() {
    val sourceRoot = Path.of("src/main/java")
    val forbiddenPrefixes = listOf("import android.", "import androidx.")

    val violations: List<String> = Files.walk(sourceRoot).use { paths ->
      paths
        .iterator()
        .asSequence()
        .filter { path -> path.toString().endsWith(".kt") }
        .flatMap { path ->
          Files.readAllLines(path)
            .withIndex()
            .asSequence()
            .filter { (_, line) ->
              forbiddenPrefixes.any { prefix -> line.trimStart().startsWith(prefix) }
            }
            .map { (index, line) ->
              "${path}:L${index + 1} contains '${line.trim()}'"
            }
        }
        .toList()
    }

    assertTrue(
      violations.isEmpty(),
      buildString {
        appendLine("Domain layer must remain Android-free. Found disallowed imports:")
  })
}}
