package com.qweld.app.data.content.questions

import java.io.InputStream
import java.util.LinkedHashMap
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.parseToJsonElement

class IndexParser(private val json: Json = DEFAULT_JSON) {

  data class Manifest(
    val blueprintId: String?,
    val bankVersion: String?,
    private val expected: Map<String, String>,
  ) {
    fun expectedFor(path: String): String? {
      return normalizedVariants(path).firstNotNullOfOrNull { candidate -> expected[candidate] }
    }

    fun hasPath(path: String): Boolean {
      return expectedFor(path) != null
    }

    fun normalize(path: String): String {
      return normalizePath(path)
    }

    fun paths(): Set<String> = expected.keys

    internal fun normalizedVariants(path: String): Sequence<String> {
      val normalized = normalize(path)
      val stripped = normalized.removePrefix(QUESTIONS_PREFIX)
      return sequence {
        yield(normalized)
        if (stripped != normalized) {
          yield(stripped)
        }
      }
    }

    internal fun expectedMap(): Map<String, String> = expected
  }

  fun parse(payload: String): Manifest {
    val element = json.parseToJsonElement(payload)
    val obj = element.jsonObject
    val blueprintId = extractBlueprintId(obj)
    val bankVersion = extractBankVersion(obj)
    val expected = LinkedHashMap<String, String>()
    collectFiles(obj[FILES_KEY], expected)
    collectEntries(obj[ENTRIES_KEY], expected)
    if (expected.isEmpty()) {
      obj[LOCALES_KEY]?.let { collectLocaleEntries(it, expected) }
    }
    return Manifest(blueprintId = blueprintId, bankVersion = bankVersion, expected = expected)
  }

  fun parse(stream: InputStream): Manifest {
    val payload = stream.bufferedReader().use { reader -> reader.readText() }
    return parse(payload)
  }

  private fun collectFiles(element: JsonElement?, sink: MutableMap<String, String>) {
    val obj = element?.asObject() ?: return
    for ((path, value) in obj) {
      val expected = extractSha(value)
      if (!expected.isNullOrBlank()) {
        sink[normalizePath(path)] = expected.lowercase(Locale.US)
      }
    }
  }

  private fun collectEntries(element: JsonElement?, sink: MutableMap<String, String>) {
    val array = element?.asArray() ?: return
    for (entry in array) {
      val obj = entry.asObject() ?: continue
      val path = obj[PATH_KEY]?.asString()
      val sha = extractSha(obj[SHA256_KEY]) ?: extractSha(obj[HASH_KEY])
      if (!path.isNullOrBlank() && !sha.isNullOrBlank()) {
        sink[normalizePath(path)] = sha.lowercase(Locale.US)
      }
    }
  }

  private fun collectLocaleEntries(element: JsonElement, sink: MutableMap<String, String>) {
    val locales = element.asObject() ?: return
    for ((_, localeElement) in locales) {
      val localeObject = localeElement.asObject() ?: continue
      val filesElement = localeObject[FILES_KEY]
      collectFiles(filesElement, sink)
      val entriesElement = localeObject[ENTRIES_KEY]
      collectEntries(entriesElement, sink)
    }
  }

  private fun extractBlueprintId(obj: JsonObject): String? {
    return STRING_BLUEPRINT_PATHS.firstNotNullOfOrNull { path -> obj.findString(path) }
  }

  private fun extractBankVersion(obj: JsonObject): String? {
    return STRING_BANK_VERSION_PATHS.firstNotNullOfOrNull { path -> obj.findString(path) }
  }

  private fun extractSha(element: JsonElement?): String? {
    if (element == null || element is JsonNull) return null
    if (element is JsonPrimitive) return element.contentOrNull
    val obj = element.asObject() ?: return null
    return obj[SHA256_KEY]?.asString() ?: obj[HASH_KEY]?.asString()
  }

  private fun JsonObject.findString(path: List<String>): String? {
    var current: JsonElement = this
    for (segment in path) {
      val next = when (current) {
        is JsonObject -> current[segment]
        else -> null
      } ?: return null
      current = next
    }
    return (current as? JsonPrimitive)?.contentOrNull
  }

  private fun JsonElement?.asObject(): JsonObject? {
    return when (this) {
      null -> null
      is JsonObject -> this
      else -> null
    }
  }

  private fun JsonElement?.asArray(): JsonArray? {
    return when (this) {
      null -> null
      is JsonArray -> this
      else -> null
    }
  }

  private fun JsonElement?.asString(): String? {
    return when (this) {
      null -> null
      is JsonPrimitive -> this.contentOrNull
      else -> null
    }
  }

  companion object {
    private const val FILES_KEY = "files"
    private const val ENTRIES_KEY = "entries"
    private const val PATH_KEY = "path"
    private const val SHA256_KEY = "sha256"
    private const val HASH_KEY = "hash"
    private const val LOCALES_KEY = "locales"
    private const val QUESTIONS_PREFIX = "questions/"

    private val STRING_BLUEPRINT_PATHS =
      listOf(
        listOf("blueprintId"),
        listOf("blueprint"),
        listOf("blueprint", "id"),
        listOf("metadata", "blueprintId"),
        listOf("metadata", "blueprint", "id"),
      )

    private val STRING_BANK_VERSION_PATHS =
      listOf(
        listOf("bankVersion"),
        listOf("bank", "version"),
        listOf("metadata", "bankVersion"),
        listOf("version"),
      )

    private val DEFAULT_JSON =
      Json {
        ignoreUnknownKeys = true
      }

    fun normalizePath(path: String): String {
      var result = path.trim()
      if (result.startsWith("./")) result = result.removePrefix("./")
      while (result.startsWith('/')) {
        result = result.removePrefix("/")
      }
      result = result.replace('\\', '/')
      while (result.contains("//")) {
        result = result.replace("//", "/")
      }
      return result
    }
  }
}
