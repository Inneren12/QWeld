package com.qweld.app.feature.exam.data

import android.content.Context
import java.io.InputStream
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

class AssetExplanationRepository internal constructor(
  private val assetReader: AssetReader,
  private val json: Json = DEFAULT_JSON,
) {
  constructor(
    context: Context,
    jsonCodec: Json = DEFAULT_JSON,
  ) : this(
      assetReader = AssetReader { path -> kotlin.runCatching { context.assets.open(path) }.getOrNull() },
      json = jsonCodec,)

  fun loadExplanation(
    locale: String,
    taskId: String,
    questionId: String,
  ): Explanation? {
    val normalizedLocale = locale.ifBlank { DEFAULT_LOCALE }.lowercase(Locale.US)
    val sanitizedTask = taskId.ifBlank { return null }
    val baseId = sanitizeQuestionId(questionId)
    val assetPath = "explanations/$normalizedLocale/$sanitizedTask/${baseId}__explain_${normalizedLocale}.json"
    val stream = assetReader.open(assetPath)
    if (stream == null) {
      Timber.w("[explain_fetch] id=%s locale=%s ok=false source=assets", baseId, normalizedLocale)
      return null
    }

    return stream.use { input ->
      val payload = input.bufferedReader().use { reader -> reader.readText() }
      runCatching {
        val dto = json.decodeFromString(ExplanationDTO.serializer(), payload)
        Timber.i("[explain_fetch] id=%s locale=%s ok=true source=assets", baseId, normalizedLocale)
        dto.toDomain()
      }.getOrElse { throwable ->
        Timber.e(throwable, "[explain_fetch] id=%s locale=%s ok=false source=assets", baseId, normalizedLocale)
        null
      }
    }
  }

  fun interface AssetReader {
    fun open(path: String): InputStream?
  }

  data class Explanation(
    val id: String,
    val summary: String?,
    val steps: List<Step>,
    val whyNot: List<WhyNot>,
    val tips: List<String>,
  ) {
    data class Step(
      val title: String?,
      val text: String?,
    )

    data class WhyNot(
      val choiceId: String?,
      val text: String?,
    )
  }

  @Serializable
  private data class ExplanationDTO(
    val id: String,
    val summary: String? = null,
    val steps: List<StepDTO> = emptyList(),
    @SerialName("why_not") val whyNot: List<WhyNotDTO> = emptyList(),
    val tips: List<String> = emptyList(),
  ) {
    @Serializable
    data class StepDTO(
      val title: String? = null,
      val text: String? = null,
    )

    @Serializable
    data class WhyNotDTO(
      val choiceId: String? = null,
      val text: String? = null,
    )

    fun toDomain(): Explanation {
      return Explanation(
        id = id,
        summary = summary?.takeIf { it.isNotBlank() },
        steps = steps.mapNotNull { dto ->
          val title = dto.title?.takeIf { it.isNotBlank() }
          val text = dto.text?.takeIf { it.isNotBlank() }
          if (title == null && text == null) {
            null
          } else {
            Explanation.Step(title = title, text = text)
          }
        },
        whyNot = whyNot.mapNotNull { dto ->
          val reasonText = dto.text?.takeIf { it.isNotBlank() }
          val choice = dto.choiceId?.takeIf { it.isNotBlank() }
          if (choice == null && reasonText == null) {
            null
          } else {
            Explanation.WhyNot(choiceId = choice, text = reasonText)
          }
        },
        tips = tips.mapNotNull { tip -> tip.takeIf { it.isNotBlank() } },
      )
    }
  }

  companion object {
    private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    private const val DEFAULT_LOCALE = "en"

    private fun sanitizeQuestionId(questionId: String): String {
      return if (questionId.startsWith("Q-", ignoreCase = true)) {
        questionId.substring(2)
      } else {
        questionId
      }
    }
  }
}
