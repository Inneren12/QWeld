package com.qweld.app.data.content

import android.content.Context
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

class ContentIndexReader
@JvmOverloads
constructor(
  private val assetLoader: AssetLoader,
  private val json: Json = DEFAULT_JSON,
) {

  interface AssetLoader {
    fun open(path: String): InputStream
    fun list(path: String): List<String>
  }

  constructor(context: Context, json: Json = DEFAULT_JSON) : this(
    assetLoader = object : AssetLoader {
      private val appContext = context.applicationContext

      override fun open(path: String): InputStream {
        return appContext.assets.open(path)
      }

      override fun list(path: String): List<String> {
        return try {
          appContext.assets.list(path)?.toList() ?: emptyList()
        } catch (_: Exception) {
          emptyList()
        }
      }
    },
    json = json,
  )

  class Result internal constructor(
    val locales: Map<String, Locale>,
    val rawJson: String,
  ) {
    data class Locale(
      val blueprintId: String?,
      val bankVersion: String?,
      val taskIds: List<String>,
      val hasBank: Boolean,
      val hasTaskLabels: Boolean,
    ) {
      val filesCount: Int
        get() = taskIds.size + if (hasBank) 1 else 0 + if (hasTaskLabels) 1 else 0
    }
  }

  fun read(): Result? {
    val candidates =
      assetLoader
        .list(QUESTIONS_ROOT)
        .map { it.lowercase(Locale.US) }
        .filter { locale -> isLocaleCandidate(locale) && SUPPORTED_LOCALES.contains(locale) }
        .sorted()
    if (candidates.isEmpty()) {
      Timber.w("[content_index] no locale directories under %s", QUESTIONS_ROOT)
      return null
    }

    val locales = mutableMapOf<String, Result.Locale>()

    candidates.forEach { candidate ->
      val localeCode = candidate.lowercase(Locale.US)
      val basePath = "$QUESTIONS_ROOT/$localeCode"
      val info = readLocale(basePath)
      locales[localeCode] = info
    }

    if (locales.isEmpty()) {
      Timber.w("[content_index] unable to load any locale info candidates=%s", candidates)
      return null
    }

    val aggregated =
      buildJsonObject {
        locales.keys.sorted().forEach { locale ->
          val info = locales[locale] ?: return@forEach
          put(
            locale,
            buildJsonObject {
              info.blueprintId?.let { put("blueprintId", JsonPrimitive(it)) }
              info.bankVersion?.let { put("bankVersion", JsonPrimitive(it)) }
              put("tasks", json.encodeToJsonElement(info.taskIds))
              put("hasBank", JsonPrimitive(info.hasBank))
              put("hasTaskLabels", JsonPrimitive(info.hasTaskLabels))
              put("files", JsonPrimitive(info.filesCount))
            },
          )
        }
      }
    val rawJson = json.encodeToString(JsonObject.serializer(), aggregated)
    return Result(locales = locales, rawJson = rawJson)
  }

  fun logContentInfo(result: Result) {
    result.locales.forEach { (locale, info) ->
      Timber.i(
        "[content_info] locale=%s blueprint=%s bankVersion=%s files=%d",
        locale,
        info.blueprintId ?: "unknown",
        info.bankVersion ?: "unknown",
        info.filesCount,
      )
    }
  }

  private fun readLocale(basePath: String): Result.Locale {
    val indexMeta = readIndexMeta(basePath)
    val tasks = assetLoader.list("$basePath/$TASKS_DIR").filter { it.endsWith(JSON_SUFFIX) }
    val taskIds = tasks.map { it.removeSuffix(JSON_SUFFIX) }.sorted()
    val hasBank = assetExists("$basePath/$BANK_FILE")
    val hasTaskLabels = assetExists("$basePath/$META_DIR/$TASK_LABELS_FILE")
    return Result.Locale(
      blueprintId = indexMeta?.blueprintId,
      bankVersion = indexMeta?.bankVersion,
      taskIds = taskIds,
      hasBank = hasBank,
      hasTaskLabels = hasTaskLabels,
    )
  }

  private fun readIndexMeta(basePath: String): IndexMeta? {
    val path = "$basePath/$INDEX_FILENAME"
    val payload =
      try {
        assetLoader.open(path).use { stream -> stream.bufferedReader().use { it.readText() } }
      } catch (_: FileNotFoundException) {
        return null
      } catch (error: Exception) {
        Timber.w(error, "[content_index] failed to read manifest path=%s", path)
        return null
      }
    return runCatching { parseIndexMeta(payload) }
      .onFailure { Timber.w(it, "[content_index] failed to parse manifest path=%s", path) }
      .getOrNull()
  }

  private fun parseIndexMeta(payload: String): IndexMeta {
    val obj = json.parseToJsonElement(payload).jsonObject
    val blueprintId = STRING_BLUEPRINT_PATHS.firstNotNullOfOrNull { path -> obj.findString(path) }
    val bankVersion = STRING_BANK_VERSION_PATHS.firstNotNullOfOrNull { path -> obj.findString(path) }
    return IndexMeta(blueprintId = blueprintId, bankVersion = bankVersion)
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
    return current.jsonPrimitive.contentOrNull
  }

  private fun assetExists(path: String): Boolean {
    return try {
      assetLoader.open(path).use { }
      true
    } catch (_: Exception) {
      false
    }
  }

  private fun isLocaleCandidate(name: String): Boolean {
    if (name.isBlank()) return false
    var letterCount = 0
    for (ch in name) {
      if (ch.isLetter()) {
        letterCount += 1
        continue
      }
      if (ch == '-' || ch == '_') {
        continue
      }
      return false
    }
    return letterCount >= 2
  }

  private data class IndexMeta(val blueprintId: String?, val bankVersion: String?)

  companion object {
    private val SUPPORTED_LOCALES = setOf("en")
    private const val QUESTIONS_ROOT = "questions"
    private const val INDEX_FILENAME = "index.json"
    private const val TASKS_DIR = "tasks"
    private const val META_DIR = "meta"
    private const val TASK_LABELS_FILE = "task_labels.json"
    private const val BANK_FILE = "bank.v1.json"
    private const val JSON_SUFFIX = ".json"

    private val DEFAULT_JSON =
      Json {
        ignoreUnknownKeys = true
        prettyPrint = true
      }

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
  }
}
