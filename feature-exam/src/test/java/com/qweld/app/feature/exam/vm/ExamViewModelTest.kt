package com.qweld.app.feature.exam.vm

import com.qweld.app.data.repo.DefaultAnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.TestIntegrity
import com.qweld.app.feature.exam.fakes.FakeAnswerDao
import com.qweld.app.feature.exam.fakes.FakeAttemptDao
import com.qweld.app.feature.exam.fakes.FakeQuestionReportRepository
import com.qweld.app.feature.exam.FakeUserPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Ignore

@OptIn(ExperimentalCoroutinesApi::class)
@Ignore("Pending ExamViewModel alignment")
class ExamViewModelTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  @Test
  fun startIpMockCreatesAttempt() {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel = createViewModel(repository)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    assertTrue(launched)
    val attempt = viewModel.uiState.value.attempt
    assertNotNull(attempt)
    assertEquals(ExamMode.IP_MOCK, attempt.mode)
    assertEquals(2, attempt.totalQuestions)
    assertEquals(0, attempt.currentIndex)
  }

  @Test
  fun practiceModeAllowsPreviousNavigation() {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel = createViewModel(repository)

    val launched = viewModel.startAttempt(ExamMode.PRACTICE, locale = "en")

    assertTrue(launched)
    viewModel.nextQuestion()
    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex)
    viewModel.previousQuestion()
    assertEquals(0, viewModel.uiState.value.attempt?.currentIndex)
  }

  @Test
  fun ipMockDisablesPreviousNavigation() {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel = createViewModel(repository)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    assertTrue(launched)
    viewModel.nextQuestion()
    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex)
    viewModel.previousQuestion()
    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex)
  }

  @Test
  fun submitAnswerLocksChoice() {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel = createViewModel(repository)

    val launched = viewModel.startAttempt(ExamMode.PRACTICE, locale = "en")

    assertTrue(launched)
    val firstQuestion = viewModel.uiState.value.attempt?.currentQuestion()
    val initialChoice = firstQuestion?.choices?.firstOrNull()
    requireNotNull(initialChoice)

    viewModel.submitAnswer(initialChoice.id)
    var updatedQuestion = viewModel.uiState.value.attempt?.currentQuestion()
    assertTrue(updatedQuestion?.isAnswered == true)
    assertEquals(initialChoice.id, updatedQuestion?.selectedChoiceId)

    val alternativeChoice = updatedQuestion?.choices?.getOrNull(1)
    if (alternativeChoice != null) {
      viewModel.submitAnswer(alternativeChoice.id)
    }
    updatedQuestion = viewModel.uiState.value.attempt?.currentQuestion()
    assertEquals(initialChoice.id, updatedQuestion?.selectedChoiceId)
  }

  @Test
  fun startAttemptShowsDeficitDialogWhenBankSmall() {
    val repository = repositoryWithQuestions(count = 1)
    val viewModel = createViewModel(repository)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    assertFalse(launched)
    assertNull(viewModel.uiState.value.attempt)
    val dialog = viewModel.uiState.value.deficitDialog
    assertNotNull(dialog)
    val detail = dialog.details.first()
    assertEquals("A-1", detail.taskId)
    assertEquals(2, detail.need)
    assertEquals(1, detail.have)
  }

  @Test
  fun practiceUsesPresetSizeFromConfig() {
    val repository = repositoryWithQuestions(count = 30)
    var capturedSize: Int? = null
    val viewModel =
      createViewModel(
        repository,
        blueprintProvider = { mode, size ->
          capturedSize = size
          ExamBlueprint(
            totalQuestions = size,
            taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = size)),
          )
        },
      )

    val config = PracticeConfig(size = 10)
    val launched = viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = config)

    assertTrue(launched)
    assertEquals(config.size, capturedSize)
  }

  private fun createViewModel(repository: AssetQuestionRepository): ExamViewModel {
    val blueprint = ExamBlueprint(
      totalQuestions = 2,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = 2)),
    )
    return createViewModel(repository, blueprintProvider = { _, _ -> blueprint })
  }

  private fun createViewModel(
    repository: AssetQuestionRepository,
    blueprintProvider: (ExamMode, Int) -> ExamBlueprint,
  ): ExamViewModel {
    val attemptDao = FakeAttemptDao()
    val answerDao = FakeAnswerDao()
    val attemptsRepository = AttemptsRepository(attemptDao) { }
    val answersRepository = DefaultAnswersRepository(answerDao)
    val statsRepository = object : UserStatsRepository {
      override suspend fun getUserItemStats(
        userId: String,
        ids: List<String>,
      ): Outcome<Map<String, com.qweld.app.domain.exam.ItemStats>> = Outcome.Ok(emptyMap())
    }
    val questionReportRepository = FakeQuestionReportRepository()
    val dispatcher = StandardTestDispatcher()
    val blueprint = ExamBlueprint(
      totalQuestions = 2,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = 2)),
    )
    val blueprintResolver = createTestBlueprintResolver(blueprint)
    val resumeUseCase = createTestResumeUseCase(repository, statsRepository, blueprint, dispatcher)
    return ExamViewModel(
      repository = repository,
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      statsRepository = statsRepository,
      userPrefs = FakeUserPrefs(),
      questionReportRepository = questionReportRepository,
      appEnv = com.qweld.app.feature.exam.vm.fakes.FakeAppEnv(),
      appErrorHandler = null,
      blueprintResolver = blueprintResolver,
      timerController = com.qweld.app.domain.exam.TimerController { },
      prewarmRunner =
        DefaultPrewarmController(
          repository = repository,
          prewarmUseCase =
            PrewarmUseCase(
              repository,
              prewarmDisabled = flowOf(false),
              ioDispatcher = dispatcher,
              nowProvider = { 0L },
            ),
        ),
      resumeUseCase = resumeUseCase,
      ioDispatcher = dispatcher,
      seedProvider = { 1L },
      userIdProvider = { "test-user" },
      attemptIdProvider = { "test-attempt" },
      nowProvider = { 0L },
      timerCoordinatorOverride = null,
      prewarmCoordinatorOverride = null,
      autosaveCoordinatorOverride = null,
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
