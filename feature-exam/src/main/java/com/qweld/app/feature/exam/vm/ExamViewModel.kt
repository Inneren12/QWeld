package com.qweld.app.feature.exam.vm

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.exam.AssembledQuestion
import com.qweld.app.domain.exam.AttemptSeed
import com.qweld.app.domain.exam.ExamAssembler
import com.qweld.app.domain.exam.ExamAssemblyConfig
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.Question
import com.qweld.app.domain.exam.QuotaDistributor
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
import com.qweld.app.feature.exam.vm.Distribution
import com.qweld.app.feature.exam.vm.PracticeScope
import com.qweld.app.feature.exam.vm.ResumeUseCase
import com.qweld.app.feature.exam.model.ResumeDialogUiModel
import com.qweld.app.feature.exam.model.ResumeLocaleOption
import com.qweld.app.feature.exam.vm.ResumeUseCase.MergeState
import java.time.Duration
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
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
  private val userPrefs: UserPrefsDataStore,
  private val blueprintProvider: (ExamMode, Int) -> ExamBlueprint = ::defaultBlueprintForMode,
  private val seedProvider: () -> Long = { Random.nextLong() },
  private val userIdProvider: () -> String = { DEFAULT_USER_ID },
  private val attemptIdProvider: () -> String = { UUID.randomUUID().toString() },
  private val nowProvider: () -> Long = { System.currentTimeMillis() },
  private val timerController: TimerController = TimerController { message -> Timber.i(message) },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val prewarmUseCase: PrewarmUseCase = PrewarmUseCase(repository, ioDispatcher),
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
  private var autosaveController: AutosaveController? = null
  private var autosaveTickerJob: Job? = null
  private var pendingResume: AttemptEntity? = null
  private var manualTimerRemaining: Duration? = null
  private var prewarmJob: Job? = null

  private val _events = MutableSharedFlow<ExamEvent>(extraBufferCapacity = 1)
  val events = _events.asSharedFlow()

  private val _effects = MutableSharedFlow<ExamEffect>(extraBufferCapacity = 1)
  val effects: Flow<ExamEffect> = _effects.asSharedFlow()

  val prewarmDisabled: Boolean = false

  val lastPracticeScope = userPrefs.readLastPracticeScope()

  fun startAttempt(
    mode: ExamMode,
    locale: String,
    practiceConfig: PracticeConfig = PracticeConfig(),
    blueprintOverride: ExamBlueprint? = null,
  ): Boolean {
    val normalizedPracticeConfig =
      if (mode == ExamMode.PRACTICE) {
        practiceConfig.copy(size = PracticeConfig.sanitizeSize(practiceConfig.size))
      } else {
        practiceConfig
      }
    if (mode == ExamMode.IP_MOCK) {
      Timber.i("[exam_start_req] mode=IPMock locale=%s", locale)
    }
    val normalizedLocale = locale.lowercase(Locale.US)
    pendingResume = null
    manualTimerRemaining = null
    _uiState.value = _uiState.value.copy(resumeDialog = null)
    val resolvedPracticeSize =
      if (mode == ExamMode.PRACTICE && blueprintOverride == null) {
        normalizedPracticeConfig.size
      } else {
        DEFAULT_PRACTICE_SIZE
      }
    val blueprint = blueprintOverride ?: blueprintProvider(mode, resolvedPracticeSize)
    if (mode == ExamMode.PRACTICE) {
      Timber.i(
        "[practice_config] size=%d wrongBiased=%s",
        blueprint.totalQuestions,
        normalizedPracticeConfig.wrongBiased,
      )
    }
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
      config =
        ExamAssemblyConfig(
          practiceWrongBiased =
            mode == ExamMode.PRACTICE && normalizedPracticeConfig.wrongBiased,
        ),
      logger = { message -> Timber.i(message) },
    )
    val attemptSeed = AttemptSeed(seedProvider())
    return try {
      val attempt =
        runBlocking(ioDispatcher) {
          assembler.assemble(
            userId = userIdProvider(),
            mode = mode,
            locale = normalizedLocale,
            seed = attemptSeed,
            blueprint = blueprint,
          )
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
      prepareAutosave(attemptId)
      latestResult = null
      questionSeenAt.clear()
      if (mode == ExamMode.IP_MOCK) {
        startTimer()
      } else {
        stopTimer(clearLabel = true)
      }
      refreshState()
      if (mode == ExamMode.IP_MOCK) {
        Timber.i("[exam_start] ready=125")
        _effects.tryEmit(ExamEffect.NavigateToExam)
      }
      true
    } catch (deficit: ExamAssemblyException.Deficit) {
      questionRationales = emptyMap()
      val details = deficit.details.map { detail ->
        val message =
          "taskId=${detail.taskId} need=${detail.need} have=${detail.have} missing=${detail.missing}"
        Timber.w("[deficit] %s", message)
        DeficitDetailUiModel(
          taskId = detail.taskId,
          need = detail.need,
          have = detail.have,
          missing = detail.missing,
        )
      }
      if (mode == ExamMode.IP_MOCK) {
        val detailSummary =
          details.joinToString(separator = "\n") { detail ->
            "taskId=${detail.taskId} need=${detail.need} have=${detail.have} missing=${detail.missing}"
          }
        _effects.tryEmit(ExamEffect.ShowDeficit(detailSummary))
      }
      currentAttempt = null
      _uiState.value = ExamUiState(
        isLoading = false,
        attempt = null,
        deficitDialog = DeficitDialogUiModel(details),
        errorMessage = null,
        prewarmState = _uiState.value.prewarmState,
      )
      false
    } catch (error: Throwable) {
      if (error is CancellationException) throw error
      if (mode == ExamMode.IP_MOCK) {
        Timber.e(error, "[exam_error]")
        _effects.tryEmit(ExamEffect.ShowError(error.message ?: "unknown"))
      } else {
        Timber.e(error)
      }
      currentAttempt = null
      _uiState.value =
        ExamUiState(
          isLoading = false,
          attempt = null,
          deficitDialog = null,
          errorMessage = error.message ?: "Unable to start attempt.",
          prewarmState = _uiState.value.prewarmState,
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
        prepareAutosave(pending.id)
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

    val answerEntity =
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
    val controller = autosaveController
    if (controller != null) {
      controller.onAnswer(
        questionId = answerEntity.questionId,
        choiceId = answerEntity.selectedId,
        correctChoiceId = answerEntity.correctId,
        isCorrect = answerEntity.isCorrect,
        displayIndex = answerEntity.displayIndex,
        timeSpentSec = answerEntity.timeSpentSec,
        seenAt = answerEntity.seenAt,
        answeredAt = answerEntity.answeredAt,
      )
    } else {
      viewModelScope.launch(ioDispatcher) { answersRepository.upsert(listOf(answerEntity)) }
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
    autosaveController?.flush(force = true)
    stopAutosaveTicker()
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
      _uiState.value = ExamUiState(prewarmState = _uiState.value.prewarmState)
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
    _uiState.value =
      ExamUiState(
        isLoading = false,
        attempt = attemptState,
        deficitDialog = null,
        errorMessage = null,
        timerLabel = latestTimerLabel,
        resumeDialog = null,
        prewarmState = _uiState.value.prewarmState,
      )
    currentAttempt = attemptResult.copy(currentIndex = index)
  }

  fun startPrewarmForIpMock(locale: String) {
    if (prewarmDisabled) return
    val normalizedLocale = locale.lowercase(Locale.US)
    val current = _uiState.value.prewarmState
    if (current.isRunning) return
    if (current.isReady && current.locale.equals(normalizedLocale, ignoreCase = true)) return

    val blueprint = blueprintProvider(ExamMode.IP_MOCK, DEFAULT_PRACTICE_SIZE)
    val tasks = blueprint.taskQuotas.mapNotNull { quota -> quota.taskId.takeIf { it.isNotBlank() } }.toSet()
    val expectedTotal = tasks.size.takeIf { it > 0 } ?: 1
    updatePrewarmState(
      locale = normalizedLocale,
      loaded = 0,
      total = expectedTotal,
      isRunning = true,
      isReady = false,
    )

    prewarmJob?.cancel()
    prewarmJob =
      viewModelScope.launch(ioDispatcher) {
        try {
          prewarmUseCase.prewarm(normalizedLocale, tasks) { loaded, total ->
            dispatchPrewarmProgress(normalizedLocale, loaded, total, expectedTotal)
          }
          finalizePrewarm(normalizedLocale, expectedTotal)
        } catch (cancellation: CancellationException) {
          throw cancellation
        } catch (error: Throwable) {
          Timber.e(error, "[prewarm_flow_error] locale=%s", normalizedLocale)
          finalizePrewarm(normalizedLocale, expectedTotal)
        }
      }
  }

  private fun dispatchPrewarmProgress(
    locale: String,
    loaded: Int,
    total: Int,
    expectedTotal: Int,
  ) {
    val safeTotal = when {
      total > 0 -> total
      expectedTotal > 0 -> expectedTotal
      else -> 1
    }
    val clampedLoaded = loaded.coerceIn(0, safeTotal)
    val isReady = clampedLoaded >= safeTotal && safeTotal > 0
    updatePrewarmState(
      locale = locale,
      loaded = clampedLoaded,
      total = safeTotal,
      isRunning = !isReady,
      isReady = isReady,
    )
  }

  private fun finalizePrewarm(locale: String, expectedTotal: Int) {
    val current = _uiState.value.prewarmState
    updatePrewarmState(
      locale = locale,
      loaded = max(current.loaded, current.total.takeIf { it > 0 } ?: expectedTotal),
      total = current.total.takeIf { it > 0 } ?: expectedTotal,
      isRunning = false,
      isReady = true,
    )
  }

  private fun updatePrewarmState(
    locale: String,
    loaded: Int,
    total: Int,
    isRunning: Boolean,
    isReady: Boolean,
  ) {
    viewModelScope.launch {
      val previous = _uiState.value.prewarmState
      val clampedTotal = total.coerceAtLeast(0)
      val resolvedTotal =
        when {
          clampedTotal > 0 -> clampedTotal
          previous.total > 0 -> previous.total
          else -> max(loaded, 1)
        }
      val clampedLoaded = loaded.coerceIn(0, resolvedTotal)
      val readyState = isReady || (resolvedTotal > 0 && clampedLoaded >= resolvedTotal)
      _uiState.value =
        _uiState.value.copy(
          prewarmState =
            previous.copy(
              locale = locale,
              loaded = clampedLoaded,
              total = resolvedTotal,
              isRunning = isRunning && !readyState,
              isReady = readyState,
            ),
        )
    }
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

  sealed class ExamEffect {
    data object NavigateToExam : ExamEffect()
    data class ShowDeficit(val detail: String) : ExamEffect()
    data class ShowError(val msg: String) : ExamEffect()
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

  fun practiceBlueprint(): ExamBlueprint {
    return blueprintProvider(ExamMode.IP_MOCK, DEFAULT_PRACTICE_SIZE)
  }

  fun startPractice(
    locale: String,
    config: PracticeConfig,
    preset: PracticeScopePresetName? = null,
  ): Boolean {
    val resolvedConfig = config.copy(size = PracticeConfig.sanitizeSize(config.size))
    val baseBlueprint = practiceBlueprint()
    val selectedTasks = resolvePracticeTasks(baseBlueprint, resolvedConfig.scope)
    val scopeBlocksLog =
      resolvedConfig.scope.blocks
        .map { it.uppercase(Locale.US) }
        .sorted()
        .joinToString(separator = ",", prefix = "[", postfix = "]")
    val scopeTasksLog =
      resolvedConfig.scope.taskIds
        .map { it.uppercase(Locale.US) }
        .sorted()
        .joinToString(separator = ",", prefix = "[", postfix = "]")
    val resolvedTasksLog =
      selectedTasks
        .map { it.uppercase(Locale.US) }
        .sorted()
        .joinToString(separator = ",", prefix = "[", postfix = "]")
    if (selectedTasks.isEmpty()) {
      Timber.w(
        "[practice_scope] blocks=%s tasks=%s size=%d dist=%s wrongBiased=%s | reason=empty",
        scopeBlocksLog,
        resolvedTasksLog,
        resolvedConfig.size,
        resolvedConfig.scope.distribution.name,
        resolvedConfig.wrongBiased,
      )
      return false
    }

    if (preset != null) {
      if (preset == PracticeScopePresetName.LAST_USED) {
        Timber.i(
          "[practice_preset] name=%s blocks=%s tasks=%s dist=%s",
          preset.logName,
          scopeBlocksLog,
          scopeTasksLog,
          resolvedConfig.scope.distribution.name,
        )
      } else {
        Timber.i(
          "[practice_preset] name=%s blocks=%s tasks=%s",
          preset.logName,
          scopeBlocksLog,
          scopeTasksLog,
        )
      }
    }

    val quotas = resolvePracticeQuotas(baseBlueprint, resolvedConfig.scope, resolvedConfig.size)
    if (quotas.isEmpty()) {
      Timber.w("[practice_quota] total=%d reason=empty", resolvedConfig.size)
      return false
    }

    Timber.i(
      "[practice_scope] blocks=%s tasks=%s size=%d dist=%s wrongBiased=%s",
      scopeBlocksLog,
      resolvedTasksLog,
      resolvedConfig.size,
      resolvedConfig.scope.distribution.name,
      resolvedConfig.wrongBiased,
    )

    val quotaLog = quotas.entries.joinToString(separator = " ") { "${it.key}=${it.value}" }
    val total = quotas.values.sum()
    Timber.i("[practice_quota] %s total=%d", quotaLog, total)

    if (total <= 0) {
      return false
    }

    val blockByTask = baseBlueprint.taskQuotas.associate { it.taskId to it.blockId }
    val taskQuotas =
      quotas.mapNotNull { (taskId, required) ->
        val blockId = blockByTask[taskId]
        blockId?.let { TaskQuota(taskId = taskId, blockId = it, required = required) }
      }
    if (taskQuotas.isEmpty()) {
      return false
    }

    val blueprint = ExamBlueprint(totalQuestions = total, taskQuotas = taskQuotas)
    val launched =
      startAttempt(
        mode = ExamMode.PRACTICE,
        locale = locale,
        practiceConfig = resolvedConfig,
        blueprintOverride = blueprint,
      )
    if (launched) {
      viewModelScope.launch(ioDispatcher) {
        userPrefs.saveLastPracticeScope(
          blocks = resolvedConfig.scope.blocks,
          tasks = resolvedConfig.scope.taskIds,
          distribution = resolvedConfig.scope.distribution.name,
        )
      }
      Timber.i(
        "[practice_scope_saved] blocks=%s tasks=%s dist=%s",
        scopeBlocksLog,
        scopeTasksLog,
        resolvedConfig.scope.distribution.name,
      )
      _effects.tryEmit(ExamEffect.NavigateToExam)
    }
    return launched
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


  fun autosaveFlush(force: Boolean = true) {
    autosaveController?.flush(force)
  }

  private fun prepareAutosave(attemptId: String) {
    autosaveController?.flush(force = true)
    stopAutosaveTicker()
    val controller =
      AutosaveController(
        attemptId = attemptId,
        answersRepository = answersRepository,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
      )
    controller.configure(AUTOSAVE_INTERVAL_SEC)
    autosaveController = controller
    startAutosaveTicker()
  }

  private fun startAutosaveTicker() {
    stopAutosaveTicker()
    val controller = autosaveController ?: return
    autosaveTickerJob =
      viewModelScope.launch {
        while (isActive) {
          delay(controller.intervalMillis)
          controller.onTick()
        }
      }
  }

  private fun stopAutosaveTicker() {
    autosaveTickerJob?.cancel()
    autosaveTickerJob = null
  }

  override fun onCleared() {
    autosaveController?.flush(force = true)
    stopAutosaveTicker()
    super.onCleared()
  }

  companion object {
    private const val DEFAULT_PRACTICE_SIZE = PracticeConfig.DEFAULT_SIZE
    private const val DEFAULT_USER_ID = "local_user"
    private const val IP_MOCK_PASS_THRESHOLD = 70
    private const val AUTOSAVE_INTERVAL_SEC = 10

    @VisibleForTesting
    internal fun resolvePracticeTasks(
      blueprint: ExamBlueprint,
      scope: PracticeScope,
    ): LinkedHashSet<String> {
      val ordered = LinkedHashSet<String>()
      val normalizedTasks = scope.taskIds.map { it.uppercase(Locale.US) }.toSet()
      if (normalizedTasks.isNotEmpty()) {
        for (quota in blueprint.taskQuotas) {
          val taskId = quota.taskId
          if (normalizedTasks.contains(taskId.uppercase(Locale.US))) {
            ordered += taskId
          }
        }
        return ordered
      }

      val normalizedBlocks = scope.blocks.map { it.uppercase(Locale.US) }.toSet()
      if (normalizedBlocks.isEmpty()) {
        return ordered
      }
      for (quota in blueprint.taskQuotas) {
        if (normalizedBlocks.contains(quota.blockId.uppercase(Locale.US))) {
          ordered += quota.taskId
        }
      }
      return ordered
    }

    @VisibleForTesting
    internal fun resolvePracticeQuotas(
      blueprint: ExamBlueprint,
      scope: PracticeScope,
      total: Int,
    ): Map<String, Int> {
      val sanitizedTotal = total.coerceAtLeast(0)
      if (sanitizedTotal == 0) return emptyMap()
      val selected = resolvePracticeTasks(blueprint, scope)
      if (selected.isEmpty()) return emptyMap()
      val blueprintQuotas = blueprint.taskQuotas.associate { it.taskId to it.required }
      val chosen = LinkedHashSet<String>()
      chosen.addAll(selected)
      val raw =
        when (scope.distribution) {
          Distribution.Proportional ->
            QuotaDistributor.proportional(blueprintQuotas, chosen, sanitizedTotal)
          Distribution.Even -> QuotaDistributor.even(chosen, sanitizedTotal)
        }
      if (raw.isEmpty()) return emptyMap()
      val ordered = LinkedHashMap<String, Int>()
      for (taskId in selected) {
        val value = raw[taskId] ?: 0
        if (value > 0) {
          ordered[taskId] = value
        }
      }
      return ordered
    }

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
  private val userPrefs: UserPrefsDataStore,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(ExamViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return ExamViewModel(
        repository = repository,
        attemptsRepository = attemptsRepository,
        answersRepository = answersRepository,
        statsRepository = statsRepository,
        userPrefs = userPrefs,
        prewarmUseCase = PrewarmUseCase(repository),
      ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
  }
}
