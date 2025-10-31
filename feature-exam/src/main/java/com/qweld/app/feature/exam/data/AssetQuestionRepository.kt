package com.qweld.app.feature.exam.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.io.InputStream
import java.util.Locale
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
    json: Json = DEFAULT_JSON,
  ) : this(
    assetReader = AssetReader { path -> runCatching { context.assets.open(path) }.getOrNull() },
    localeResolver = { resolveLanguage(context.resources.configuration) },
    json = json,
  )

  fun loadQuestions(locale: String? = null): Result {
    val resolvedLocale = resolveLocale(locale)
    val assetPath = "questions/$resolvedLocale/bank.v1.json"
    val stream = assetReader.open(assetPath)
    if (stream == null) {
      Timber.w("[assets] open=%s ok=false", assetPath)
      return Result.Missing(resolvedLocale)
    }

    return stream.use {
      val jsonPayload = it.bufferedReader().use { reader -> reader.readText() }
      runCatching {
        val questions = json.decodeFromString(ListSerializer(QuestionDTO.serializer()), jsonPayload)
        Timber.i("[assets] open=%s ok=true", assetPath)
        Result.Success(locale = resolvedLocale, questions = questions)
      }.getOrElse { throwable ->
        Timber.e(throwable, "[assets] open=%s ok=false", assetPath)
        Result.Error(locale = resolvedLocale, cause = throwable)
      }
    }
  }

  private fun resolveLocale(locale: String?): String {
    val candidate = locale ?: localeResolver()
    if (candidate.isBlank()) return DEFAULT_LOCALE
    return candidate.lowercase(Locale.US)
  }

  fun interface AssetReader {
    fun open(path: String): InputStream?
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

  companion object {
    private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    private const val DEFAULT_LOCALE = "en"

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
