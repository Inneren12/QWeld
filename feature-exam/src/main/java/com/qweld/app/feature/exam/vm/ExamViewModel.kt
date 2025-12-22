package com.qweld.app.feature.exam.vm

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.qweld.app.domain.exam.ExamAttempt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qweld.app.common.error.AppError
import com.qweld.app.common.error.AppErrorHandler
import com.qweld.app.common.error.ErrorContext
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.prefs.UserPrefs
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.common.di.IoDispatcher
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.AssembledQuestion
import com.qweld.app.domain.exam.AttemptSeed
import com.qweld.app.domain.exam.ExamAssemblyConfig
import com.qweld.app.domain.exam.ExamAssemblerFactory
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.Question
import com.qweld.app.domain.exam.QuotaDistributor
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.blueprint.BlueprintCatalog
import com.qweld.app.feature.exam.data.blueprint.BlueprintProvider
import com.qweld.app.feature.exam.model.DeficitDetailUiModel
import com.qweld.app.feature.exam.model.DeficitDialogUiModel
import com.qweld.app.feature.exam.model.ExamAttemptUiState
import com.qweld.app.feature.exam.model.ExamChoiceUiModel
import com.qweld.app.feature.exam.model.ExamQuestionUiModel
import com.qweld.app.feature.exam.model.ExamUiState
import com.qweld.app.feature.exam.model.ResumeDialogUiModel
import com.qweld.app.feature.exam.model.ResumeLocaleOption
import com.qweld.app.feature.exam.vm.ResumeUseCase.MergeState
import com.qweld.core.common.AppEnv
import com.qweld.core.common.logging.LogTag
import com.qweld.core.common.logging.Logx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import java.time.Duration
import java.util.Locale
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import com.qweld.app.common.error.AppErrorEvent
import com.qweld.app.common.error.UiErrorEvent
import com.qweld.app.common.error.AppErrorReportResult

/**
 * ViewModel responsible for managing the exam/practice flow.
 *
 * ## Responsibilities:
 * 1. **Exam Assembly & Starting**: Load questions, assemble exams, handle errors
 * 2. **Resume Logic**: Detect and resume unfinished attempts with timer restoration
 * 3. **Navigation**: Handle question navigation with mode-specific rules
 * 4. **Answer Handling**: Submit and persist answers with time tracking
 * 5. **Finishing**: Calculate scores, persist results, navigate to result screen
 * 6. **Timer Management**: Start/resume/stop timers for timed exams (IP_MOCK)
 * 7. **Autosave**: Periodic background persistence of answers
 * 8. **Prewarming**: Pre-cache questions before starting exams
 * 9. **Abort/Restart**: Abandon or restart current attempts
 * 10. **State Management**: Convert domain models to UI state
 *
 * ## Architecture:
 * - Uses dedicated controllers for autosave (DefaultExamAutosaveController) and prewarming (DefaultExamPrewarmCoordinator)
 * - Uses separate use cases for resume logic (ResumeUseCase) and prewarming (PrewarmUseCase)
 * - Timer logic uses TimerController from domain layer via DefaultExamTimerController
 * - Repositories handle persistence (AttemptsRepository, AnswersRepository)
 *
 * ## Testing:
 * - All dependencies are injectable for testability
 * - Providers (seedProvider, nowProvider, etc.) allow deterministic testing
 * - IO operations use injected dispatcher for test control
 */
