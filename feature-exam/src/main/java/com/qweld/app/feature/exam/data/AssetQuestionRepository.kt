package com.qweld.app.feature.exam.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.qweld.app.feature.exam.data.Io
import com.qweld.core.common.logging.LogTag
import com.qweld.core.common.logging.Logx
import com.qweld.app.core.i18n.LocaleController
import java.io.InputStream
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.system.measureTimeMillis
import kotlin.text.Charsets
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class AssetQuestionRepository private constructor(
  private val assetReader: AssetReader,
  private val localeResolver: () -> String,
  private val json: Json,
  cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
) {
  constructor(
    context: Context,
    cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
    jsonCodec: Json = DEFAULT_JSON,
  ) : this(
      assetReader = AssetReader(
        opener = { path -> kotlin.runCatching { context.assets.open(path) }.getOrNull() },
        lister = { path -> kotlin.runCatching { context.assets.list(path)?.toList() }.getOrNull() },
      ),
      // ВАЖНО: сначала берём язык из AppCompatDelegate (локаль приложения),
      // потому что applicationContext.resources.configuration может остаться на системной локали.
      localeResolver = {
          // Стало: единая точка правды о языке — LocaleController.currentLanguage()
          // это предотвращает гонку между apply(...) и чтением конфигурации
          LocaleController.currentLanguage(context)
      },
      json = jsonCodec,
      cacheCapacity = cacheCapacity,
    )

  private val cacheLock = Any()
  private val cacheCapacity = cacheCapacity.coerceIn(MIN_CACHE_CAPACITY, MAX_CACHE_CAPACITY)

  private val taskCache =
    object : LinkedHashMap<String, List<QuestionDTO>>(this.cacheCapacity, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<QuestionDTO>>): Boolean {
        return size > this@AssetQuestionRepository.cacheCapacity
      }
    }

  suspend fun loadTaskIntoCache(
    locale: String,
    taskId: String,
  ) {
    val key = cacheKey(locale, taskId)
    synchronized(cacheLock) {
      if (taskCache.containsKey(key)) {
        return
      }
    }

    val path = "questions/$locale/tasks/$taskId.json"
    when (val payload = readArray(path)) {
      is AssetPayload.Success -> {
        synchronized(cacheLock) { taskCache[key] = payload.value }
      }
      AssetPayload.Missing -> {
        throw TaskAssetMissingException(taskId = taskId, path = path)
      }
      is AssetPayload.Error -> {
        throw TaskAssetReadException(taskId = taskId, path = path, cause = payload.throwable)
      }
    }
  }

  fun clearLocaleCache(locale: String) {
    val prefix = cacheKey(locale, "")
    synchronized(cacheLock) {
      val iterator = taskCache.keys.iterator()
      while (iterator.hasNext()) {
        val key = iterator.next()
        if (key.startsWith(prefix)) {
          iterator.remove()
        }
      }
    }
  }

  fun clearAllCaches() {
    synchronized(cacheLock) { taskCache.clear() }
  }

  fun cacheEntryCount(): Int = synchronized(cacheLock) { taskCache.size }

  fun cachedTasks(locale: String): Set<String> {
    val prefix = cacheKey(locale, "")
    return synchronized(cacheLock) {
      taskCache.keys
        .asSequence()
        .filter { it.startsWith(prefix) }
        .map { key -> key.substringAfter("::") }
        .toSet()
    }
  }

  fun loadTaskLabels(locale: String): TaskLabelSet {
    val path = "questions/$locale/meta/$TASK_LABELS_FILE"
    return when (val result = readTaskLabels(path)) {
      is AssetPayload.Success -> result.value
      AssetPayload.Missing -> TaskLabelSet.EMPTY
      is AssetPayload.Error -> {
        Logx.w(
          LogTag.LOAD,
          "labels_error",
          result.throwable,
          "locale" to locale,
          "path" to path,
        )
        TaskLabelSet.EMPTY
      }
    }
  }

  fun loadQuestions(locale: String? = null, tasks: Set<String>? = null): LoadResult {
    val resolvedLocale = resolveLocale(locale)
    val normalizedTasks = tasks?.filter { it.isNotBlank() }?.toSet()?.takeIf { it.isNotEmpty() }

    if (normalizedTasks != null) {
      val orderedTasks = normalizedTasks.sorted()
      val tasksLog = orderedTasks.joinToString(separator = ",", prefix = "[", postfix = "]")
      var perTaskResult: PerTaskLoadResult? = null
      val elapsed =
        measureTimeMillis { perTaskResult = loadFromPerTask(resolvedLocale, orderedTasks) }
      if (perTaskResult != null) {
        val perTask = perTaskResult!!
        val questionList = perTask.questions
        Logx.i(
          LogTag.LOAD,
          "per_task_success",
          "locale" to resolvedLocale,
          "task" to tasksLog,
          "count" to questionList.size,
          "elapsedMs" to elapsed,
          "cacheHits" to perTask.cacheHits,
        )
        return LoadResult.Success(locale = resolvedLocale, questions = questionList)
      } else {
        Logx.w(
          LogTag.LOAD,
          "per_task_fallback",
          "locale" to resolvedLocale,
          "task" to tasksLog,
          "fallback" to "bank",
        )
      }
    }

    var bankOutcome: AssetPayload<List<QuestionDTO>> = AssetPayload.Missing
    val bankElapsed = measureTimeMillis { bankOutcome = loadFromBank(resolvedLocale) }
    when (val outcome = bankOutcome) {
      is AssetPayload.Success -> {
        Logx.i(
          LogTag.LOAD,
          "bank_success",
          "locale" to resolvedLocale,
          "task" to "*",
          "count" to outcome.value.size,
          "elapsedMs" to bankElapsed,
        )
        return LoadResult.Success(locale = resolvedLocale, questions = outcome.value)
      }
      AssetPayload.Missing -> {
        Logx.w(
          LogTag.LOAD,
          "bank_missing",
          "locale" to resolvedLocale,
          "task" to "*",
        )
      }
      is AssetPayload.Error -> {
        val reason = outcome.throwable.toReason(prefix = "bank_asset_error")
        Logx.e(
          LogTag.LOAD,
          "bank_error",
          outcome.throwable,
          "locale" to resolvedLocale,
          "task" to "*",
          "reason" to reason,
        )
        return LoadResult.Corrupt(reason)
      }
    }

    var singlesOutcome: AssetPayload<List<QuestionDTO>> = AssetPayload.Missing
    val singlesElapsed = measureTimeMillis { singlesOutcome = loadFromSingleFiles(resolvedLocale) }
    return when (val outcome = singlesOutcome) {
      is AssetPayload.Success -> {
        Logx.i(
          LogTag.LOAD,
          "raw_success",
          "locale" to resolvedLocale,
          "task" to "*",
          "count" to outcome.value.size,
          "elapsedMs" to singlesElapsed,
        )
        LoadResult.Success(locale = resolvedLocale, questions = outcome.value)
      }
      AssetPayload.Missing -> {
        Logx.w(
          LogTag.LOAD,
          "raw_missing",
          "locale" to resolvedLocale,
          "task" to "*",
        )
        LoadResult.Missing
      }
      is AssetPayload.Error -> {
        val reason = outcome.throwable.toReason(prefix = "raw_asset_error")
        Logx.e(
          LogTag.LOAD,
          "raw_error",
          outcome.throwable,
          "locale" to resolvedLocale,
          "task" to "*",
          "reason" to reason,
        )
        LoadResult.Corrupt(reason)
      }
    }
  }

  private fun loadFromPerTask(locale: String, tasks: List<String>): PerTaskLoadResult? {
    val cached = mutableMapOf<String, List<QuestionDTO>>()
    val toLoad = mutableListOf<String>()
    var cacheHits = 0
    synchronized(cacheLock) {
      for (task in tasks) {
        val cachedQuestions = taskCache[cacheKey(locale, task)]
        if (cachedQuestions != null) {
          cached[task] = cachedQuestions
          cacheHits += 1
        } else {
          toLoad += task
        }
      }
    }

    val loaded = mutableMapOf<String, List<QuestionDTO>>()
    for (task in toLoad) {
      val path = "questions/$locale/tasks/$task.json"
      when (val result = readArray(path)) {
        is AssetPayload.Success -> {
          loaded[task] = result.value
        }
        AssetPayload.Missing -> {
          Logx.w(
            LogTag.LOAD,
            "per_task_missing",
            "locale" to locale,
            "task" to task,
            "path" to path,
          )
          return null
        }
        is AssetPayload.Error -> {
          Logx.e(
            LogTag.LOAD,
            "per_task_error",
            result.throwable,
            "locale" to locale,
            "task" to task,
            "path" to path,
          )
          return null
        }
      }
    }

    synchronized(cacheLock) {
      for ((task, questions) in loaded) {
        taskCache[cacheKey(locale, task)] = questions
        cached[task] = questions
      }
      return PerTaskLoadResult(
        questions = tasks.flatMap { task -> cached[task].orEmpty() },
        cacheHits = cacheHits,
      )
    }
  }

  private fun loadFromBank(locale: String): AssetPayload<List<QuestionDTO>> {
    val path = "questions/$locale/bank.v1.json"
    return when (val result = readArray(path)) {
      is AssetPayload.Success -> AssetPayload.Success(result.value.sortedBy { it.id })
      AssetPayload.Missing -> AssetPayload.Missing
      is AssetPayload.Error -> result
    }
  }

  private fun loadFromSingleFiles(locale: String): AssetPayload<List<QuestionDTO>> {
    val basePath = "questions/$locale"
    val taskEntries = assetReader.list(basePath) ?: return AssetPayload.Missing
    val tasks = taskEntries.filterNot { entry -> entry == TASKS_DIR || entry.endsWith(".json") }.sorted()
    val aggregated = mutableListOf<QuestionDTO>()

    for (task in tasks) {
      val taskPath = "$basePath/$task"
      val questionFiles = assetReader.list(taskPath) ?: continue
      for (file in questionFiles.sorted()) {
        if (!file.endsWith(".json")) continue
        val path = "$taskPath/$file"
        when (val result = readSingle(path)) {
          is AssetPayload.Success -> aggregated += result.value
          AssetPayload.Missing ->
            Logx.w(
              LogTag.LOAD,
              "raw_missing",
              "locale" to locale,
              "task" to task,
              "path" to path,
            )
          is AssetPayload.Error -> return result
        }
      }
    }

    if (aggregated.isEmpty()) {
      return AssetPayload.Missing
    }

    aggregated.sortBy { it.id }
    return AssetPayload.Success(aggregated)
  }

  private fun readArray(path: String): AssetPayload<List<QuestionDTO>> {
    return readAsset(path) { bytes ->
      val payload = bytes.toString(Charsets.UTF_8)
      json.decodeFromString(QUESTION_LIST_SERIALIZER, payload)
    }
  }

  private fun readSingle(path: String): AssetPayload<QuestionDTO> {
    return readAsset(path) { bytes ->
      val payload = bytes.toString(Charsets.UTF_8)
      json.decodeFromString(QuestionDTO.serializer(), payload)
    }
  }

  private fun readTaskLabels(path: String): AssetPayload<TaskLabelSet> {
    return readAsset(path) { bytes ->
      val payload = bytes.toString(Charsets.UTF_8)
      val dto = json.decodeFromString(TaskLabelsPayload.serializer(), payload)
      TaskLabelSet(
        blocks = dto.blocks.normalizeLabels(),
        tasks = dto.tasks.normalizeLabels(),
      )
    }
  }

  private fun Map<String, String>?.normalizeLabels(): Map<String, String> {
    if (this.isNullOrEmpty()) return emptyMap()
    return entries
      .mapNotNull { entry ->
        val key = entry.key.trim().uppercase(Locale.US)
        val value = entry.value.trim()
        if (key.isEmpty() || value.isEmpty()) null else key to value
      }
      .toMap()
  }

  private fun <T> readAsset(
    path: String,
    reader: (ByteArray) -> T,
  ): AssetPayload<T> {
    return runBlocking {
      withContext(Io.pool) {
        val locale = localeFromPath(path)
        if (locale == null) {
          val stream = assetReader.open(path) ?: return@withContext AssetPayload.Missing
          return@withContext try {
            val bytes = stream.use { it.readBytes() }
            AssetPayload.Success(reader(bytes))
          } catch (throwable: Throwable) {
            AssetPayload.Error(throwable)
          }
        }

        val (primary, fallback) = resolveAssetPaths(path)
        val bytes =
          openAsset(primary) ?: fallback?.let { alt -> openAsset(alt) }
            ?: return@withContext AssetPayload.Missing
        runCatching { AssetPayload.Success(reader(bytes)) }
          .getOrElse { throwable -> AssetPayload.Error(throwable) }
      }
    }
  }

  private fun resolveLocale(locale: String?): String {
    val candidate = locale ?: localeResolver()
    if (candidate.isBlank()) return DEFAULT_LOCALE
    return candidate.lowercase(Locale.US)
  }

  private fun cacheKey(locale: String, taskId: String): String {
    return "$locale::$taskId"
  }

  private fun resolveAssetPaths(
    requested: String,
  ): Pair<String, String?> {
    val normalized = normalizePath(requested)
    val alternate = toggleCompressedPath(normalized)
    return normalized to alternate
  }

  private fun normalizePath(path: String): String {
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

  private fun toggleCompressedPath(path: String): String? {
    return when {
      path.endsWith(GZIP_SUFFIX) -> path.removeSuffix(GZIP_SUFFIX)
      path.endsWith(JSON_SUFFIX) -> "$path$GZIP_SUFFIX"
      else -> null
    }
  }

  private fun openAsset(path: String): ByteArray? {
    return assetReader.open(path)?.use { stream ->
      try {
        stream.readBytes()
      } catch (throwable: Throwable) {
        Logx.e(LogTag.LOAD, "read_error", throwable, "path" to path)
        null
      }
    }
  }

  private fun localeFromPath(path: String): String? {
    val normalized = normalizePath(path)
    if (!normalized.startsWith("$QUESTIONS_ROOT/")) return null
    val remainder = normalized.removePrefix("$QUESTIONS_ROOT/")
    val locale = remainder.substringBefore('/', missingDelimiterValue = "")
    return locale.takeIf { it.isNotBlank() }
  }

  private data class PerTaskLoadResult(
    val questions: List<QuestionDTO>,
    val cacheHits: Int,
  )

    private class AssetReader(
        private val opener: (String) -> InputStream?,
        private val lister: (String) -> List<String>? = { _ -> null },
    ) {
        fun open(path: String): InputStream? = opener(path)
        fun list(path: String): List<String>? = lister(path)
    }

  sealed class TaskLoadException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    abstract val taskId: String
    abstract val path: String
  }

  class TaskAssetMissingException(
    override val taskId: String,
    override val path: String,
  ) : TaskLoadException("Task asset missing: $path")

  class TaskAssetReadException(
    override val taskId: String,
    override val path: String,
    cause: Throwable,
  ) : TaskLoadException("Unable to read task asset: $path", cause)

  sealed class LoadResult {
    data class Success(
      val locale: String,
      val questions: List<QuestionDTO>,
    ) : LoadResult()

    object Missing : LoadResult()

    data class Corrupt(val reason: String) : LoadResult()
  }

  private fun Throwable.toReason(prefix: String): String {
    val sanitized = message?.trim()?.replace(Regex("\\s+"), " ")
    return buildString {
      append(prefix)
      append(':')
      append(javaClass.simpleName)
      if (!sanitized.isNullOrEmpty()) {
        append(':')
        append(sanitized)
      }
    }
  }

  @Serializable
  data class QuestionDTO(
    val id: String,
    val taskId: String,
    val blockId: String? = null,
    val locale: String? = null,
    val familyId: String? = null,
    val stem: JsonElement,
    val choices: List<QuestionChoiceDTO>,
    @SerialName("correctId") val correctId: String,
    val rationales: Map<String, String>? = null,
    val tags: List<String>? = null,
    val metadata: JsonElement? = null,
  )

  @Serializable
  data class QuestionChoiceDTO(
    val id: String,
    val text: JsonElement,
    val metadata: JsonElement? = null,
  )

  private sealed interface AssetPayload<out T> {
    data class Success<T>(val value: T) : AssetPayload<T>
    object Missing : AssetPayload<Nothing>
    data class Error(val throwable: Throwable) : AssetPayload<Nothing>
  }

  @Serializable
  private data class TaskLabelsPayload(
    val blocks: Map<String, String>? = null,
    val tasks: Map<String, String>? = null,
  )

  data class TaskLabelSet(
    val blocks: Map<String, String>,
    val tasks: Map<String, String>,
  ) {
    companion object {
      val EMPTY = TaskLabelSet(emptyMap(), emptyMap())
    }
  }

  companion object {
    private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    private const val DEFAULT_LOCALE = "en"
    private const val DEFAULT_CACHE_CAPACITY = 8
    private const val MIN_CACHE_CAPACITY = 4
    private const val MAX_CACHE_CAPACITY = 32
    private const val TASKS_DIR = "tasks"
    private const val TASK_LABELS_FILE = "task_labels.json"
    private const val QUESTIONS_ROOT = "questions"
    private const val JSON_SUFFIX = ".json"
    private const val GZIP_SUFFIX = ".gz"
    private val QUESTION_LIST_SERIALIZER = ListSerializer(QuestionDTO.serializer())

    private fun resolveLanguage(configuration: Configuration): String {
      val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.locales.takeIf { it.size() > 0 }?.get(0)?.language
      } else {
        @Suppress("DEPRECATION")
        configuration.locale?.language
      }
      return language?.takeIf { it.isNotBlank() } ?: Locale.ENGLISH.language
    }
  }
}
