package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.domain.exam.AssembledQuestion
import com.qweld.app.domain.exam.AttemptSeed
import com.qweld.app.domain.exam.ExamAssembler
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.Question
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class ResumeUseCase(
  private val repository: AssetQuestionRepository,
  private val statsRepository: UserStatsRepository,
  private val blueprintProvider: (ExamMode, Int) -> ExamBlueprint,
  private val userIdProvider: () -> String,
  private val ioDispatcher: CoroutineDispatcher,
) {

  data class Reconstruction(
    val attempt: com.qweld.app.domain.exam.ExamAttempt,
    val rationales: Map<String, String>,
  )

  data class MergeState(
    val answers: Map<String, String>,
    val currentIndex: Int,
  )

  suspend fun reconstructAttempt(
    mode: ExamMode,
    seed: Long,
    locale: String,
    questionCount: Int,
  ): Reconstruction {
    val normalizedLocale = locale.lowercase(Locale.US)
    val blueprint = blueprintProvider(mode, questionCount)
    val requestedTasks =
      blueprint.taskQuotas.mapNotNull { quota -> quota.taskId.takeIf { it.isNotBlank() } }.toSet()
    val questionsResult = repository.loadQuestions(normalizedLocale, tasks = requestedTasks)
    val (domainQuestions, rationales) = when (questionsResult) {
      is AssetQuestionRepository.Result.Success -> {
        val rationales =
          questionsResult.questions.mapNotNull { dto ->
            val rationale = dto.rationales?.get(dto.correctId)?.takeIf { it.isNotBlank() }
            rationale?.let { dto.id to it }
          }.toMap()
        val domain = questionsResult.questions.map { dto -> dto.toDomain(normalizedLocale) }
        domain to rationales
      }
      is AssetQuestionRepository.Result.Missing -> {
        throw IllegalStateException("Question bank missing for ${questionsResult.locale}")
      }
      is AssetQuestionRepository.Result.Error -> {
        throw IllegalStateException("Failed to load questions", questionsResult.cause)
      }
    }

    val assembler =
      ExamAssembler(
        questionRepository = InMemoryQuestionRepository(domainQuestions),
        statsRepository = statsRepository,
        logger = { },
      )

    val attempt =
      withContext(ioDispatcher) {
        assembler.assemble(
          userId = userIdProvider(),
          mode = mode,
          locale = normalizedLocale,
          seed = AttemptSeed(seed),
          blueprint = blueprint,
        )
      }

    return Reconstruction(attempt = attempt, rationales = rationales)
  }

  fun mergeAnswers(
    questions: List<AssembledQuestion>,
    answers: List<AnswerEntity>,
  ): MergeState {
    val answersById = answers.associate { it.questionId to it.selectedId }
    val firstUnansweredIndex =
      questions.indexOfFirst { assembled -> answersById[assembled.question.id] == null }
    val index =
      when {
        questions.isEmpty() -> 0
        firstUnansweredIndex >= 0 -> firstUnansweredIndex
        else -> questions.lastIndex
      }
    return MergeState(answers = answersById, currentIndex = index)
  }

  fun remainingTime(
    attempt: AttemptEntity,
    mode: ExamMode,
    nowMillis: Long,
  ): Duration {
    if (mode != ExamMode.IP_MOCK) return Duration.ZERO
    val elapsedMillis = (nowMillis - attempt.startedAt).coerceAtLeast(0L)
    val elapsed = Duration.ofMillis(elapsedMillis)
    val remaining = TimerController.EXAM_DURATION.minus(elapsed)
    return if (remaining.isNegative) Duration.ZERO else remaining
  }

  private class InMemoryQuestionRepository(
    private val questions: List<Question>,
  ) : QuestionRepository {
    override fun listByTaskAndLocale(
      taskId: String,
      locale: String,
      allowFallbackToEnglish: Boolean,
    ): List<Question> {
      val normalizedLocale = locale.lowercase(Locale.US)
      val primary =
        questions.filter { it.taskId == taskId && it.locale.equals(normalizedLocale, ignoreCase = true) }
      if (primary.isNotEmpty() || !allowFallbackToEnglish) {
        return primary
      }
      return questions.filter { it.taskId == taskId && it.locale.equals("en", ignoreCase = true) }
    }
  }

  private fun AssetQuestionRepository.QuestionDTO.toDomain(defaultLocale: String): Question {
    val resolvedLocale = this.locale?.takeIf { it.isNotBlank() }?.lowercase(Locale.US) ?: defaultLocale
    val stemMap = stem.toTextMap(resolvedLocale).takeIf { it.isNotEmpty() } ?: mapOf(resolvedLocale to "")
    val choiceDomain = choices.map { dto ->
      com.qweld.app.domain.exam.Choice(
        id = dto.id,
        text = dto.text.toTextMap(resolvedLocale),
      )
    }
    val block = blockId ?: taskId.substringBefore("-", taskId)
    return Question(
      id = id,
      taskId = taskId,
      blockId = block,
      locale = resolvedLocale,
      stem = stemMap,
      familyId = familyId,
      choices = choiceDomain,
      correctChoiceId = correctId,
    )
  }

  private fun JsonElement.toTextMap(resolvedLocale: String): Map<String, String> {
    return when (this) {
      is JsonNull -> emptyMap()
      is JsonObject -> this.mapValues { (_, element) -> element.jsonPrimitive.content }
      is JsonPrimitive -> mapOf(resolvedLocale to this.content)
      else -> mapOf(resolvedLocale to this.toString())
    }
  }
}
