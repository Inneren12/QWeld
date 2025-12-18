package com.qweld.app.feature.exam.vm

import com.qweld.app.data.repo.DefaultAnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.FakeUserPrefs
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.TestIntegrity
import com.qweld.app.feature.exam.fakes.FakeAnswerDao
import com.qweld.app.feature.exam.fakes.FakeAttemptDao
import com.qweld.app.feature.exam.fakes.FakeQuestionReportRepository
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.Ignore

/**
 * Tests for navigation functionality in ExamViewModel.
 * Covers next/previous navigation, boundary conditions, and mode-specific restrictions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Ignore("Pending ExamViewModel alignment")
class ExamViewModelNavigationTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  @Test
  fun nextQuestionAdvancesToSecondQuestion() {
    val repository = repositoryWithTasks("A-1" to 3)
    val blueprint = blueprint(required = 3)
    val viewModel = createViewModel(repository, blueprint)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 3, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    assertEquals(0, viewModel.uiState.value.attempt?.currentIndex)

    viewModel.nextQuestion()

    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex)
  }

  @Test
  fun nextQuestionAtLastQuestionDoesNotAdvance() {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val viewModel = createViewModel(repository, blueprint)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 2, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    viewModel.nextQuestion()
    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex)

    viewModel.nextQuestion()
    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex) // Should stay at last question
  }

  @Test
  fun previousQuestionGoesBackInPracticeMode() {
    val repository = repositoryWithTasks("A-1" to 3)
    val blueprint = blueprint(required = 3)
    val viewModel = createViewModel(repository, blueprint)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 3, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    viewModel.nextQuestion()
    viewModel.nextQuestion()
    assertEquals(2, viewModel.uiState.value.attempt?.currentIndex)

    viewModel.previousQuestion()
    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex)

    viewModel.previousQuestion()
    assertEquals(0, viewModel.uiState.value.attempt?.currentIndex)
  }

  @Test
  fun previousQuestionAtFirstQuestionDoesNotGoBack() {
    val repository = repositoryWithTasks("A-1" to 3)
    val blueprint = blueprint(required = 3)
    val viewModel = createViewModel(repository, blueprint)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 3, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    assertEquals(0, viewModel.uiState.value.attempt?.currentIndex)

    viewModel.previousQuestion()

    assertEquals(0, viewModel.uiState.value.attempt?.currentIndex) // Should stay at first question
  }

  @Test
  fun previousQuestionDisabledInIpMockMode() {
    val repository = repositoryWithTasks("A-1" to 3)
    val blueprint = blueprint(required = 3)
    val viewModel = createViewModel(repository, blueprint)

    viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    viewModel.nextQuestion()
    viewModel.nextQuestion()
    assertEquals(2, viewModel.uiState.value.attempt?.currentIndex)

    viewModel.previousQuestion()

    assertEquals(2, viewModel.uiState.value.attempt?.currentIndex) // Should not go back in IP_MOCK
  }

  @Test
  fun navigationWithAnsweredQuestions() {
    val repository = repositoryWithTasks("A-1" to 3)
    val blueprint = blueprint(required = 3)
    val viewModel = createViewModel(repository, blueprint)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 3, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    // Answer first question
    val firstQuestion = viewModel.uiState.value.attempt?.currentQuestion()
    val firstChoice = firstQuestion?.choices?.first()
    if (firstChoice != null) {
      viewModel.submitAnswer(firstChoice.id)
    }
    assertTrue(viewModel.uiState.value.attempt?.currentQuestion()?.isAnswered == true)

    // Navigate to second question
    viewModel.nextQuestion()
    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex)

    // Navigate back to first question
    viewModel.previousQuestion()
    assertEquals(0, viewModel.uiState.value.attempt?.currentIndex)

    // First question should still be answered
    assertTrue(viewModel.uiState.value.attempt?.currentQuestion()?.isAnswered == true)
  }

  @Test
  fun navigationAcrossAllQuestionsInSequence() {
    val repository = repositoryWithTasks("A-1" to 5)
    val blueprint = blueprint(required = 5)
    val viewModel = createViewModel(repository, blueprint)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 5, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    // Navigate forward through all questions
    for (expectedIndex in 0..4) {
      assertEquals(expectedIndex, viewModel.uiState.value.attempt?.currentIndex)
      if (expectedIndex < 4) {
        viewModel.nextQuestion()
      }
    }

    // Navigate backward through all questions
    for (expectedIndex in 4 downTo 0) {
      assertEquals(expectedIndex, viewModel.uiState.value.attempt?.currentIndex)
      if (expectedIndex > 0) {
        viewModel.previousQuestion()
      }
    }
  }

  private fun repositoryWithTasks(
    vararg counts: Pair<String, Int>,
    locale: String = "en",
  ): AssetQuestionRepository {
    val payloads = counts.associate { (taskId, count) ->
      val json = questionArray(taskId, count, locale)
      "questions/$locale/tasks/$taskId.json" to json
    }
    val assets = TestIntegrity.addIndexes(payloads.mapValues { it.value.toByteArray() })
    return AssetQuestionRepository(
      assetReader = AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
      localeResolver = { locale },
      json = Json { ignoreUnknownKeys = true },
    )
  }

  private fun questionArray(taskId: String, count: Int, locale: String): String {
    return buildString {
      append('[')
      repeat(count) { index ->
        if (index > 0) append(',')
        val id = "Q-${taskId}-${index + 1}"
        val blockId = taskId.substringBefore('-', taskId)
        val choiceId = "${id}-A"
        append(
          """
          {
            \"id\": \"$id\",
            \"taskId\": \"$taskId\",
            \"blockId\": \"$blockId\",
            \"locale\": \"$locale\",
            \"stem\": { \"$locale\": \"Stem $id\" },
            \"choices\": [
              { \"id\": \"$choiceId\", \"text\": { \"$locale\": \"Option A\" } },
              { \"id\": \"${id}-B\", \"text\": { \"$locale\": \"Option B\" } }
            ],
            \"correctId\": \"$choiceId\"
          }
          """.trimIndent(),
        )
      }
      append(']')
    }
  }

  private fun blueprint(required: Int): ExamBlueprint {
    return ExamBlueprint(
      totalQuestions = required,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = required)),
    )
  }

  private fun createViewModel(
    repository: AssetQuestionRepository,
    blueprint: ExamBlueprint,
    statsRepository: UserStatsRepository = object : UserStatsRepository {
      override suspend fun getUserItemStats(
        userId: String,
        ids: List<String>,
      ): Outcome<Map<String, com.qweld.app.domain.exam.ItemStats>> = Outcome.Ok(emptyMap())
    },
  ): ExamViewModel {
    val attemptDao = FakeAttemptDao()
    val answerDao = FakeAnswerDao()
    val attemptsRepository = AttemptsRepository(attemptDao) { }
    val answersRepository = DefaultAnswersRepository(answerDao)
    val questionReportRepository = FakeQuestionReportRepository()
    val dispatcher = dispatcherRule.dispatcher
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
}
