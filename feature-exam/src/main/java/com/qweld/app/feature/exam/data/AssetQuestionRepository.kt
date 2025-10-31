package com.qweld.app.feature.exam.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.io.InputStream
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.system.measureTimeMillis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import timber.log.Timber

class AssetQuestionRepository internal constructor(
  private val assetReader: AssetReader,
  private val localeResolver: () -> String,
  private val json: Json,
) {
  constructor(
    context: Context,
    jsonCodec: Json = DEFAULT_JSON,
  ) : this(
      assetReader =
        AssetReader(
          open = { path -> kotlin.runCatching { context.assets.open(path) }.getOrNull() },
          list = { path -> kotlin.runCatching { context.assets.list(path)?.toList() }.getOrNull() },
        ),
      localeResolver = { resolveLanguage(context.resources.configuration) },
      json = jsonCodec,
    )

  private val cacheLock = Any()
  private val taskCache =
    object : LinkedHashMap<String, List<QuestionDTO>>(CACHE_CAPACITY, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<QuestionDTO>>): Boolean {
        return size > CACHE_CAPACITY
      }
    }

  fun loadQuestions(locale: String? = null, tasks: Set<String>? = null): Result {
    val resolvedLocale = resolveLocale(locale)
    val normalizedTasks = tasks?.filter { it.isNotBlank() }?.toSet()?.takeIf { it.isNotEmpty() }

    if (normalizedTasks != null) {
      val orderedTasks = normalizedTasks.sorted()
      val tasksLog = orderedTasks.joinToString(prefix = "[", postfix = "]")
      var questions: List<QuestionDTO>? = null
      val elapsed = measureTimeMillis { questions = loadFromPerTask(resolvedLocale, orderedTasks) }
      if (questions != null) {
        val questionList = questions!!
        Timber.i(
          "[repo_load] src=per-task tasks=%s count=%d elapsed=%dms",
          tasksLog,
          questionList.size,
          elapsed,
        )
        return Result.Success(locale = resolvedLocale, questions = questionList)
      } else {
        Timber.w("[repo_load] src=per-task tasks=%s fallback=bank", tasksLog)
      }
    }

    var bankOutcome: AssetPayload<List<QuestionDTO>> = AssetPayload.Missing
    val bankElapsed = measureTimeMillis { bankOutcome = loadFromBank(resolvedLocale) }
    when (val outcome = bankOutcome) {
      is AssetPayload.Success -> {
        Timber.i(
          "[repo_load] src=bank tasks=null count=%d elapsed=%dms",
          outcome.value.size,
          bankElapsed,
        )
        return Result.Success(locale = resolvedLocale, questions = outcome.value)
      }
      AssetPayload.Missing -> {
        Timber.w("[repo_load] src=bank tasks=null missing locale=%s", resolvedLocale)
      }
      is AssetPayload.Error -> {
        Timber.e(outcome.throwable, "[repo_load] src=bank tasks=null error locale=%s", resolvedLocale)
        return Result.Error(locale = resolvedLocale, cause = outcome.throwable)
      }
    }

    var singlesOutcome: AssetPayload<List<QuestionDTO>> = AssetPayload.Missing
    val singlesElapsed = measureTimeMillis { singlesOutcome = loadFromSingleFiles(resolvedLocale) }
    return when (val outcome = singlesOutcome) {
      is AssetPayload.Success -> {
        Timber.i(
          "[repo_load] src=single tasks=null count=%d elapsed=%dms",
          outcome.value.size,
          singlesElapsed,
        )
        Result.Success(locale = resolvedLocale, questions = outcome.value)
      }
      AssetPayload.Missing -> {
        Timber.w("[repo_load] src=single tasks=null missing locale=%s", resolvedLocale)
        Result.Missing(resolvedLocale)
      }
      is AssetPayload.Error -> {
        Timber.e(outcome.throwable, "[repo_load] src=single tasks=null error locale=%s", resolvedLocale)
        Result.Error(locale = resolvedLocale, cause = outcome.throwable)
      }
    }
  }

  private fun loadFromPerTask(locale: String, tasks: List<String>): List<QuestionDTO>? {
    val cached = mutableMapOf<String, List<QuestionDTO>>()
    val toLoad = mutableListOf<String>()
    synchronized(cacheLock) {
      for (task in tasks) {
        val cachedQuestions = taskCache[task]
        if (cachedQuestions != null) {
          cached[task] = cachedQuestions
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
          Timber.w("[repo_load] per-task missing task=%s path=%s", task, path)
          return null
        }
        is AssetPayload.Error -> {
          Timber.e(result.throwable, "[repo_load] per-task error task=%s path=%s", task, path)
          return null
        }
      }
    }

    synchronized(cacheLock) {
      for ((task, questions) in loaded) {
        taskCache[task] = questions
        cached[task] = questions
      }
      return tasks.flatMap { task -> cached[task].orEmpty() }
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
          AssetPayload.Missing -> Timber.w("[repo_load] single missing path=%s", path)
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
    val stream = assetReader.open(path) ?: return AssetPayload.Missing
    return try {
      val questions = stream.use { input ->
        val payload = input.bufferedReader().use { reader -> reader.readText() }
        json.decodeFromString(QUESTION_LIST_SERIALIZER, payload)
      }
      AssetPayload.Success(questions)
    } catch (throwable: Throwable) {
      AssetPayload.Error(throwable)
    }
  }

  private fun readSingle(path: String): AssetPayload<QuestionDTO> {
    val stream = assetReader.open(path) ?: return AssetPayload.Missing
    return try {
      val question = stream.use { input ->
        val payload = input.bufferedReader().use { reader -> reader.readText() }
        json.decodeFromString(QuestionDTO.serializer(), payload)
      }
      AssetPayload.Success(question)
    } catch (throwable: Throwable) {
      AssetPayload.Error(throwable)
    }
  }

  private fun resolveLocale(locale: String?): String {
    val candidate = locale ?: localeResolver()
    if (candidate.isBlank()) return DEFAULT_LOCALE
    return candidate.lowercase(Locale.US)
  }

  class AssetReader(
    private val opener: (String) -> InputStream?,
    private val lister: (String) -> List<String>? = { _ -> emptyList() },
  ) {
    fun open(path: String): InputStream? = opener(path)
    fun list(path: String): List<String>? = lister(path)
  }

  sealed interface Result {
    val locale: String

    data class Success(
      override val locale: String,
      val questions: List<QuestionDTO>,
    ) : Result

    data class Missing(override val locale: String) : Result

    data class Error(
      override val locale: String,
      val cause: Throwable,
    ) : Result
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

  companion object {
    private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    private const val DEFAULT_LOCALE = "en"
    private const val CACHE_CAPACITY = 6
    private const val TASKS_DIR = "tasks"
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
