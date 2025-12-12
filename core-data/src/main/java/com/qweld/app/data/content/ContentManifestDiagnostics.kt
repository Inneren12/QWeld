package com.qweld.app.data.content

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.InputStream
import java.util.Locale

class ContentManifestDiagnostics
@JvmOverloads
constructor(
  private val assetLoader: ContentIndexReader.AssetLoader,
  private val json: Json = DEFAULT_JSON,
) {

  data class ManifestSummary(
    val version: String?,
    val generatedAt: String?,
    val schema: String?,
    val locales: Map<String, LocaleSummary>,
  ) {
    data class LocaleSummary(
      val total: Int?,
      val tasks: Map<String, Int>,
    )
  }

  enum class ManifestStatus { OK, WARNING, ERROR }

  data class Diagnostics(
    val status: ManifestStatus,
    val messages: List<String>,
    val missingLocales: List<String>,
    val missingTasks: Map<String, List<String>>,
  )

  data class Result(
    val summary: ManifestSummary?,
    val diagnostics: Diagnostics,
  )

  constructor(context: Context, json: Json = DEFAULT_JSON) : this(
    assetLoader = object : ContentIndexReader.AssetLoader {
      private val appContext = context.applicationContext

      override fun open(path: String): InputStream {
        return appContext.assets.open(path)
      }

      override fun list(path: String): List<String> {
        return appContext.assets.list(path)?.toList() ?: emptyList()
      }
    },
    json = json,
  )

  fun load(): Result {
    val summary = runCatching { readManifest() }.getOrNull()
    val diagnostics = evaluate(summary)
    return Result(summary = summary, diagnostics = diagnostics)
  }

  private fun readManifest(): ManifestSummary {
    val payload = assetLoader.open(MANIFEST_PATH).bufferedReader().use { reader -> reader.readText() }
    val root = json.parseToJsonElement(payload).jsonObject
    val locales =
      root[LOCALES_KEY]
        ?.jsonObject
        ?.mapValues { (_, element) -> parseLocale(element) }
        ?.filterValues { it != null }
        ?.mapValues { (_, value) -> value!! }
        ?: emptyMap()

    return ManifestSummary(
      version = root.findString(VERSION_PATHS),
      generatedAt = root.findString(GENERATED_AT_PATHS),
      schema = root.findString(SCHEMA_PATHS),
      locales = locales,
    )
  }

  private fun parseLocale(element: JsonElement?): ManifestSummary.LocaleSummary? {
    val obj = element as? JsonObject ?: return null
    val tasks =
      obj[TASKS_KEY]
        ?.jsonObject
        ?.mapValues { (_, value) -> value.asIntOrNull() }
        ?.filterValues { it != null }
        ?.mapValues { (_, value) -> value!! }
        ?: emptyMap()

    return ManifestSummary.LocaleSummary(
      total = obj[TOTAL_KEY].asIntOrNull(),
      tasks = tasks,
    )
  }

  private fun evaluate(summary: ManifestSummary?): Diagnostics {
    val messages = mutableListOf<String>()
    if (summary == null) {
      return Diagnostics(
        status = ManifestStatus.ERROR,
        messages = listOf("index.json missing or unreadable"),
        missingLocales = REQUIRED_LOCALES,
        missingTasks = emptyMap(),
      )
    }

    val missingLocales = REQUIRED_LOCALES.filterNot { summary.locales.containsKey(it) }
    val missingTasks = LinkedHashMap<String, List<String>>()

    REQUIRED_LOCALES
      .filter { summary.locales.containsKey(it) }
      .forEach { locale ->
        val presentTasks = summary.locales[locale]?.tasks?.keys ?: emptySet()
        val missingForLocale = REQUIRED_TASKS.filterNot { presentTasks.contains(it) }
        if (missingForLocale.isNotEmpty()) {
          missingTasks[locale] = missingForLocale
        }
      }

    messages +=
      buildString {
        append("Manifest loaded")
        summary.schema?.let { append(" (schema $it)") }
        summary.version?.let { append(" – version $it") }
      }
    summary.generatedAt?.let { timestamp -> messages += "Generated at $timestamp" }

    val status =
      when {
        missingLocales.isNotEmpty() || missingTasks.isNotEmpty() -> ManifestStatus.ERROR
        else -> ManifestStatus.OK
      }

    if (missingLocales.isNotEmpty()) {
      val tags = missingLocales.joinToString(", ") { it.uppercase(Locale.US) }
      messages += "Missing locales: $tags"
    }
    if (missingTasks.isNotEmpty()) {
      missingTasks.forEach { (locale, tasks) ->
        val taskList = tasks.joinToString(", ")
        messages += "Missing tasks for ${locale.uppercase(Locale.US)}: $taskList"
      }
    }

    return Diagnostics(
      status = status,
      messages = messages,
      missingLocales = missingLocales,
      missingTasks = missingTasks,
    )
  }

  private fun JsonObject.findString(paths: List<List<String>>): String? {
    return paths.firstNotNullOfOrNull { path ->
      var current: JsonElement? = this
      for (segment in path) {
        current = (current as? JsonObject)?.get(segment) ?: return@firstNotNullOfOrNull null
      }
      (current as? JsonPrimitive)?.contentOrNull
    }
  }

  private fun JsonElement?.asIntOrNull(): Int? {
    return when (this) {
      is JsonPrimitive -> this.intOrNull
      else -> null
    }
  }

  companion object {
    private const val MANIFEST_PATH = "questions/index.json"
    private const val LOCALES_KEY = "locales"
    private const val TASKS_KEY = "tasks"
    private const val TOTAL_KEY = "total"

    private val REQUIRED_LOCALES = listOf("en", "ru")

    private val REQUIRED_TASKS =
      listOf(
        "A-1",
        "A-2",
        "A-3",
        "A-4",
        "A-5",
        "B-6",
        "B-7",
        "C-8",
        "C-9",
        "C-10",
        "C-11",
        "D-12",
        "D-13",
        "D-14",
        "D-15",
      )

    private val VERSION_PATHS =
      listOf(
        listOf("version"),
        listOf("buildId"),
        listOf("metadata", "version"),
        listOf("metadata", "buildId"),
      )

    private val GENERATED_AT_PATHS =
      listOf(
        listOf("generatedAt"),
        listOf("builtAt"),
        listOf("metadata", "generatedAt"),
        listOf("metadata", "builtAt"),
      )

    private val SCHEMA_PATHS = listOf(listOf("schema"))

    private val DEFAULT_JSON =
      Json {
        ignoreUnknownKeys = true
      }
  }
}
