package com.qweld.app.feature.exam.vm

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.exam.AssembledQuestion
import com.qweld.app.domain.exam.AttemptSeed
import com.qweld.app.domain.exam.ExamAssembler
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.Question
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.errors.ExamAssemblyException
import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.model.DeficitDetailUiModel
import com.qweld.app.feature.exam.model.DeficitDialogUiModel
import com.qweld.app.feature.exam.model.ExamAttemptUiState
import com.qweld.app.feature.exam.model.ExamChoiceUiModel
import com.qweld.app.feature.exam.model.ExamQuestionUiModel
import com.qweld.app.feature.exam.model.ExamUiState
import com.qweld.app.feature.exam.vm.ResumeUseCase
import com.qweld.app.feature.exam.model.ResumeDialogUiModel
import com.qweld.app.feature.exam.model.ResumeLocaleOption
import com.qweld.app.feature.exam.vm.ResumeUseCase.MergeState
import java.time.Duration
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import kotlinx.coroutines.runBlocking

class ExamViewModel(
  private val repository: AssetQuestionRepository,
  private val attemptsRepository: AttemptsRepository,
  private val answersRepository: AnswersRepository,
  private val statsRepository: UserStatsRepository,
  private val blueprintProvider: (ExamMode, Int) -> ExamBlueprint = ::defaultBlueprintForMode,
  private val seedProvider: () -> Long = { Random.nextLong() },
  private val userIdProvider: () -> String = { DEFAULT_USER_ID },
  private val attemptIdProvider: () -> String = { UUID.randomUUID().toString() },
  private val nowProvider: () -> Long = { System.currentTimeMillis() },
  private val timerController: TimerController = TimerController { message -> Timber.i(message) },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val resumeUseCase: ResumeUseCase =
    ResumeUseCase(
      repository = repository,
      statsRepository = statsRepository,
      blueprintProvider = blueprintProvider,
      userIdProvider = userIdProvider,
      ioDispatcher = ioDispatcher,
    ),
) : ViewModel() {

  private val _uiState = mutableStateOf(ExamUiState())
  val uiState: State<ExamUiState> = _uiState

  private var currentAttempt: ExamAssemblerResult? = null
  private var latestTimerLabel: String? = null
  private var timerJob: Job? = null
  private var timerRunning: Boolean = false
  private var hasFinished: Boolean = false
  private var latestResult: ExamResultData? = null
  private var questionRationales: Map<String, String> = emptyMap()
  private val questionSeenAt = mutableMapOf<String, Long>()
  private var pendingResume: AttemptEntity? = null
  private var manualTimerRemaining: Duration? = null

  private val _events = MutableSharedFlow<ExamEvent>(extraBufferCapacity = 1)
  val events = _events.asSharedFlow()

  fun startAttempt(
    mode: ExamMode,
    locale: String,
    practiceSize: Int = DEFAULT_PRACTICE_SIZE,
  ): Boolean {
    val normalizedLocale = locale.lowercase(Locale.US)
    pendingResume = null
    manualTimerRemaining = null
    _uiState.value = _uiState.value.copy(resumeDialog = null)
    val blueprint = blueprintProvider(mode, practiceSize)
    val requestedTasks =
      blueprint.taskQuotas.mapNotNull { quota -> quota.taskId.takeIf { it.isNotBlank() } }.toSet()
    val result = repository.loadQuestions(normalizedLocale, tasks = requestedTasks)
    val questions = when (result) {
      is AssetQuestionRepository.Result.Success -> {
        questionRationales = result.questions.mapNotNull { dto ->
          val rationale = dto.rationales?.get(dto.correctId)?.takeIf { it.isNotBlank() }
          rationale?.let { dto.id to it }
        }.toMap()
        result.questions.map { it.toDomain(normalizedLocale) }
      }
      is AssetQuestionRepository.Result.Missing -> {
        questionRationales = emptyMap()
        Timber.w("[exam_start] cancelled reason=missing locale=%s", result.locale)
        return false
      }
      is AssetQuestionRepository.Result.Error -> {
        questionRationales = emptyMap()
        Timber.e(result.cause, "[exam_start] cancelled reason=error locale=%s", result.locale)
        return false
      }
    }

    val assembler = ExamAssembler(
      questionRepository = InMemoryQuestionRepository(questions),
      statsRepository = statsRepository,
      logger = { message -> Timber.i(message) },
    )
    val attemptSeed = AttemptSeed(seedProvider())
    return try {
        val attempt = runBlocking(ioDispatcher) {
            assembler.assemble(
                userId = userIdProvider(),
                mode = mode,
                locale = normalizedLocale,
                seed = attemptSeed,
                blueprint = blueprint,)
        }
      val attemptId = attemptIdProvider()
      val startedAt = nowProvider()
      Timber.i("[attempt_create] id=%s mode=%s locale=%s", attemptId, attempt.mode.name, attempt.locale)
      viewModelScope.launch(ioDispatcher) {
        val entity =
          AttemptEntity(
            id = attemptId,
            mode = attempt.mode.name,
            locale = attempt.locale.uppercase(Locale.US),
            seed = attempt.seed.value,
            questionCount = attempt.questions.size,
            startedAt = startedAt,
          )
        attemptsRepository.save(entity)
      }
      currentAttempt =
        ExamAssemblerResult(
          attempt = attempt,
          answers = emptyMap(),
          currentIndex = 0,
          attemptId = attemptId,
          startedAt = startedAt,
        )
      hasFinished = false
      latestResult = null
      questionSeenAt.clear()
      if (mode == ExamMode.IP_MOCK) {
        startTimer()
      } else {
        stopTimer(clearLabel = true)
      }
      refreshState()
      true
    } catch (deficit: ExamAssemblyException.Deficit) {
      questionRationales = emptyMap()
      val details = deficit.details.map { detail ->
        Timber.w(
          "[deficit_dialog] shown=true taskId=%s need=%d have=%d missing=%d",
          detail.taskId,
          detail.need,
          detail.have,
          detail.missing,
        )
        DeficitDetailUiModel(
          taskId = detail.taskId,
          need = detail.need,
          have = detail.have,
          missing = detail.missing,
        )
      }
      currentAttempt = null
      _uiState.value = ExamUiState(
        isLoading = false,
        attempt = null,
        deficitDialog = DeficitDialogUiModel(details),
        errorMessage = null,
      )
      false
    }
  }

  fun detectResume(deviceLocale: String) {
    viewModelScope.launch {
      val unfinished = withContext(ioDispatcher) { attemptsRepository.getUnfinished() }
      if (unfinished == null) {
        pendingResume = null
        _uiState.value = _uiState.value.copy(resumeDialog = null)
        return@launch
      }
      if (currentAttempt?.attemptId == unfinished.id) {
        pendingResume = null
        _uiState.value = _uiState.value.copy(resumeDialog = null)
        return@launch
      }
      val mode = runCatching { ExamMode.valueOf(unfinished.mode) }.getOrNull()
      if (mode == null) {
        Timber.w(
          "[resume_detect] ignored attemptId=%s reason=unknown_mode mode=%s",
          unfinished.id,
          unfinished.mode,
        )
        return@launch
      }
      val elapsedSec =
        ((nowProvider() - unfinished.startedAt).coerceAtLeast(0L) / 1_000L).toInt()
      Timber.i(
        "[resume_detect] attemptId=%s mode=%s locale=%s elapsedSec=%d",
        unfinished.id,
        mode.name,
        unfinished.locale,
        elapsedSec,
      )
      val nowMillis = nowProvider()
      val remaining = resumeUseCase.remainingTime(unfinished, mode, nowMillis)
      if (mode == ExamMode.IP_MOCK && remaining.isZero) {
        withContext(ioDispatcher) { finalizeExpiredAttempt(unfinished, mode) }
        pendingResume = null
        _uiState.value = _uiState.value.copy(resumeDialog = null)
        return@launch
      }
      val attemptLocale = unfinished.locale.lowercase(Locale.US)
      val deviceNormalized = deviceLocale.lowercase(Locale.US)
      val mismatch = !attemptLocale.equals(deviceNormalized, ignoreCase = true)
      if (mismatch) {
        Timber.w("[resume_mismatch] reason=locale_changed attemptId=%s", unfinished.id)
      }
      pendingResume = unfinished
      _uiState.value =
        _uiState.value.copy(
          resumeDialog =
            ResumeDialogUiModel(
              attemptId = unfinished.id,
              mode = mode,
              attemptLocale = attemptLocale,
              deviceLocale = deviceNormalized,
              questionCount = unfinished.questionCount,
              showLocaleMismatch = mismatch,
              remaining = if (mode == ExamMode.IP_MOCK) remaining else null,
            ),
        )
    }
  }

  fun resumeAttempt(
    attemptId: String,
    localeOption: ResumeLocaleOption,
    deviceLocale: String,
  ) {
    val pending = pendingResume?.takeIf { it.id == attemptId } ?: return
    val mode = runCatching { ExamMode.valueOf(pending.mode) }.getOrNull() ?: return
    val targetLocale =
      when (localeOption) {
        ResumeLocaleOption.KEEP_ORIGINAL -> pending.locale.lowercase(Locale.US)
        ResumeLocaleOption.SWITCH_TO_DEVICE -> deviceLocale.lowercase(Locale.US)
      }
    viewModelScope.launch {
      try {
        val remaining = resumeUseCase.remainingTime(pending, mode, nowProvider())
        if (mode == ExamMode.IP_MOCK && remaining.isZero) {
          withContext(ioDispatcher) { finalizeExpiredAttempt(pending, mode) }
          pendingResume = null
          _uiState.value = _uiState.value.copy(resumeDialog = null)
          return@launch
        }
        val answers = withContext(ioDispatcher) { answersRepository.listByAttempt(attemptId) }
        val reconstruction =
          withContext(ioDispatcher) {
            resumeUseCase.reconstructAttempt(
              mode = mode,
              seed = pending.seed,
              locale = targetLocale,
              questionCount = pending.questionCount,
            )
          }
        val merged: MergeState = resumeUseCase.mergeAnswers(reconstruction.attempt.questions, answers)
        questionRationales = reconstruction.rationales
        questionSeenAt.clear()
        answers.forEach { answer -> questionSeenAt[answer.questionId] = answer.seenAt }
        currentAttempt =
          ExamAssemblerResult(
            attempt = reconstruction.attempt,
            answers = merged.answers,
            currentIndex = merged.currentIndex,
            attemptId = pending.id,
            startedAt = pending.startedAt,
          )
        hasFinished = false
        latestResult = null
        pendingResume = null
        manualTimerRemaining = null
        _uiState.value = _uiState.value.copy(resumeDialog = null, errorMessage = null)
        if (mode == ExamMode.IP_MOCK) {
          resumeTimer(remaining)
        } else {
          stopTimer(clearLabel = true)
        }
        Timber.i(
          "[resume_continue] attemptId=%s remaining=%d questions=%d",
          pending.id,
          remaining.seconds,
          reconstruction.attempt.questions.size,
        )
        refreshState()
        _events.tryEmit(ExamEvent.ResumeReady)
      } catch (error: Exception) {
        Timber.e(error, "[resume_continue] failed attemptId=%s", attemptId)
        pendingResume = null
        _uiState.value =
          _uiState.value.copy(
            resumeDialog = null,
            errorMessage = error.message ?: "Unable to resume attempt.",
          )
      }
    }
  }

  fun discardAttempt(attemptId: String) {
    val pending = pendingResume?.takeIf { it.id == attemptId } ?: return
    Timber.i("[resume_discard] attemptId=%s", attemptId)
    pendingResume = null
    _uiState.value = _uiState.value.copy(resumeDialog = null)
    viewModelScope.launch(ioDispatcher) {
      val now = nowProvider()
      val durationSec = ((now - pending.startedAt).coerceAtLeast(0L) / 1_000L).toInt()
      attemptsRepository.markFinished(
        attemptId = attemptId,
        finishedAt = now,
        durationSec = durationSec,
        passThreshold = null,
        scorePct = null,
      )
    }
  }

  fun submitAnswer(choiceId: String) {
    val attemptResult = currentAttempt ?: return
    val assembledQuestion = attemptResult.attempt.questions.getOrNull(attemptResult.currentIndex) ?: return
    val questionId = assembledQuestion.question.id
    if (attemptResult.answers.containsKey(questionId)) return

    val answeredAt = nowProvider()
    val seenAt = questionSeenAt.getOrPut(questionId) { attemptResult.startedAt }
    val durationMillis = (answeredAt - seenAt).coerceAtLeast(0L)
    val timeSpentSec = (durationMillis / 1_000L).toInt()
    val displayIndex =
      attemptResult.attempt.questions.indexOfFirst { it.question.id == questionId }.takeIf { it >= 0 }
        ?: attemptResult.currentIndex
    val correct = assembledQuestion.question.correctChoiceId == choiceId

    viewModelScope.launch(ioDispatcher) {
      val entity =
        AnswerEntity(
          attemptId = attemptResult.attemptId,
          displayIndex = displayIndex,
          questionId = questionId,
          selectedId = choiceId,
          correctId = assembledQuestion.question.correctChoiceId,
          isCorrect = correct,
          timeSpentSec = timeSpentSec,
          seenAt = seenAt,
          answeredAt = answeredAt,
        )
      answersRepository.saveAll(listOf(entity))
    }
    Timber.i("[answer_write] attempt=%s qId=%s correct=%s", attemptResult.attemptId, questionId, correct)

    val updatedAnswers = attemptResult.answers + (questionId to choiceId)
    currentAttempt = attemptResult.copy(answers = updatedAnswers)
    refreshState()
  }

  fun finishExam() {
    val attemptResult = currentAttempt ?: return
    if (hasFinished) return
    val attempt = attemptResult.attempt
    val remaining =
      if (attempt.mode == ExamMode.IP_MOCK) {
        manualTimerRemaining ?: timerController.remaining()
      } else {
        Duration.ZERO
      }
    val correct = attempt.questions.count { question ->
      val answer = attemptResult.answers[question.question.id]
      answer != null && question.question.correctChoiceId == answer
    }
    val total = attempt.questions.size
    val scorePercent = if (total == 0) 0.0 else (correct.toDouble() / total.toDouble()) * 100.0
    val timeLeftLabel = if (attempt.mode == ExamMode.IP_MOCK) TimerController.formatDuration(remaining) else null
    Timber.i(
      "[exam_finish] correct=%d/%d score=%.2f%% timeLeft=%s",
      correct,
      total,
      scorePercent,
      timeLeftLabel ?: "-",
    )
    stopTimer(clearLabel = false)
    hasFinished = true
    val finishedAt = nowProvider()
    val durationSec = ((finishedAt - attemptResult.startedAt).coerceAtLeast(0L) / 1_000L).toInt()
    val passThreshold = if (attempt.mode == ExamMode.IP_MOCK) IP_MOCK_PASS_THRESHOLD else null
    Timber.i("[attempt_finish] id=%s scorePct=%.2f", attemptResult.attemptId, scorePercent)
    viewModelScope.launch(ioDispatcher) {
      attemptsRepository.markFinished(
        attemptId = attemptResult.attemptId,
        finishedAt = finishedAt,
        durationSec = durationSec,
        passThreshold = passThreshold,
        scorePct = scorePercent,
      )
    }
    latestResult = ExamResultData(
      attemptId = attemptResult.attemptId,
      attempt = attempt,
      answers = attemptResult.answers,
      remaining = if (attempt.mode == ExamMode.IP_MOCK) remaining else null,
      rationales = questionRationales,
      scorePercent = scorePercent,
      passThreshold = passThreshold,
    )
    _events.tryEmit(ExamEvent.NavigateToResult)
  }

  fun nextQuestion() {
    val attemptResult = currentAttempt ?: return
    val attempt = attemptResult.attempt
    val nextIndex = (attemptResult.currentIndex + 1).coerceAtMost(attempt.questions.lastIndex)
    if (nextIndex == attemptResult.currentIndex) return
    currentAttempt = attemptResult.copy(currentIndex = nextIndex)
    refreshState()
  }

  fun previousQuestion() {
    val attemptResult = currentAttempt ?: return
    if (attemptResult.attempt.mode == ExamMode.IP_MOCK) return
    val prevIndex = (attemptResult.currentIndex - 1).coerceAtLeast(0)
    if (prevIndex == attemptResult.currentIndex) return
    currentAttempt = attemptResult.copy(currentIndex = prevIndex)
    refreshState()
  }

  fun dismissDeficitDialog() {
    _uiState.value = _uiState.value.copy(deficitDialog = null)
  }

  private fun refreshState() {
    val attemptResult = currentAttempt
    if (attemptResult == null) {
      latestTimerLabel = null
      _uiState.value = ExamUiState()
      return
    }
    val attempt = attemptResult.attempt
    val uiQuestions = attempt.questions.map { assembled ->
      toUiModel(
        assembled = assembled,
        selectedChoiceId = attemptResult.answers[assembled.question.id],
        locale = attempt.locale,
      )
    }
    val index = attemptResult.currentIndex.coerceIn(0, uiQuestions.lastIndex.coerceAtLeast(0))
    markQuestionSeen(attemptResult, index)
    val attemptState =
      ExamAttemptUiState(
        attemptId = attemptResult.attemptId,
        mode = attempt.mode,
        locale = attempt.locale,
        questions = uiQuestions,
        currentIndex = index,
      )
    _uiState.value = ExamUiState(
      isLoading = false,
      attempt = attemptState,
      deficitDialog = null,
      errorMessage = null,
      timerLabel = latestTimerLabel,
      resumeDialog = null,
    )
    currentAttempt = attemptResult.copy(currentIndex = index)
  }

  private fun toUiModel(
    assembled: AssembledQuestion,
    selectedChoiceId: String?,
    locale: String,
  ): ExamQuestionUiModel {
    val stem = assembled.question.stem[locale] ?: assembled.question.stem.values.firstOrNull().orEmpty()
    val choices = assembled.choices.mapIndexed { index, choice ->
      val label = ('A'.code + index).toChar().toString()
      val text = choice.text[locale] ?: choice.text.values.firstOrNull().orEmpty()
      ExamChoiceUiModel(
        id = choice.id,
        label = label,
        text = text,
        isSelected = choice.id == selectedChoiceId,
      )
    }
    return ExamQuestionUiModel(
      id = assembled.question.id,
      stem = stem,
      choices = choices,
      selectedChoiceId = selectedChoiceId,
    )
  }

  private fun markQuestionSeen(attemptResult: ExamAssemblerResult, index: Int) {
    val questionId = attemptResult.attempt.questions.getOrNull(index)?.question?.id ?: return
    questionSeenAt.putIfAbsent(questionId, nowProvider())
  }

  fun requireLatestResult(): ExamResultData {
    return checkNotNull(latestResult) { "Result is not available" }
  }

  private fun startTimer() {
    stopTimer(clearLabel = true)
    manualTimerRemaining = null
    timerController.start()
    timerRunning = true
    val initialRemaining = TimerController.EXAM_DURATION
    latestTimerLabel = TimerController.formatDuration(initialRemaining)
    Timber.i(
      "[exam_timer] started=4h remaining=%s",
      latestTimerLabel,
    )
    _uiState.value = _uiState.value.copy(timerLabel = latestTimerLabel)
    timerJob = viewModelScope.launch {
      while (true) {
        delay(1_000)
        val remaining = timerController.remaining()
        latestTimerLabel = TimerController.formatDuration(remaining)
        _uiState.value = _uiState.value.copy(timerLabel = latestTimerLabel)
        if (remaining.isZero) {
          finishExam()
          break
        }
      }
    }
  }

  private fun resumeTimer(initialRemaining: Duration) {
    stopTimer(clearLabel = true)
    manualTimerRemaining = initialRemaining
    timerRunning = true
    latestTimerLabel = TimerController.formatDuration(initialRemaining)
    Timber.i(
      "[exam_timer] resumed remaining=%s",
      latestTimerLabel,
    )
    _uiState.value = _uiState.value.copy(timerLabel = latestTimerLabel)
    timerJob = viewModelScope.launch {
      var remaining = initialRemaining
      while (true) {
        delay(1_000)
        remaining = remaining.minusSeconds(1)
        if (remaining.isNegative) {
          remaining = Duration.ZERO
        }
        manualTimerRemaining = remaining
        latestTimerLabel = TimerController.formatDuration(remaining)
        _uiState.value = _uiState.value.copy(timerLabel = latestTimerLabel)
        if (remaining.isZero) {
          finishExam()
          break
        }
      }
    }
  }

  private fun stopTimer(clearLabel: Boolean) {
    timerJob?.cancel()
    timerJob = null
    if (timerRunning) {
      timerController.stop()
      timerRunning = false
    }
    manualTimerRemaining = null
    if (clearLabel) {
      latestTimerLabel = null
      _uiState.value = _uiState.value.copy(timerLabel = null)
    } else if (latestTimerLabel != null) {
      latestTimerLabel = null
      _uiState.value = _uiState.value.copy(timerLabel = null)
    }
  }

  private suspend fun finalizeExpiredAttempt(
    attempt: AttemptEntity,
    mode: ExamMode,
  ) {
    val answers = answersRepository.listByAttempt(attempt.id)
    val reconstruction =
      resumeUseCase.reconstructAttempt(
        mode = mode,
        seed = attempt.seed,
        locale = attempt.locale.lowercase(Locale.US),
        questionCount = attempt.questionCount,
      )
    val merged = resumeUseCase.mergeAnswers(reconstruction.attempt.questions, answers)
    val correct =
      reconstruction.attempt.questions.count { assembled ->
        val selected = merged.answers[assembled.question.id]
        selected != null && selected == assembled.question.correctChoiceId
      }
    val total = reconstruction.attempt.questions.size
    val scorePercent = if (total == 0) 0.0 else (correct.toDouble() / total.toDouble()) * 100.0
    val finishedAt = nowProvider()
    val durationSec = TimerController.EXAM_DURATION.seconds.toInt()
    val passThreshold = if (mode == ExamMode.IP_MOCK) IP_MOCK_PASS_THRESHOLD else null
    attemptsRepository.markFinished(
      attemptId = attempt.id,
      finishedAt = finishedAt,
      durationSec = durationSec,
      passThreshold = passThreshold,
      scorePct = scorePercent,
    )
    Timber.i(
      "[resume_timeout] attemptId=%s correct=%d/%d score=%.2f",
      attempt.id,
      correct,
      total,
      scorePercent,
    )
  }

  sealed interface ExamEvent {
    data object NavigateToResult : ExamEvent
    data object ResumeReady : ExamEvent
  }

  data class ExamResultData(
    val attemptId: String,
    val attempt: com.qweld.app.domain.exam.ExamAttempt,
    val answers: Map<String, String>,
    val remaining: Duration?,
    val rationales: Map<String, String>,
    val scorePercent: Double,
    val passThreshold: Int?,
    val flaggedQuestionIds: Set<String> = emptySet(),
  )

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

  private data class ExamAssemblerResult(
    val attempt: com.qweld.app.domain.exam.ExamAttempt,
    val answers: Map<String, String>,
    val currentIndex: Int,
    val attemptId: String,
    val startedAt: Long,
  )

  private class InMemoryQuestionRepository(
    private val questions: List<Question>,
  ) : QuestionRepository {
    override fun listByTaskAndLocale(
      taskId: String,
      locale: String,
      allowFallbackToEnglish: Boolean,
    ): List<Question> {
      val normalizedLocale = locale.lowercase(Locale.US)
      val primary = questions.filter { it.taskId == taskId && it.locale.equals(normalizedLocale, ignoreCase = true) }
      if (primary.isNotEmpty() || !allowFallbackToEnglish) {
        return primary
      }
      return questions.filter { it.taskId == taskId && it.locale.equals("en", ignoreCase = true) }
    }
  }

  companion object {
    private const val DEFAULT_PRACTICE_SIZE = 20
    private const val DEFAULT_USER_ID = "local_user"
    private const val IP_MOCK_PASS_THRESHOLD = 70

    private fun defaultBlueprintForMode(mode: ExamMode, practiceSize: Int): ExamBlueprint {
      return when (mode) {
        ExamMode.PRACTICE -> buildPracticeBlueprint(practiceSize)
        else -> ExamBlueprint.default()
      }
    }

    private fun buildPracticeBlueprint(practiceSize: Int): ExamBlueprint {
      val sanitizedSize = practiceSize.coerceAtLeast(1)
      val base = ExamBlueprint.default()
      if (sanitizedSize >= base.totalQuestions) return base
      var remaining = sanitizedSize
      val quotas = mutableListOf<TaskQuota>()
      for (quota in base.taskQuotas) {
        if (remaining <= 0) break
        val take = min(remaining, quota.required)
        if (take > 0) {
          quotas += TaskQuota(quota.taskId, quota.blockId, take)
          remaining -= take
        }
      }
      if (remaining > 0) {
        val last = base.taskQuotas.last()
        quotas += TaskQuota(last.taskId, last.blockId, remaining)
      }
      return ExamBlueprint(totalQuestions = sanitizedSize, taskQuotas = quotas)
    }
  }
}

class ExamViewModelFactory(
  private val repository: AssetQuestionRepository,
  private val attemptsRepository: AttemptsRepository,
  private val answersRepository: AnswersRepository,
  private val statsRepository: UserStatsRepository,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(ExamViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return ExamViewModel(
        repository = repository,
        attemptsRepository = attemptsRepository,
        answersRepository = answersRepository,
        statsRepository = statsRepository,
      ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
  }
}