@HiltViewModel
class ExamViewModel @Inject constructor(
  private val repository: AssetQuestionRepository,
  private val attemptsRepository: AttemptsRepository,
  private val answersRepository: AnswersRepository,
  private val statsRepository: UserStatsRepository,
  private val userPrefs: UserPrefs,
  private val questionReportRepository: com.qweld.app.data.reports.QuestionReportRepository,
  private val appEnv: AppEnv,
  private val appErrorHandler: AppErrorHandler,
  private val resultHolder: ExamResultHolder,
  private val blueprintResolver: BlueprintResolver,
  private val timerController: TimerController,
  private val prewarmRunner: PrewarmController,
  private val resumeUseCase: ResumeUseCase,
  @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

  /**
   * Testing constructor that allows overriding internal hooks and coordinators.
   * Used by unit tests to inject deterministic behavior.
   */
  @VisibleForTesting
  internal constructor(
    repository: AssetQuestionRepository,
    attemptsRepository: AttemptsRepository,
    answersRepository: AnswersRepository,
    statsRepository: UserStatsRepository,
    userPrefs: UserPrefs,
    questionReportRepository: com.qweld.app.data.reports.QuestionReportRepository,
    appEnv: AppEnv,
    appErrorHandler: AppErrorHandler?,
    resultHolder: ExamResultHolder,
    blueprintResolver: BlueprintResolver,
    timerController: TimerController,
    prewarmRunner: PrewarmController,
    resumeUseCase: ResumeUseCase,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    seedProvider: () -> Long,
    userIdProvider: () -> String,
    attemptIdProvider: () -> String,
    nowProvider: () -> Long,
    timerCoordinatorOverride: ExamTimerController?,
    prewarmCoordinatorOverride: ExamPrewarmCoordinator?,
    autosaveCoordinatorOverride: ExamAutosaveController?,
  ) : this(
    repository = repository,
    attemptsRepository = attemptsRepository,
    answersRepository = answersRepository,
    statsRepository = statsRepository,
    userPrefs = userPrefs,
    questionReportRepository = questionReportRepository,
    appEnv = appEnv,
    appErrorHandler = appErrorHandler ?: NoOpAppErrorHandler(),
    resultHolder = resultHolder,
    blueprintResolver = blueprintResolver,
    timerController = timerController,
    prewarmRunner = prewarmRunner,
    resumeUseCase = resumeUseCase,
    ioDispatcher = ioDispatcher,
  ) {
    this.seedProvider = seedProvider
    this.userIdProvider = userIdProvider
    this.attemptIdProvider = attemptIdProvider
    this.nowProvider = nowProvider

    timerCoordinatorOverride?.let { this.timerCoordinator = it }
    prewarmCoordinatorOverride?.let { this.prewarmCoordinator = it }
    autosaveCoordinatorOverride?.let { this.autosaveCoordinator = it }
  }

  private val blueprintProvider: (ExamMode, Int) -> ExamBlueprint = blueprintResolver::forMode

  // Internal hook fields with production-safe defaults for testing
  internal var seedProvider: () -> Long = { Random.nextLong() }
  internal var userIdProvider: () -> String = { DEFAULT_USER_ID }
  internal var attemptIdProvider: () -> String = { UUID.randomUUID().toString() }
  internal var nowProvider: () -> Long = { System.currentTimeMillis() }

  // Internal coordinators initialized with production defaults
  internal var timerCoordinator: ExamTimerController =
    DefaultExamTimerController(timerController) { viewModelScope }
  internal var prewarmCoordinator: ExamPrewarmCoordinator =
    DefaultExamPrewarmCoordinator(viewModelScope, ioDispatcher, blueprintProvider, prewarmRunner)
  internal var autosaveCoordinator: ExamAutosaveController =
    DefaultExamAutosaveController(
      answersRepository = answersRepository,
      externalScope = viewModelScope,
      ioDispatcher = ioDispatcher,
      autosaveIntervalSec = AUTOSAVE_INTERVAL_SEC,
      autosaveFactory = { attemptId ->
        AutosaveController(
          attemptId = attemptId,
          answersRepository = answersRepository,
          scope = viewModelScope,
          ioDispatcher = ioDispatcher,
        )
      },
    )

  private val _uiState = mutableStateOf(ExamUiState())
  val uiState: State<ExamUiState> = _uiState

  private var currentAttempt: ExamAssemblerResult? = null
  private var latestTimerLabel: String? = null
  private var hasFinished: Boolean = false
  private var latestResult: ExamResultData? = null
  private var questionRationales: Map<String, String> = emptyMap()
  private val questionSeenAt = mutableMapOf<String, Long>()
  private var pendingResume: AttemptEntity? = null

  private val _events = MutableSharedFlow<ExamEvent>(extraBufferCapacity = 1)
  val events = _events.asSharedFlow()

  private val _effects = MutableSharedFlow<ExamEffect>(extraBufferCapacity = 1)
  val effects: Flow<ExamEffect> = _effects.asSharedFlow()

  private val _reportEvents = MutableSharedFlow<QuestionReportUiEvent>(extraBufferCapacity = 1)
  val reportEvents: Flow<QuestionReportUiEvent> = _reportEvents.asSharedFlow()

  private val _prewarmDisabled = MutableStateFlow(UserPrefsDataStore.DEFAULT_PREWARM_DISABLED)
  val prewarmDisabled = _prewarmDisabled.asStateFlow()

  private val _adaptiveExamEnabled = MutableStateFlow(UserPrefsDataStore.DEFAULT_ADAPTIVE_EXAM_ENABLED)
  val adaptiveExamEnabled = _adaptiveExamEnabled.asStateFlow()

  val lastPracticeScope = userPrefs.readLastPracticeScope()

  val lastPracticeConfig =
    userPrefs.readLastPracticeConfig().map { saved ->
      saved?.let {
        val distribution =
          when (it.distribution) {
            Distribution.Proportional.name -> Distribution.Proportional
            Distribution.Even.name -> Distribution.Even
            else -> Distribution.Proportional
          }
        PracticeConfig(
          size = PracticeConfig.sanitizeSize(it.size),
          scope = PracticeScope(blocks = it.blocks, taskIds = it.tasks, distribution = distribution),
          wrongBiased = it.wrongBiased,
        )
      }
    }

  // region Session Management

  private fun recordLastSession(
    mode: ExamMode,
    locale: String,
    practiceConfig: PracticeConfig? = _uiState.value.lastPracticeConfig,
  ) {
    val previous = _uiState.value
    val resolvedConfig =
      if (mode == ExamMode.PRACTICE) practiceConfig ?: previous.lastPracticeConfig else previous.lastPracticeConfig
    _uiState.value =
      previous.copy(
        lastMode = mode,
        lastLocale = locale,
        lastPracticeConfig = resolvedConfig,
      )
  }

  fun persistPracticeConfig(
    scope: PracticeScope,
    size: Int,
    wrongBiased: Boolean,
  ) {
    val normalizedBlocks =
      scope.blocks.mapNotNull { value ->
        val normalized = value.trim().uppercase(Locale.US)
        normalized.takeIf { it.isNotBlank() }
      }
    val normalizedTasks =
      scope.taskIds.mapNotNull { value ->
        val normalized = value.trim().uppercase(Locale.US)
        normalized.takeIf { it.isNotBlank() }
      }
    val normalizedScope =
      PracticeScope(
        blocks = normalizedBlocks.toSet(),
        taskIds = normalizedTasks.toSet(),
        distribution = scope.distribution,
      )
    val resolvedSize = PracticeConfig.sanitizeSize(size)
    val updatedConfig =
      PracticeConfig(
        size = resolvedSize,
        scope = normalizedScope,
        wrongBiased = wrongBiased,
      )
    _uiState.value = _uiState.value.copy(lastPracticeConfig = updatedConfig)
    viewModelScope.launch(ioDispatcher) {
      userPrefs.saveLastPracticeConfig(
        blocks = normalizedScope.blocks,
        tasks = normalizedScope.taskIds,
        distribution = normalizedScope.distribution.name,
        size = resolvedSize,
        wrongBiased = wrongBiased,
      )
    }
  }

  init {
    viewModelScope.launch {
      userPrefs.prewarmDisabled.collect { disabled ->
        val previous = _prewarmDisabled.value
        _prewarmDisabled.value = disabled
        if (disabled && !previous) {
          prewarmCoordinator.cancel()
        }
      }
    }
    viewModelScope.launch { userPrefs.adaptiveExamEnabled.collect { enabled -> _adaptiveExamEnabled.value = enabled } }
    viewModelScope.launch {
      prewarmCoordinator.prewarmState.collect { state ->
        _uiState.value = _uiState.value.copy(prewarmState = state)
      }
    }
  }

  // endregion

  // region Exam Assembly & Starting

  fun setAdaptiveExamEnabled(enabled: Boolean) {
    _adaptiveExamEnabled.value = enabled
    viewModelScope.launch { userPrefs.setAdaptiveExamEnabled(enabled) }
  }

  /**
   * Starts a new exam or practice attempt.
   * Loads questions, assembles the exam, and initializes state.
   * For IP_MOCK exams, starts the timer automatically.
   *
   * @return true if the attempt was successfully started, false otherwise
   */
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
    _uiState.value = _uiState.value.copy(resumeDialog = null)
    val blueprintSize =
      when {
        mode == ExamMode.PRACTICE && blueprintOverride == null -> normalizedPracticeConfig.size
        mode == ExamMode.ADAPTIVE -> DEFAULT_ADAPTIVE_SIZE
        else -> DEFAULT_PRACTICE_SIZE
      }
    val blueprint = blueprintOverride ?: blueprintProvider(mode, blueprintSize)
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
      is AssetQuestionRepository.LoadResult.Success -> {
        questionRationales = result.questions.mapNotNull { dto ->
          val rationale = dto.rationales?.get(dto.correctId)?.takeIf { it.isNotBlank() }
          rationale?.let { dto.id to it }
        }.toMap()
        result.questions.map { it.toDomain(normalizedLocale) }
      }
      AssetQuestionRepository.LoadResult.Missing -> {
        questionRationales = emptyMap()
        Timber.w("[exam_start] cancelled reason=missing locale=%s", normalizedLocale)
        return false
      }
      is AssetQuestionRepository.LoadResult.Corrupt -> {
        questionRationales = emptyMap()
        Timber.e(
          "[exam_start] cancelled reason=corrupt locale=%s error=%s",
          normalizedLocale,
          result.error.diagnosticMessage,
        )
        // Store the error for potential UI display
        currentAttempt = null
        val previous = _uiState.value
        _uiState.value =
          previous.copy(
            isLoading = false,
            attempt = null,
            deficitDialog = null,
            errorMessage = result.error.toUserMessage(),
          )
        return false
      }
    }

    val useAdaptive = mode == ExamMode.ADAPTIVE && _adaptiveExamEnabled.value
    if (mode == ExamMode.ADAPTIVE && !useAdaptive) {
      Timber.i("[adaptive_disabled] start blocked")
      return false
    }
    val assemblyConfig =
      ExamAssemblyConfig(
        practiceWrongBiased =
          mode == ExamMode.PRACTICE && normalizedPracticeConfig.wrongBiased,
      )
    val assemblerLogger: (String) -> Unit = { message ->
      if (message.startsWith("[deficit]")) {
        val payload = message.removePrefix("[deficit]").trim()
        val kv =
          payload
            .split(' ')
            .mapNotNull { entry ->
              val delimiter = entry.indexOf('=')
              if (delimiter <= 0) return@mapNotNull null
              entry.substring(0, delimiter) to entry.substring(delimiter + 1)
            }
        Logx.w(LogTag.DEFICIT, "detail", *kv.toTypedArray())
      } else {
        Timber.i(message)
      }
    }
    val attemptSeed = AttemptSeed(seedProvider())
    val assemblyOutcome: Outcome<AssemblyResult> =
      try {
        if (useAdaptive) {
          val assembler =
            ExamAssemblerFactory.adaptive(
              questionRepository = InMemoryQuestionRepository(questions),
              statsRepository = statsRepository,
              config = assemblyConfig,
              logger = assemblerLogger,
            )
          when (
            val result =
              runBlocking(ioDispatcher) {
                assembler.assemble(
                  userId = userIdProvider(),
                  locale = normalizedLocale,
                  seed = attemptSeed,
                  blueprint = blueprint,
                )
              }
          ) {
            is Outcome.Ok -> Outcome.Ok(AssemblyResult(result.value.exam, result.value.seed))
            is Outcome.Err -> result
          }
        } else {
          val assembler =
            ExamAssemblerFactory.standard(
              questionRepository = InMemoryQuestionRepository(questions),
              statsRepository = statsRepository,
              config = assemblyConfig,
              logger = assemblerLogger,
            )
          when (
            val result =
              runBlocking(ioDispatcher) {
                assembler.assemble(
                  userId = userIdProvider(),
                  mode = mode,
                  locale = normalizedLocale,
                  seed = attemptSeed,
                  blueprint = blueprint,
                )
              }
          ) {
            is Outcome.Ok -> Outcome.Ok(AssemblyResult(result.value.exam, result.value.seed))
            is Outcome.Err -> result
          }
        }
      } catch (error: Throwable) {
        if (error is CancellationException) throw error
        return handleAssemblyFailure(
          mode = mode,
          message = error.message ?: "Unable to start attempt.",
          throwable = error,
        )
      }

    return when (assemblyOutcome) {
      is Outcome.Ok -> {
        val attempt = assemblyOutcome.value.exam
        val attemptId = attemptIdProvider()
        val startedAt = nowProvider()
        Timber.i(
          "[attempt_create] id=%s mode=%s locale=%s",
          attemptId,
          attempt.mode.name,
          attempt.locale,
        )
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
        recordLastSession(
          mode = mode,
          locale = attempt.locale,
          practiceConfig = if (mode == ExamMode.PRACTICE) normalizedPracticeConfig else null,
        )
        hasFinished = false
        prepareAutosave(attemptId)
        latestResult = null
        resultHolder.clear()
        questionSeenAt.clear()
        if (mode == ExamMode.IP_MOCK) {
          startTimer()
        } else {
          stopTimer(clearLabel = true)
        }
        refreshState()
        if (mode == ExamMode.IP_MOCK || mode == ExamMode.ADAPTIVE) {
          Timber.i("[exam_start] ready=125 mode=%s", mode)
          _effects.tryEmit(ExamEffect.NavigateToExam)
        }
        true
      }
      is Outcome.Err.QuotaExceeded -> {
        questionRationales = emptyMap()
        Logx.w(
          LogTag.DEFICIT,
          "assembly",
          "taskId" to assemblyOutcome.taskId,
          "required" to assemblyOutcome.required,
          "have" to assemblyOutcome.have,
          "seed" to attemptSeed.value,
        )
        val details =
          listOf(
            DeficitDetailUiModel(
              taskId = assemblyOutcome.taskId,
              need = assemblyOutcome.required,
              have = assemblyOutcome.have,
              missing = assemblyOutcome.required - assemblyOutcome.have,
            )
          )
        if (mode == ExamMode.IP_MOCK) {
          val detailSummary =
            details.joinToString(separator = "\n") { detail ->
              "taskId=${detail.taskId} need=${detail.need} have=${detail.have} missing=${detail.missing}"
            }
          _effects.tryEmit(ExamEffect.ShowDeficit(detailSummary))
        }
        currentAttempt = null
        val previous = _uiState.value
        _uiState.value =
          previous.copy(
            isLoading = false,
            attempt = null,
            deficitDialog = DeficitDialogUiModel(details),
            errorMessage = null,
          )
        false
      }
      is Outcome.Err.ContentNotFound ->
        handleAssemblyFailure(
          mode = mode,
          message = "Missing content: ${assemblyOutcome.path}",
        )
      is Outcome.Err.SchemaViolation ->
        handleAssemblyFailure(
          mode = mode,
          message = "Invalid content at ${assemblyOutcome.path}: ${assemblyOutcome.reason}",
        )
      is Outcome.Err.IoFailure ->
        handleAssemblyFailure(
          mode = mode,
          message = assemblyOutcome.cause.message ?: "Unable to start attempt.",
          throwable = assemblyOutcome.cause,
        )
    }
  }

  private fun shouldNavigateToExam(mode: ExamMode): Boolean =
    when (mode) {
      ExamMode.IP_MOCK, ExamMode.ADAPTIVE, ExamMode.PRACTICE -> true
    }

  private fun handleAssemblyFailure(
    mode: ExamMode,
    message: String,
    throwable: Throwable? = null,
  ): Boolean {
    if (mode == ExamMode.IP_MOCK) {
      if (throwable != null) {
        Timber.e(throwable, "[exam_error] %s", message)
      } else {
        Timber.e("[exam_error] %s", message)
      }
      _effects.tryEmit(ExamEffect.ShowError(message))
    } else {
      if (throwable != null) {
        Timber.e(throwable)
      } else {
        Timber.e(message)
      }
    }
    questionRationales = emptyMap()
    currentAttempt = null
    val previous = _uiState.value
    _uiState.value =
      previous.copy(
        isLoading = false,
        attempt = null,
        deficitDialog = null,
        errorMessage = message,
      )
    return false
  }

  // endregion

  // region Resume Logic

  /**
   * Detects if there's an unfinished attempt and shows resume dialog.
   * Handles locale mismatches and expired timers.
   */
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
        val reconstructionOutcome =
          withContext(ioDispatcher) {
            resumeUseCase.reconstructAttempt(
              mode = mode,
              seed = pending.seed,
              locale = targetLocale,
              questionCount = pending.questionCount,
            )
          }
        val reconstruction =
          when (reconstructionOutcome) {
            is Outcome.Ok -> reconstructionOutcome.value
            is Outcome.Err.QuotaExceeded -> {
              Logx.w(
                LogTag.DEFICIT,
                "resume",
                "taskId" to reconstructionOutcome.taskId,
                "required" to reconstructionOutcome.required,
                "have" to reconstructionOutcome.have,
                "seed" to pending.seed,
              )
              pendingResume = null
              _uiState.value =
                _uiState.value.copy(
                  resumeDialog = null,
                  errorMessage = "Unable to resume attempt.",
                )
              return@launch
            }
            is Outcome.Err.ContentNotFound -> {
              Timber.e(
                "[resume_continue] missing content path=%s attemptId=%s",
                reconstructionOutcome.path,
                attemptId,
              )
              pendingResume = null
              _uiState.value =
                _uiState.value.copy(
                  resumeDialog = null,
                  errorMessage = "Unable to resume attempt.",
                )
              return@launch
            }
            is Outcome.Err.SchemaViolation -> {
              Timber.e(
                "[resume_continue] invalid content path=%s reason=%s attemptId=%s",
                reconstructionOutcome.path,
                reconstructionOutcome.reason,
                attemptId,
              )
              pendingResume = null
              _uiState.value =
                _uiState.value.copy(
                  resumeDialog = null,
                  errorMessage = "Unable to resume attempt.",
                )
              return@launch
            }
            is Outcome.Err.IoFailure -> {
              Timber.e(
                reconstructionOutcome.cause,
                "[resume_continue] failed attemptId=%s",
                attemptId,
              )
              pendingResume = null
              _uiState.value =
                _uiState.value.copy(
                  resumeDialog = null,
                  errorMessage = reconstructionOutcome.cause.message ?: "Unable to resume attempt.",
                )
              return@launch
            }
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
        recordLastSession(mode, reconstruction.attempt.locale)
        hasFinished = false
        prepareAutosave(pending.id)
        latestResult = null
        resultHolder.clear()
        pendingResume = null
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

  // endregion

  // region Answer Handling

  /**
   * Submits an answer for the current question.
   * Answers are locked after first submission (cannot be changed).
   * Automatically persists via autosave controller.
   */
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
    autosaveCoordinator.recordAnswer(answerEntity)
    Timber.i("[answer_write] attempt=%s qId=%s correct=%s", attemptResult.attemptId, questionId, correct)

    val updatedAnswers = attemptResult.answers + (questionId to choiceId)
    currentAttempt = attemptResult.copy(answers = updatedAnswers)
    refreshState()
  }

  // endregion

  // region Finishing

  /**
   * Finishes the current exam, calculates score, and persists results.
   * Stops timer, flushes autosave, and navigates to result screen.
   */
  fun finishExam() {
    val attemptResult = currentAttempt ?: return
    if (hasFinished) return
    val attempt = attemptResult.attempt
    val remaining =
      if (attempt.mode == ExamMode.IP_MOCK) {
        timerCoordinator.remaining()
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
      autosaveCoordinator.flush(force = true)
      val finishedAt = nowProvider()
      val durationSec = ((finishedAt - attemptResult.startedAt).coerceAtLeast(0L) / 1_000L).toInt()
      val passThreshold = if (attempt.mode == ExamMode.IP_MOCK) IP_MOCK_PASS_THRESHOLD else null
      Timber.i("[attempt_finish] id=%s scorePct=%.2f", attemptResult.attemptId, scorePercent)

      viewModelScope.launch(ioDispatcher) {
          stopAutosaveBlocking()
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
    resultHolder.update(checkNotNull(latestResult))
    _events.tryEmit(ExamEvent.NavigateToResult)
  }

  // endregion

  // region Navigation

  /**
   * Navigates to the next question.
   * Does nothing if already at the last question.
   */
  fun nextQuestion() {
    val attemptResult = currentAttempt ?: return
    val attempt = attemptResult.attempt
    val nextIndex = (attemptResult.currentIndex + 1).coerceAtMost(attempt.questions.lastIndex)
    if (nextIndex == attemptResult.currentIndex) return
    currentAttempt = attemptResult.copy(currentIndex = nextIndex)
    refreshState()
  }

  /**
   * Navigates to the previous question.
   * Only allowed in PRACTICE mode (disabled for IP_MOCK).
   * Does nothing if already at the first question.
   */
  fun previousQuestion() {
    val attemptResult = currentAttempt ?: return
    if (attemptResult.attempt.mode == ExamMode.IP_MOCK) return
    val prevIndex = (attemptResult.currentIndex - 1).coerceAtLeast(0)
    if (prevIndex == attemptResult.currentIndex) return
    currentAttempt = attemptResult.copy(currentIndex = prevIndex)
    refreshState()
  }

  // endregion

  // region Question Reporting

  private data class QuestionReportContext(
    val attemptId: String?,
    val attempt: com.qweld.app.domain.exam.ExamAttempt,
    val answers: Map<String, String>,
    val questionIndex: Int,
  )

  sealed class QuestionReportUiEvent {
    data object Sent : QuestionReportUiEvent()
    data object Queued : QuestionReportUiEvent()
    data object Failed : QuestionReportUiEvent()
  }

  fun reportCurrentQuestion(
    reason: com.qweld.app.feature.exam.model.QuestionReportReason,
    userComment: String?,
  ) {
    val context = currentAttempt?.let { attemptResult ->
      QuestionReportContext(
        attemptId = attemptResult.attemptId,
        attempt = attemptResult.attempt,
        answers = attemptResult.answers,
        questionIndex = attemptResult.currentIndex,
      )
    }
    submitQuestionReport(context, reason, userComment)
  }

  fun reportQuestionById(
    questionId: String,
    reason: com.qweld.app.feature.exam.model.QuestionReportReason,
    userComment: String?,
  ) {
    val result = latestResult
    if (result == null) {
      Timber.w("[question_report] skipped reason=no_result questionId=%s", questionId)
      return
    }
    val questionIndex = result.attempt.questions.indexOfFirst { assembled ->
      assembled.question.id == questionId
    }
    if (questionIndex < 0) {
      Timber.w("[question_report] skipped reason=question_not_found questionId=%s", questionId)
      return
    }
    val context = QuestionReportContext(
      attemptId = result.attemptId,
      attempt = result.attempt,
      answers = result.answers,
      questionIndex = questionIndex,
    )
    submitQuestionReport(context, reason, userComment)
  }

  private fun submitQuestionReport(
    context: QuestionReportContext?,
    reason: com.qweld.app.feature.exam.model.QuestionReportReason,
    userComment: String?,
  ) {
    val report = buildQuestionReport(context, reason, userComment)
    if (report == null) {
      Timber.w("[question_report] cannot build report, missing context")
      return
    }

    viewModelScope.launch {
      try {
        when (val result = questionReportRepository.submitReport(report)) {
          is com.qweld.app.data.reports.QuestionReportSubmitResult.Sent -> {
            Timber.i(
              "[question_report] submitted questionId=%s reason=%s mode=%s",
              report.questionId,
              report.reasonCode,
              report.mode,
            )
            _reportEvents.emit(QuestionReportUiEvent.Sent)
          }
          is com.qweld.app.data.reports.QuestionReportSubmitResult.Queued -> {
            Timber.w(
              "[question_report] queued questionId=%s reason=%s error=%s",
              report.questionId,
              report.reasonCode,
              result.error?.message ?: "unknown",
            )
            _reportEvents.emit(QuestionReportUiEvent.Queued)
          }
        }
      } catch (e: Exception) {
        Timber.e(e, "[question_report] submit failed for questionId=%s", report.questionId)
        appErrorHandler?.handle(
          AppError.Reporting(
            cause = e,
            context =
              ErrorContext(
                screen = "exam",
                action = "question_report_submit",
                extras = mapOf("questionId" to report.questionId),
              ),
          )
        )
        _reportEvents.emit(QuestionReportUiEvent.Failed)
        // User continues exam even if report fails
      }
    }
  }

  /**
   * Builds a QuestionReport from current ExamViewModel state.
   * Returns null if essential context is missing.
   */
  private fun buildQuestionReport(
    context: QuestionReportContext?,
    reason: com.qweld.app.feature.exam.model.QuestionReportReason,
    userComment: String?,
  ): com.qweld.app.data.reports.QuestionReport? {
    val context = context ?: return null
    val assembledQuestion = context.attempt.questions.getOrNull(context.questionIndex) ?: return null
    val attempt = context.attempt
    val question = assembledQuestion.question
    val blueprint = attempt.blueprint
    val blueprintId = BlueprintCatalog.DEFAULT_ID.name.lowercase()
    val blueprintVersion = BlueprintCatalog.versionLabelFor(BlueprintCatalog.DEFAULT_ID)

    // Get selected and correct choice indices
    val selectedChoiceId = context.answers[question.id]
    val selectedChoiceIndex = assembledQuestion.choices.indexOfFirst { it.id == selectedChoiceId }
      .takeIf { it >= 0 }
    val correctChoiceIndex = assembledQuestion.correctIndex

    // Find blueprint quota for this task
    val taskQuota = blueprint.taskQuotas.find { it.taskId == question.taskId }?.required

    // Get app version and build info from injected AppEnv
    val appVersionName = appEnv.appVersionName
    val appVersionCode = appEnv.appVersionCode
    val buildType = appEnv.buildType

    // Get device info
    val androidVersion = try {
      android.os.Build.VERSION.RELEASE
    } catch (e: Exception) {
      null
    }
    val deviceModel = try {
      android.os.Build.MODEL
    } catch (e: Exception) {
      null
    }
    // Возьмём последний AppErrorEvent из истории и проверим, что он был недавно.
    val recentError =
      appErrorHandler
        ?.history
        ?.value
        ?.lastOrNull()
        ?.takeIf { event ->
          nowProvider() - event.timestamp <= ERROR_CONTEXT_WINDOW_MS
        }

    return com.qweld.app.data.reports.QuestionReport(
      // Core identifiers
      questionId = question.id,
      taskId = question.taskId,
      blockId = question.blockId,
      blueprintId = blueprintId,

      // Localization & mode
      locale = attempt.locale,
      mode = attempt.mode.name,

      // Reason
      reasonCode = reason.code,
      reasonDetail = null,
      userComment = userComment,

      // Position/context within attempt
      questionIndex = context.questionIndex,
      totalQuestions = attempt.questions.size,
      selectedChoiceIds = selectedChoiceIndex?.let { listOf(it) },
      correctChoiceIds = listOf(correctChoiceIndex),
      blueprintTaskQuota = taskQuota,

      // Versions & environment
      contentIndexSha = null, // Not available in current context
      blueprintVersion = blueprintVersion,
      contentVersion = blueprintVersion,
      appVersionName = appVersionName,
      appVersionCode = appVersionCode,
      buildType = buildType,
      platform = "android",
      androidVersion = androidVersion,
      deviceModel = deviceModel,

      // Session/attempt context (no PII)
      sessionId = null, // Not tracked currently
      attemptId = context.attemptId,
      seed = attempt.seed.value,
      attemptKind = attempt.mode.name, // e.g., "IP_MOCK", "PRACTICE", "ADAPTIVE"

        // Error correlation
        errorContextId = recentError?.id,
        // Короткое не-PII описание — тип ошибки
        errorContextMessage = recentError?.error?.javaClass?.simpleName,
        recentError = recentError != null,

      // Admin / workflow
      status = "OPEN",
      createdAt = null, // Firestore will set serverTimestamp
      review = null,
    )
  }

  // endregion

  // region UI State Management

  fun dismissDeficitDialog() {
    _uiState.value = _uiState.value.copy(deficitDialog = null)
  }

  suspend fun abortAttempt(reason: String = "user_exit") {
    val aborted = closeCurrentAttemptAsAborted(reason)
    if (!aborted) {
      Timber.i("[attempt_abort] id=none reason=%s", reason)
    }
    _effects.emit(ExamEffect.NavigateToMode)
  }

  // endregion

  // region Abort & Restart

  /**
   * Restarts the current attempt with the same configuration.
   * For PRACTICE mode, emits effect to restart with stored config.
   * For IP_MOCK mode, starts a new attempt immediately.
   */
  fun restartAttempt() {
    val attemptResult = currentAttempt
    if (attemptResult == null) {
      Timber.w("[attempt_restart] skipped reason=no_attempt")
      viewModelScope.launch { _effects.emit(ExamEffect.ShowError("Unable to restart.")) }
      return
    }
    val attempt = attemptResult.attempt
    val questionCount = attempt.questions.size
    when (attempt.mode) {
      ExamMode.PRACTICE -> {
        val storedConfig = _uiState.value.lastPracticeConfig
        if (storedConfig == null) {
          Timber.w("[attempt_restart] mode=Practice size=%d scope=[] dist=%s wrongBiased=%s | reason=no_config", questionCount, Distribution.Proportional.name, false)
          viewModelScope.launch { _effects.emit(ExamEffect.ShowError("Unable to restart practice.")) }
          return
        }
        val sanitized = storedConfig.copy(size = PracticeConfig.sanitizeSize(storedConfig.size))
        val locale = attempt.locale
        viewModelScope.launch {
          logRestart(ExamMode.PRACTICE, sanitized, questionCount)
          val aborted = closeCurrentAttemptAsAborted(reason = "user_restart")
          if (!aborted) return@launch
          recordLastSession(ExamMode.PRACTICE, locale, sanitized)
          _effects.emit(ExamEffect.RestartWithSameConfig)
        }
      }
      ExamMode.IP_MOCK -> {
        val locale = attempt.locale
        viewModelScope.launch {
          logRestart(ExamMode.IP_MOCK, null, questionCount)
          val aborted = closeCurrentAttemptAsAborted(reason = "user_restart")
          if (!aborted) return@launch
          val launched = startAttempt(ExamMode.IP_MOCK, locale)
          if (!launched) {
            _effects.emit(ExamEffect.ShowError("Unable to restart exam."))
          }
        }
      }
      else -> {
        Timber.w("[attempt_restart] skipped reason=unsupported_mode mode=%s", attempt.mode.name)
        viewModelScope.launch { _effects.emit(ExamEffect.ShowError("Unable to restart.")) }
      }
    }
  }

  fun notifyRestartFailure(message: String) {
    _effects.tryEmit(ExamEffect.ShowError(message))
  }

  // endregion

  // region State Refresh & UI Mapping

  /**
   * Refreshes UI state from current attempt data.
   * Converts domain models to UI models and updates mutable state.
   */
  private fun refreshState() {
    val attemptResult = currentAttempt
    if (attemptResult == null) {
      latestTimerLabel = null
      val previous = _uiState.value
      _uiState.value =
        previous.copy(
          isLoading = false,
          attempt = null,
          deficitDialog = null,
          errorMessage = null,
          timerLabel = null,
          resumeDialog = null,
        )
      return
    }
    val previous = _uiState.value
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
      previous.copy(
        isLoading = false,
        attempt = attemptState,
        deficitDialog = null,
        errorMessage = null,
        timerLabel = latestTimerLabel,
        resumeDialog = null,
      )
    currentAttempt = attemptResult.copy(currentIndex = index)
  }

  // endregion

  // region Prewarming

  /**
   * Pre-caches questions for IP_MOCK exams to improve start performance.
   * Only runs if prewarming is not disabled in user preferences.
   */
  fun startPrewarmForIpMock(locale: String) {
    if (_prewarmDisabled.value) return
    prewarmCoordinator.startForIpMock(locale)
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

  // endregion

  // region Timer Management

  fun requireLatestResult(): ExamResultData {
    return checkNotNull(latestResult) { "Result is not available" }
  }

  private fun updateTimerLabel(label: String?) {
    latestTimerLabel = label
    _uiState.value = _uiState.value.copy(timerLabel = label)
  }

  /**
   * Persists remaining timer value to support process-death resume.
   * Only persists for IP_MOCK mode; other modes don't have timers.
   */
  private fun persistRemainingTime(remainingMs: Long?) {
    val attemptResult = currentAttempt ?: return
    val mode = attemptResult.attempt.mode
    if (mode != ExamMode.IP_MOCK) return

    viewModelScope.launch(ioDispatcher) {
      attemptsRepository.updateRemainingTime(
        attemptId = attemptResult.attemptId,
        remainingTimeMs = remainingMs,
      )
    }
  }

  /**
   * Starts a new timer for IP_MOCK exams.
   * Timer runs for 4 hours and automatically finishes exam when expired.
   */
  private fun startTimer() {
    val initialLabel = TimerController.formatDuration(TimerController.EXAM_DURATION)
    Timber.i(
      "[exam_timer] started=4h remaining=%s",
      initialLabel,
    )
    timerCoordinator.start(
      onTick = { label, remaining ->
        updateTimerLabel(label)
        persistRemainingTime(remaining.toMillis())
      },
      onExpired = { finishExam() },
    )
  }

  /**
   * Resumes a timer with the given remaining duration.
   * Used when resuming an interrupted IP_MOCK exam.
   */
  private fun resumeTimer(initialRemaining: Duration) {
    val initialLabel = TimerController.formatDuration(initialRemaining)
    Timber.i(
      "[exam_timer] resumed remaining=%s",
      initialLabel,
    )
    timerCoordinator.resume(
      initialRemaining = initialRemaining,
      onTick = { label, remaining ->
        updateTimerLabel(label)
        persistRemainingTime(remaining.toMillis())
      },
      onExpired = { finishExam() },
    )
  }

  /**
   * Stops the timer and optionally clears the timer label from UI.
   */
  private fun stopTimer(clearLabel: Boolean) {
    timerCoordinator.stop(clearLabel)
    updateTimerLabel(if (clearLabel) null else timerCoordinator.latestLabel)
  }

  // endregion

  // region Helper Methods

  /**
   * Finalizes an expired attempt by calculating score from saved answers.
   * Used when resume is attempted but timer has expired.
   */
  private suspend fun finalizeExpiredAttempt(
    attempt: AttemptEntity,
    mode: ExamMode,
  ) {
    val answers = answersRepository.listByAttempt(attempt.id)
    val reconstructionOutcome =
      resumeUseCase.reconstructAttempt(
        mode = mode,
        seed = attempt.seed,
        locale = attempt.locale.lowercase(Locale.US),
        questionCount = attempt.questionCount,
      )
    val reconstruction =
      when (reconstructionOutcome) {
        is Outcome.Ok -> reconstructionOutcome.value
        is Outcome.Err -> {
          when (reconstructionOutcome) {
            is Outcome.Err.QuotaExceeded ->
              Logx.w(
                LogTag.DEFICIT,
                "expire",
                "taskId" to reconstructionOutcome.taskId,
                "required" to reconstructionOutcome.required,
                "have" to reconstructionOutcome.have,
                "seed" to attempt.seed,
              )
            is Outcome.Err.ContentNotFound ->
              Timber.e(
                "[resume_timeout] missing content path=%s attemptId=%s",
                reconstructionOutcome.path,
                attempt.id,
              )
            is Outcome.Err.SchemaViolation ->
              Timber.e(
                "[resume_timeout] invalid content path=%s reason=%s attemptId=%s",
                reconstructionOutcome.path,
                reconstructionOutcome.reason,
                attempt.id,
              )
            is Outcome.Err.IoFailure ->
              Timber.e(
                reconstructionOutcome.cause,
                "[resume_timeout] failed to reconstruct attemptId=%s",
                attempt.id,
              )
          }
          val finishedAt = nowProvider()
          val durationSec = TimerController.EXAM_DURATION.seconds.toInt()
          val passThreshold = if (mode == ExamMode.IP_MOCK) IP_MOCK_PASS_THRESHOLD else null
          attemptsRepository.markFinished(
            attemptId = attempt.id,
            finishedAt = finishedAt,
            durationSec = durationSec,
            passThreshold = passThreshold,
            scorePct = 0.0,
          )
          Timber.i(
            "[resume_timeout] attemptId=%s correct=%d/%d score=%.2f",
            attempt.id,
            0,
            attempt.questionCount,
            0.0,
          )
          return
        }
      }
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

  private suspend fun closeCurrentAttemptAsAborted(reason: String): Boolean {
    val attemptResult = currentAttempt ?: return false
    val attempt = attemptResult.attempt
    Timber.i("[attempt_abort] id=%s reason=%s", attemptResult.attemptId, reason)
    autosaveCoordinator.flush(force = true)
    stopAutosave()
    stopTimer(clearLabel = true)
    withContext(ioDispatcher) { attemptsRepository.abortAttempt(attemptResult.attemptId) }
    currentAttempt = null
    latestResult = null
    resultHolder.clear()
    hasFinished = false
    questionSeenAt.clear()
    recordLastSession(attempt.mode, attempt.locale)
    val previous = _uiState.value
    _uiState.value =
      previous.copy(
        attempt = null,
        deficitDialog = null,
        errorMessage = null,
        timerLabel = null,
        resumeDialog = null,
      )
    return true
  }

  private fun logRestart(
    mode: ExamMode,
    practiceConfig: PracticeConfig?,
    questionCount: Int,
  ) {
    val scopeValues =
      if (practiceConfig == null) {
        emptyList()
      } else {
        val scope = practiceConfig.scope
        val tasks = scope.taskIds
        if (tasks.isNotEmpty()) {
          tasks.map { it.uppercase(Locale.US) }
        } else {
          scope.blocks.map { it.uppercase(Locale.US) }
        }
      }
    val scopeLog = scopeValues.sorted().joinToString(prefix = "[", postfix = "]")
    val distribution = practiceConfig?.scope?.distribution?.name ?: Distribution.Proportional.name
    val wrongBiased = practiceConfig?.wrongBiased ?: false
    val resolvedSize = if (mode == ExamMode.PRACTICE) practiceConfig?.size ?: questionCount else questionCount
    Timber.i(
      "[attempt_restart] mode=%s size=%d scope=%s dist=%s wrongBiased=%s",
      when (mode) {
        ExamMode.IP_MOCK -> "IPMock"
        ExamMode.PRACTICE -> "Practice"
        ExamMode.ADAPTIVE -> "Adaptive"
      },
      resolvedSize,
      scopeLog,
      distribution,
      wrongBiased,
    )
  }

  // endregion

  // region Autosave

  fun autosaveFlush(force: Boolean = true) {
    autosaveCoordinator.flush(force)
  }

  private fun prepareAutosave(attemptId: String) {
    autosaveCoordinator.prepare(attemptId)
  }

  private fun stopAutosave() {
    viewModelScope.launch { autosaveCoordinator.stop() }
  }

  private suspend fun stopAutosaveBlocking() {
    autosaveCoordinator.stop()
  }

  override fun onCleared() {
    stopAutosave()
    super.onCleared()
  }

  // endregion

  // region Events & Effects

  sealed interface ExamEvent {
    data object NavigateToResult : ExamEvent
    data object ResumeReady : ExamEvent
  }

  sealed class ExamEffect {
    data object NavigateToExam : ExamEffect()
    data object NavigateToMode : ExamEffect()
    data object RestartWithSameConfig : ExamEffect()
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
    val sizeSource = if (resolvedConfig.size in PracticeConfig.PRESETS) "preset" else "manual"
    Timber.i("[practice_size] value=%d source=%s", resolvedConfig.size, sizeSource)
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
        userPrefs.saveLastPracticeConfig(
          blocks = resolvedConfig.scope.blocks,
          tasks = resolvedConfig.scope.taskIds,
          distribution = resolvedConfig.scope.distribution.name,
          size = resolvedConfig.size,
          wrongBiased = resolvedConfig.wrongBiased,
        )
      }
      Timber.i(
        "[practice_scope_saved] blocks=%s tasks=%s dist=%s",
        scopeBlocksLog,
        scopeTasksLog,
        resolvedConfig.scope.distribution.name,
      )
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
    ): Outcome<List<Question>> {
      val normalizedLocale = locale.lowercase(Locale.US)
      val primary = questions.filter { it.taskId == taskId && it.locale.equals(normalizedLocale, ignoreCase = true) }
      if (primary.isNotEmpty() || !allowFallbackToEnglish) {
        return Outcome.Ok(primary)
      }
      return Outcome.Ok(questions.filter { it.taskId == taskId && it.locale.equals("en", ignoreCase = true) })
    }
  }

  /**
   * Converts ContentLoadError to a user-friendly message.
   * Provides specific guidance based on error type while avoiding technical jargon.
   */
  private fun com.qweld.app.feature.exam.data.ContentLoadError.toUserMessage(): String {
    return when (this) {
      is com.qweld.app.feature.exam.data.ContentLoadError.MissingManifest ->
        "Content manifest is missing for locale: ${locale.uppercase(Locale.US)}. Please check your installation."

      is com.qweld.app.feature.exam.data.ContentLoadError.InvalidManifest ->
        "Content manifest is invalid for locale: ${locale.uppercase(Locale.US)}. Please reinstall the app."

      is com.qweld.app.feature.exam.data.ContentLoadError.MissingTaskFile ->
        "Question file for task $taskId is missing. Please check your installation."

      is com.qweld.app.feature.exam.data.ContentLoadError.TaskFileReadError ->
        "Unable to read questions for task $taskId. Please reinstall the app."

      is com.qweld.app.feature.exam.data.ContentLoadError.IntegrityMismatch ->
        "Content verification failed. The app data may be corrupted. Please reinstall the app."

      is com.qweld.app.feature.exam.data.ContentLoadError.InvalidJson ->
        "Question data is invalid or corrupted. Please reinstall the app."

      is com.qweld.app.feature.exam.data.ContentLoadError.UnsupportedLocale ->
        "Language ${requestedLocale.uppercase(Locale.US)} is not supported. Available languages: ${availableLocales.joinToString { it.uppercase(Locale.US) }}"

      is com.qweld.app.feature.exam.data.ContentLoadError.MissingBank ->
        "Question bank is missing for locale: ${locale.uppercase(Locale.US)}. Please check your installation."

      is com.qweld.app.feature.exam.data.ContentLoadError.BankFileError ->
        "Question bank is corrupted or invalid. Please reinstall the app."

      is com.qweld.app.feature.exam.data.ContentLoadError.Unknown ->
        "An unexpected error occurred while loading questions. Please try again or reinstall the app."
    }
  }

  private data class AssemblyResult(val exam: ExamAttempt, val seed: Long)

  /**
   * No-op implementation of AppErrorHandler for testing.
   * Does nothing when errors are handled.
   */
  private class NoOpAppErrorHandler : AppErrorHandler {
    override val history: StateFlow<List<AppErrorEvent>> = MutableStateFlow(emptyList())
    override val uiEvents: SharedFlow<UiErrorEvent> = MutableSharedFlow()
    override val analyticsEnabled: StateFlow<Boolean> = MutableStateFlow(false)

    override fun handle(error: AppError) {
      // No-op
    }

    override fun updateAnalyticsEnabled(userOptIn: Boolean) {
      // No-op
    }

    override suspend fun submitReport(event: AppErrorEvent, comment: String?): AppErrorReportResult {
      return AppErrorReportResult.Disabled
    }
  }

  companion object {
    private const val DEFAULT_PRACTICE_SIZE = PracticeConfig.DEFAULT_SIZE
    private const val DEFAULT_ADAPTIVE_SIZE = BlueprintResolver.DEFAULT_ADAPTIVE_SIZE
    private const val DEFAULT_USER_ID = "local_user"
    private const val IP_MOCK_PASS_THRESHOLD = 70
    private const val AUTOSAVE_INTERVAL_SEC = 10
    private val ERROR_CONTEXT_WINDOW_MS = Duration.ofMinutes(3).toMillis()

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

  }
}
