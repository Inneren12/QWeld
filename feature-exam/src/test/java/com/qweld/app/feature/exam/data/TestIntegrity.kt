package com.qweld.app.feature.exam.data

import java.security.MessageDigest
import com.qweld.app.data.content.questions.IndexParser

internal object TestIntegrity {
  private const val BLUEPRINT_ID = "test-blueprint"
  private const val BANK_VERSION = "test-bank"

  fun addIndexes(payloads: Map<String, ByteArray>): Map<String, ByteArray> {
    val result = payloads.toMutableMap()
    val locales = payloads.keys.mapNotNull { localeFromPath(it) }.toSet()
    for (locale in locales) {
      val files = payloads.filterKeys { belongsToLocale(it, locale) }
      if (files.isEmpty()) continue
      val indexPath = "questions/$locale/index.json"
      result[indexPath] = buildIndex(locale, files)
    }
    return result
  }

  private fun buildIndex(locale: String, files: Map<String, ByteArray>): ByteArray {
    val sorted = files.entries
      .filterNot { (path, _) -> path.endsWith("/index.json") }
      .sortedBy { it.key }
    val fileEntries =
      sorted.joinToString(separator = ",") { (path, bytes) ->
        val normalized = IndexParser.normalizePath(path)
        val hash = sha256(bytes)
        "\"$normalized\":{\"sha256\":\"$hash\"}"
      }
    val json =
      """
        {
          "blueprintId": "$BLUEPRINT_ID",
          "bankVersion": "$BANK_VERSION",
          "files": { $fileEntries }
        }
      """
        .trimIndent()
    return json.toByteArray()
  }

  private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
  }

  private fun localeFromPath(path: String): String? {
    val normalized = IndexParser.normalizePath(path)
    if (!normalized.startsWith("questions/")) return null
    val remainder = normalized.removePrefix("questions/")
    return remainder.substringBefore('/', missingDelimiterValue = "").takeIf { it.isNotBlank() }
  }

  private fun belongsToLocale(path: String, locale: String): Boolean {
    val normalized = IndexParser.normalizePath(path)
    return normalized.startsWith("questions/$locale/")
  }
}
