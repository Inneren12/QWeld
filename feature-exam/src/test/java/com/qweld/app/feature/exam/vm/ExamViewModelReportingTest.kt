package com.qweld.app.feature.exam.vm

import com.qweld.app.data.reports.QuestionReport
import com.qweld.app.data.reports.QuestionReportQueueStatus
import com.qweld.app.data.reports.QuestionReportRepository
import com.qweld.app.data.reports.QuestionReportRetryResult
import com.qweld.app.data.reports.QuestionReportSubmitResult
import com.qweld.app.data.reports.QuestionReportSummary
import com.qweld.app.data.reports.QuestionReportWithId
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.common.error.AppError
import com.qweld.app.common.error.AppErrorEvent
import com.qweld.app.common.error.AppErrorHandler
import com.qweld.app.common.error.AppErrorReportResult
import com.qweld.app.common.error.UiErrorEvent
import com.qweld.app.feature.exam.FakeUserPrefs
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.TestIntegrity
import com.qweld.app.feature.exam.fakes.FakeAnswerDao
import com.qweld.app.feature.exam.fakes.FakeAttemptDao
import com.qweld.app.feature.exam.model.QuestionReportReason
import com.qweld.app.feature.exam.vm.fakes.FakeAppEnv
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelReportingTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  @Test
  fun reportQueued_emitsQueuedUiEvent_withoutCrashing() = runTest {
    val repository = repositoryWithQuestions(count = 1)
    val appErrorHandler = RecordingAppErrorHandler()
    val viewModel =
      createViewModel(
        repository = repository,
        questionReportRepository = QueuingReportRepository,
        appErrorHandler = appErrorHandler,
        ioDispatcher = dispatcherRule.dispatcher,
      )

    val started = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")
    assertTrue(started)
    advanceUntilIdle()

    viewModel.reportCurrentQuestion(QuestionReportReason.WRONG_ANSWER, "queued")
    val event = viewModel.reportEvents.first()

    assertEquals(ExamViewModel.QuestionReportUiEvent.Queued, event)
    assertTrue(appErrorHandler.handledErrors.isEmpty())
  }

  @Test
  fun reportFailure_emitsFailedEvent_andForwardedToAppErrorHandler() = runTest {
    val repository = repositoryWithQuestions(count = 1)
    val appErrorHandler = RecordingAppErrorHandler()
    val viewModel =
      createViewModel(
        repository = repository,
        questionReportRepository = FailingReportRepository,
        appErrorHandler = appErrorHandler,
        ioDispatcher = dispatcherRule.dispatcher,
      )

    val started = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")
    assertTrue(started)
    advanceUntilIdle()

    viewModel.reportCurrentQuestion(QuestionReportReason.WRONG_ANSWER, "offline")
    val event = viewModel.reportEvents.first()

    assertEquals(ExamViewModel.QuestionReportUiEvent.Failed, event)
    assertTrue(appErrorHandler.handledErrors.any { it is AppError.Reporting })
  }

  private fun createViewModel(
    repository: AssetQuestionRepository,
    questionReportRepository: QuestionReportRepository,
    appErrorHandler: AppErrorHandler,
    ioDispatcher: CoroutineDispatcher,
  ): ExamViewModel {
    val attemptDao = FakeAttemptDao()
    val answerDao = FakeAnswerDao()
    val attemptsRepository = AttemptsRepository(attemptDao) { }
    val answersRepository = AnswersRepository(answerDao)
    val statsRepository =
      object : UserStatsRepository {
        override suspend fun getUserItemStats(
          userId: String,
          ids: List<String>,
        ): Outcome<Map<String, com.qweld.app.domain.exam.ItemStats>> = Outcome.Ok(emptyMap())
      }
    val blueprint = ExamBlueprint(
      totalQuestions = 1,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = 1)),
    )
    return ExamViewModel(
      repository = repository,
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      statsRepository = statsRepository,
      userPrefs = FakeUserPrefs(),
      questionReportRepository = questionReportRepository,
      appEnv = FakeAppEnv(),
      blueprintProvider = { _, _ -> blueprint },
      seedProvider = { 1L },
      userIdProvider = { "user" },
      attemptIdProvider = { UUID.randomUUID().toString() },
      nowProvider = { 0L },
      timerController = com.qweld.app.domain.exam.TimerController { },
      ioDispatcher = ioDispatcher,
      prewarmController =
        PrewarmController(
          repository = repository,
          prewarmUseCase =
            PrewarmUseCase(
              repository,
              prewarmDisabled = MutableStateFlow(false),
              ioDispatcher = ioDispatcher,
              nowProvider = { 0L },
            ),
        ),
    )
  }

  private fun repositoryWithQuestions(count: Int): AssetQuestionRepository {
    val questions = buildString {
      append("[")
      repeat(count) { index ->
        if (index > 0) append(",")
        val qId = "Q${index + 1}"
        val choiceIds = listOf("A", "B", "C", "D").map { suffix -> "${qId}-$suffix" }
        append(
          """
          {
            \"id\": \"$qId\",
            \"taskId\": \"A-1\",
            \"blockId\": \"A\",
            \"locale\": \"en\",
            \"stem\": { \"en\": \"Stem $qId\" },
            \"choices\": [
              { \"id\": \"${choiceIds[0]}\", \"text\": { \"en\": \"Choice 1\" } },
              { \"id\": \"${choiceIds[1]}\", \"text\": { \"en\": \"Choice 2\" } },
              { \"id\": \"${choiceIds[2]}\", \"text\": { \"en\": \"Choice 3\" } },
              { \"id\": \"${choiceIds[3]}\", \"text\": { \"en\": \"Choice 4\" } }
            ],
            \"correctId\": \"${choiceIds[0]}\"
          }
          """.trimIndent(),
        )
      }
      append("]")
    }
    val assets =
      TestIntegrity.addIndexes(
        mapOf("questions/en/bank.v1.json" to questions.toByteArray()),
      )
    return AssetQuestionRepository(
      assetReader = AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
      localeResolver = { "en" },
      json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
    )
  }
}

