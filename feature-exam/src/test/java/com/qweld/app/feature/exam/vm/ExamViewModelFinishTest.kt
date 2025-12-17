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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for exam finishing functionality in ExamViewModel.
 * Covers score calculation, result persistence, and finish behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Ignore("Pending ExamViewModel alignment")
class ExamViewModelFinishTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  @Test
  fun finishExamCalculatesCorrectScore() = runTest {
    val repository = repositoryWithTasks("A-1" to 3)
    val blueprint = blueprint(required = 3)
    val attemptDao = FakeAttemptDao()
    val viewModel = createViewModel(repository, blueprint, attemptDao = attemptDao)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 3, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    val questions = viewModel.uiState.value.attempt?.questions ?: emptyList()

    // Answer 2 out of 3 correctly
    viewModel.submitAnswer(questions[0].choices[0].id) // Correct (first choice is always correct in test data)
    viewModel.nextQuestion()
    viewModel.submitAnswer(questions[1].choices[1].id) // Incorrect (second choice is wrong)
    viewModel.nextQuestion()
    viewModel.submitAnswer(questions[2].choices[0].id) // Correct

    viewModel.finishExam()
    advanceUntilIdle()

    val finishedAttempt = attemptDao.getById("test-attempt")
    assertNotNull(finishedAttempt)
    assertNotNull(finishedAttempt.finishedAt)

    // Score should be 66.67% (2/3 correct)
    val score = finishedAttempt.scorePct
    assertNotNull(score)
    assertTrue(score in 66.0..67.0)
  }

  @Test
  fun finishExamWithAllCorrectAnswers() = runTest {
    val repository = repositoryWithTasks("A-1" to 3)
    val blueprint = blueprint(required = 3)
    val attemptDao = FakeAttemptDao()
    val viewModel = createViewModel(repository, blueprint, attemptDao = attemptDao)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 3, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    val questions = viewModel.uiState.value.attempt?.questions ?: emptyList()

    // Answer all correctly
    questions.forEach { question ->
      viewModel.submitAnswer(question.choices[0].id) // First choice is always correct
      if (question != questions.last()) {
        viewModel.nextQuestion()
      }
    }

    viewModel.finishExam()
    advanceUntilIdle()

    val finishedAttempt = attemptDao.getById("test-attempt")
    assertNotNull(finishedAttempt)
    assertEquals(100.0, finishedAttempt.scorePct)
  }

  @Test
  fun finishExamWithNoAnswers() = runTest {
    val repository = repositoryWithTasks("A-1" to 3)
    val blueprint = blueprint(required = 3)
    val attemptDao = FakeAttemptDao()
    val viewModel = createViewModel(repository, blueprint, attemptDao = attemptDao)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 3, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    // Don't answer any questions
    viewModel.finishExam()
    advanceUntilIdle()

    val finishedAttempt = attemptDao.getById("test-attempt")
    assertNotNull(finishedAttempt)
    assertEquals(0.0, finishedAttempt.scorePct)
  }

  @Test
  fun finishExamEmitsNavigateEvent() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val viewModel = createViewModel(repository, blueprint)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 2, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    val event = async { viewModel.events.first() }

    viewModel.finishExam()
    advanceUntilIdle()

    assertEquals(ExamViewModel.ExamEvent.NavigateToResult, event.await())
  }

  @Test
  fun finishExamSetsPassThresholdForIpMock() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val attemptDao = FakeAttemptDao()
    val viewModel = createViewModel(repository, blueprint, attemptDao = attemptDao)

    viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    val questions = viewModel.uiState.value.attempt?.questions ?: emptyList()
    questions.forEach { question ->
      viewModel.submitAnswer(question.choices[0].id)
      if (question != questions.last()) {
        viewModel.nextQuestion()
      }
    }

    viewModel.finishExam()
    advanceUntilIdle()

    val finishedAttempt = attemptDao.getById("test-attempt")
    assertNotNull(finishedAttempt)
    assertEquals(70, finishedAttempt.passThreshold) // IP_MOCK pass threshold is 70
  }

  @Test
  fun finishExamDoesNotSetPassThresholdForPractice() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val attemptDao = FakeAttemptDao()
    val viewModel = createViewModel(repository, blueprint, attemptDao = attemptDao)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 2, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    viewModel.finishExam()
    advanceUntilIdle()

    val finishedAttempt = attemptDao.getById("test-attempt")
    assertNotNull(finishedAttempt)
    assertNull(finishedAttempt.passThreshold) // Practice should not have pass threshold
  }

  @Test
  fun cannotFinishExamTwice() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val attemptDao = FakeAttemptDao()
    var currentTime = 1000L
    val viewModel = createViewModel(repository, blueprint, attemptDao = attemptDao, nowProvider = { currentTime })

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 2, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    viewModel.finishExam()
    advanceUntilIdle()

    val firstFinish = attemptDao.getById("test-attempt")
    val firstFinishTime = firstFinish?.finishedAt

    currentTime = 2000L
    viewModel.finishExam()
    advanceUntilIdle()

    val secondFinish = attemptDao.getById("test-attempt")
    assertEquals(firstFinishTime, secondFinish?.finishedAt) // Should not update finish time
  }

  @Test
  fun requireLatestResultReturnsResultAfterFinish() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val viewModel = createViewModel(repository, blueprint)

    viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 2, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = blueprint)

    viewModel.finishExam()
    advanceUntilIdle()

    val result = viewModel.requireLatestResult()
    assertNotNull(result)
    assertEquals("test-attempt", result.attemptId)
    assertEquals(0.0, result.scorePercent)
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
              { \"id\": \"$choiceId\", \"text\": { \"$locale\": \"Correct Option\" } },
              { \"id\": \"${id}-B\", \"text\": { \"$locale\": \"Wrong Option\" } }
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
    attemptDao: FakeAttemptDao = FakeAttemptDao(),
    nowProvider: () -> Long = { 0L },
    statsRepository: UserStatsRepository = object : UserStatsRepository {
      override suspend fun getUserItemStats(
        userId: String,
        ids: List<String>,
      ): Outcome<Map<String, com.qweld.app.domain.exam.ItemStats>> = Outcome.Ok(emptyMap())
    },
  ): ExamViewModel {
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
      blueprintResolver = blueprintResolver,
      resumeUseCase = resumeUseCase,
      seedProvider = { 1L },
      attemptIdProvider = { "test-attempt" },
      nowProvider = nowProvider,
      timerController = com.qweld.app.domain.exam.TimerController { },
      ioDispatcher = dispatcher,
      prewarmRunner =
        DefaultPrewarmController(
          repository = repository,
          prewarmUseCase =
            PrewarmUseCase(
              repository,
              prewarmDisabled = flowOf(false),
              ioDispatcher = dispatcher,
              nowProvider = nowProvider,
            ),
        ),
    )
  }
}
