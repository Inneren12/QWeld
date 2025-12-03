package com.qweld.app.data.export

import android.annotation.SuppressLint
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.exam.mapTaskToBlock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter

class AttemptExporter(
  private val attemptsRepository: AttemptsRepository,
  private val answersRepository: AnswersRepository,
  private val json: Json = DEFAULT_JSON,
  private val clock: Clock = Clock.systemUTC(),
  private val versionProvider: () -> String = { "" },
  private val errorLogger: (String, Throwable) -> Unit = { message, throwable ->
      Timber.tag(TAG).e(throwable, message)
  },
) {
  suspend fun exportAttemptJson(attemptId: String): String {
    return runCatching {
      val attempt =
        attemptsRepository.getById(attemptId)
          ?: throw AttemptNotFoundException("Attempt $attemptId not found")
      val answers = answersRepository.listByAttempt(attemptId)
      val payload = buildPayload(attempt, answers)
      json.encodeToString(payload)
    }.getOrElse { throwable ->
      errorLogger(
        "[export_attempt_error] id=$attemptId reason=${throwable.message ?: throwable::class.java.simpleName}",
        throwable,
      )
      throw throwable
    }
  }

  private fun buildPayload(
    attempt: AttemptEntity,
    answers: List<AnswerEntity>,
  ): AttemptExportPayload {
    val contexts = answers.map { answer ->
      val taskId = extractTaskId(answer.questionId)
      val blockId = resolveBlockId(taskId)
      AnswerContext(taskId = taskId, blockId = blockId, isCorrect = answer.isCorrect)
    }
    val summaries =
      SummariesJson(
        byBlock = aggregateSummaries(contexts) { it.blockId },
        byTask = aggregateSummaries(contexts) { it.taskId },
      )
    val attemptSection =
      AttemptSection(
        id = attempt.id,
        mode = attempt.mode,
        locale = attempt.locale,
        seed = attempt.seed,
        questionCount = attempt.questionCount,
        startedAt = attempt.startedAt,
        finishedAt = attempt.finishedAt,
        durationSec = attempt.durationSec,
        scorePct = attempt.scorePct,
        passThreshold = attempt.passThreshold,
      )
    val answersJson = answers.map { answer ->
      AnswerJson(
        schema = ANSWER_SCHEMA,
        attemptId = attempt.id,
        mode = attempt.mode,
        locale = attempt.locale,
        attemptSeed = attempt.seed,
        displayIndex = answer.displayIndex,
        questionId = answer.questionId,
        selectedId = answer.selectedId,
        correctId = answer.correctId,
        isCorrect = answer.isCorrect,
        timeSpentSec = answer.timeSpentSec,
        seenAt = toIsoString(answer.seenAt),
        answeredAt = toIsoString(answer.answeredAt),
        telemetry = AnswerTelemetryJson(),
      )
    }
    val meta = MetaJson(appVersion = versionProvider(), exportedAt = toIsoString(clock.instant()))
    return AttemptExportPayload(
      schema = ATTEMPT_SCHEMA,
      attempt = attemptSection,
      answers = answersJson,
      summaries = summaries,
      meta = meta,
    )
  }

  private fun aggregateSummaries(
    contexts: List<AnswerContext>,
    keySelector: (AnswerContext) -> String,
  ): Map<String, SummaryJson> {
    if (contexts.isEmpty()) return emptyMap()
    return contexts
      .groupBy(keySelector)
      .toSortedMap()
      .mapValues { (_, values) ->
        val total = values.size
        val correct = values.count { it.isCorrect }
        SummaryJson(total = total, correct = correct, pct = percentage(correct, total))
      }
  }

  private fun percentage(correct: Int, total: Int): Double {
    if (total == 0) return 0.0
    return (correct.toDouble() / total.toDouble()) * 100.0
  }

  private fun extractTaskId(questionId: String): String {
    val trimmed = questionId.removePrefix("Q-")
    val base = trimmed.substringBefore('_', trimmed)
    return base.ifBlank { questionId }
  }

  private fun resolveBlockId(taskId: String): String {
    val mapped = mapTaskToBlock(taskId)
    if (mapped != null) return mapped
    val fallback = taskId.substringBefore('-')
    return fallback.ifBlank { taskId.take(1).ifBlank { taskId } }
  }

  private fun toIsoString(epochMillis: Long): String {
    return toIsoString(Instant.ofEpochMilli(epochMillis))
  }

  private fun toIsoString(instant: Instant): String {
    return DateTimeFormatter.ISO_INSTANT.format(instant)
  }

  private data class AnswerContext(
    val taskId: String,
    val blockId: String,
    val isCorrect: Boolean,
  )

  private class AttemptNotFoundException(message: String) : IllegalArgumentException(message)

  companion object {
    private const val TAG = "AttemptExporter"
    private const val ATTEMPT_SCHEMA = "qweld.attempt.v1"
    private const val ANSWER_SCHEMA = "qweld.answer.v1"
    private val DEFAULT_JSON = Json { prettyPrint = true }
  }
}

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class AttemptExportPayload(
  val schema: String,
  val attempt: AttemptSection,
  val answers: List<AnswerJson>,
  val summaries: SummariesJson,
  val meta: MetaJson,
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class AttemptSection(
  val id: String,
  val mode: String,
  val locale: String,
  val seed: Long,
  val questionCount: Int,
  val startedAt: Long,
  val finishedAt: Long?,
  val durationSec: Int?,
  val scorePct: Double?,
  val passThreshold: Int?,
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class AnswerJson(
  val schema: String,
  val attemptId: String,
  val mode: String,
  val locale: String,
  val attemptSeed: Long,
  val displayIndex: Int,
  val questionId: String,
  val selectedId: String,
  val correctId: String,
  val isCorrect: Boolean,
  val timeSpentSec: Int,
  val seenAt: String,
  val answeredAt: String,
  val telemetry: AnswerTelemetryJson,
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class AnswerTelemetryJson(
  val shuffledChoiceOrder: List<String> = listOf("A", "B", "C", "D"),
  val fallbackLocaleUsed: Boolean = false,
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class SummariesJson(
  val byBlock: Map<String, SummaryJson>,
  val byTask: Map<String, SummaryJson>,
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class SummaryJson(
  val total: Int,
  val correct: Int,
  val pct: Double,
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class MetaJson(
  val appVersion: String,
  val exportedAt: String,
)