private object QueuingReportRepository : QuestionReportRepository {
  override suspend fun submitReport(report: QuestionReport): QuestionReportSubmitResult =
    QuestionReportSubmitResult.Queued(queueId = 1L)

  override suspend fun listReports(status: String?, limit: Int): List<QuestionReportWithId> = emptyList()

  override suspend fun listReportSummaries(limit: Int): List<QuestionReportSummary> = emptyList()

  override suspend fun listReportsForQuestion(questionId: String, limit: Int): List<QuestionReportWithId> = emptyList()

  override suspend fun getReportById(reportId: String): QuestionReportWithId? = null

  override suspend fun updateReportStatus(
    reportId: String,
    status: String,
    resolutionCode: String?,
    resolutionComment: String?,
  ) = Unit

  override suspend fun retryQueuedReports(maxAttempts: Int, batchSize: Int): QuestionReportRetryResult =
    QuestionReportRetryResult(sent = 0, dropped = 0)

  override suspend fun getQueueStatus(): QuestionReportQueueStatus =
    QuestionReportQueueStatus(queuedCount = 1, oldestQueuedAt = null, lastAttemptAt = null)
}

private object FailingReportRepository : QuestionReportRepository {
  override suspend fun submitReport(report: QuestionReport): QuestionReportSubmitResult {
    error("network_down")
  }

  override suspend fun listReports(status: String?, limit: Int): List<QuestionReportWithId> = emptyList()

  override suspend fun listReportSummaries(limit: Int): List<QuestionReportSummary> = emptyList()

  override suspend fun listReportsForQuestion(questionId: String, limit: Int): List<QuestionReportWithId> = emptyList()

  override suspend fun getReportById(reportId: String): QuestionReportWithId? = null

  override suspend fun updateReportStatus(
    reportId: String,
    status: String,
    resolutionCode: String?,
    resolutionComment: String?,
  ) = Unit

  override suspend fun retryQueuedReports(maxAttempts: Int, batchSize: Int): QuestionReportRetryResult =
    QuestionReportRetryResult(sent = 0, dropped = 0)

  override suspend fun getQueueStatus(): QuestionReportQueueStatus =
    QuestionReportQueueStatus(queuedCount = 0, oldestQueuedAt = null, lastAttemptAt = null)
}

private class RecordingAppErrorHandler : AppErrorHandler {
  val handledErrors = mutableListOf<AppError>()
  private val historyState = MutableStateFlow<List<AppErrorEvent>>(emptyList())
  private val uiEventsFlow = MutableSharedFlow<UiErrorEvent>()
  private val analytics = MutableStateFlow(true)

  override val history: StateFlow<List<AppErrorEvent>> = historyState.asStateFlow()
  override val uiEvents: SharedFlow<UiErrorEvent> = uiEventsFlow.asSharedFlow()
  override val analyticsEnabled: StateFlow<Boolean> = analytics.asStateFlow()

  override fun handle(error: AppError) {
    handledErrors.add(error)
    historyState.value = historyState.value + AppErrorEvent(error = error, timestamp = 0L)
  }

  override fun updateAnalyticsEnabled(userOptIn: Boolean) {
    analytics.value = userOptIn
  }

  override suspend fun submitReport(event: AppErrorEvent, comment: String?): AppErrorReportResult {
    return AppErrorReportResult.Submitted
  }
}
