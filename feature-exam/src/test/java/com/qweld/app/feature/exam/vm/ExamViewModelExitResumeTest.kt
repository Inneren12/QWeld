package com.qweld.app.feature.exam.vm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.repo.UserStatsRepositoryRoom
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.feature.exam.FakeUserPrefs
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.TestIntegrity
import com.qweld.app.feature.exam.model.ResumeLocaleOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for exit-mid-exam and resume-later functionality in ExamViewModel.
 * Covers the scenario where a user exits during an exam and later resumes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelExitResumeTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  private lateinit var context: Context
  private lateinit var db: QWeldDb
  private lateinit var attemptsRepository: AttemptsRepository
  private lateinit var answersRepository: AnswersRepository
  private lateinit var statsRepository: UserStatsRepositoryRoom
  private var currentTime = 0L

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = QWeldDb.inMemory(context)
    attemptsRepository = AttemptsRepository(db.attemptDao()) { }
    answersRepository = AnswersRepository(db.answerDao())
    statsRepository = UserStatsRepositoryRoom(db.answerDao()) { }
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun exitMidExamAndResumeLaterRestoresProgress() = runTest {
    val repository = repositoryWithQuestions(count = 3)
    val viewModel1 = createViewModel(repository)

    // Start exam and answer one question
    val launched = viewModel1.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 3, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = practiceBlueprint(3))
    assertTrue(launched)

    val firstQuestion = viewModel1.uiState.value.attempt?.currentQuestion()
    assertNotNull(firstQuestion)
    viewModel1.submitAnswer(firstQuestion.choices[0].id)

    // Navigate to second question
    viewModel1.nextQuestion()
    assertEquals(1, viewModel1.uiState.value.attempt?.currentIndex)

    // Exit (abort) the exam
    viewModel1.abortAttempt()
    advanceUntilIdle()

    // Create new ViewModel instance (simulates app restart)
    currentTime = 10_000L
    val viewModel2 = createViewModel(repository)

    // Detect resume
    viewModel2.detectResume("en")
    advanceUntilIdle()

    val resumeDialog = viewModel2.uiState.value.resumeDialog
    assertNotNull(resumeDialog)
    assertEquals(ExamMode.PRACTICE, resumeDialog.mode)

    // Resume the attempt
    viewModel2.resumeAttempt(resumeDialog.attemptId, ResumeLocaleOption.KEEP_ORIGINAL, "en")
    advanceUntilIdle()

    // Verify resume event was emitted
    val event = viewModel2.events.first()
    assertEquals(ExamViewModel.ExamEvent.ResumeReady, event)

    // Verify state was restored
    val attemptState = viewModel2.uiState.value.attempt
    assertNotNull(attemptState)
    assertEquals(ExamMode.PRACTICE, attemptState.mode)
    assertEquals(1, attemptState.currentIndex) // Should resume at second question

    // Verify first question is still answered
    val restoredFirstQuestion = attemptState.questions[0]
    assertTrue(restoredFirstQuestion.isAnswered)
    assertEquals(firstQuestion.choices[0].id, restoredFirstQuestion.selectedChoiceId)
  }

  @Test
  fun exitMidIpMockExamAndResumeWithTimer() = runTest {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel1 = createViewModel(repository)

    // Start IP_MOCK exam
    val launched = viewModel1.startAttempt(ExamMode.IP_MOCK, locale = "en")
    assertTrue(launched)

    // Answer first question
    val firstQuestion = viewModel1.uiState.value.attempt?.currentQuestion()
    assertNotNull(firstQuestion)
    currentTime = 30_000L
    viewModel1.submitAnswer(firstQuestion.choices[0].id)

    // Abort exam
    viewModel1.abortAttempt()
    advanceUntilIdle()

    // Simulate time passing (1 hour)
    currentTime = 3_630_000L

    // Create new ViewModel (app restart)
    val viewModel2 = createViewModel(repository)

    // Detect resume
    viewModel2.detectResume("en")
    advanceUntilIdle()

    val resumeDialog = viewModel2.uiState.value.resumeDialog
    assertNotNull(resumeDialog)
    assertEquals(ExamMode.IP_MOCK, resumeDialog.mode)

    // Timer should show remaining time (approximately 3 hours)
    val remaining = resumeDialog.remaining
    assertNotNull(remaining)
    assertTrue(remaining.toHours() in 2L..3L)

    // Resume the attempt
    viewModel2.resumeAttempt(resumeDialog.attemptId, ResumeLocaleOption.KEEP_ORIGINAL, "en")
    advanceUntilIdle()

    // Verify timer was resumed
    val timerLabel = viewModel2.uiState.value.timerLabel
    assertNotNull(timerLabel)
    // Timer should show approximately 3 hours remaining
  }

  @Test
  fun exitWithoutAnswersAndResume() = runTest {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel1 = createViewModel(repository)

    // Start exam but don't answer anything
    val launched = viewModel1.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 2, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = practiceBlueprint(2))
    assertTrue(launched)

    // Immediately abort
    viewModel1.abortAttempt()
    advanceUntilIdle()

    // Create new ViewModel
    currentTime = 5_000L
    val viewModel2 = createViewModel(repository)

    // Detect and resume
    viewModel2.detectResume("en")
    advanceUntilIdle()

    val resumeDialog = viewModel2.uiState.value.resumeDialog
    assertNotNull(resumeDialog)

    viewModel2.resumeAttempt(resumeDialog.attemptId, ResumeLocaleOption.KEEP_ORIGINAL, "en")
    advanceUntilIdle()

    // Should resume at first question with no answers
    val attemptState = viewModel2.uiState.value.attempt
    assertNotNull(attemptState)
    assertEquals(0, attemptState.currentIndex)
    attemptState.questions.forEach { question ->
      assertNull(question.selectedChoiceId)
    }
  }

  @Test
  fun discardResumeDialogClearsAttempt() = runTest {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel1 = createViewModel(repository)

    // Start and abort exam
    viewModel1.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = PracticeConfig(size = 2, scope = PracticeScope(taskIds = setOf("A-1"))), blueprintOverride = practiceBlueprint(2))
    viewModel1.abortAttempt()
    advanceUntilIdle()

    // Create new ViewModel and detect resume
    val viewModel2 = createViewModel(repository)
    viewModel2.detectResume("en")
    advanceUntilIdle()

    val resumeDialog = viewModel2.uiState.value.resumeDialog
    assertNotNull(resumeDialog)

    // Discard the resume
    viewModel2.discardAttempt(resumeDialog.attemptId)
    advanceUntilIdle()

    // Resume dialog should be gone
    assertNull(viewModel2.uiState.value.resumeDialog)

    // Attempt should be marked as finished
    val attempt = db.attemptDao().getById(resumeDialog.attemptId)
    assertNotNull(attempt?.finishedAt)
  }

  private fun createViewModel(repository: AssetQuestionRepository): ExamViewModel {
    val blueprint = practiceBlueprint(3)
    val questionReportRepository = object : com.qweld.app.data.reports.QuestionReportRepository {
      override suspend fun submitReport(report: com.qweld.app.data.reports.QuestionReport) {
        // No-op for tests
      }
    }
    return ExamViewModel(
      repository = repository,
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      statsRepository = statsRepository,
      userPrefs = FakeUserPrefs(),
      questionReportRepository = questionReportRepository,
      blueprintProvider = { _, _ -> blueprint },
      seedProvider = { 7L },
      attemptIdProvider = { "test-attempt-exit-resume" },
      nowProvider = { currentTime },
      timerController = com.qweld.app.domain.exam.TimerController { },
      ioDispatcher = dispatcherRule.dispatcher,
      prewarmController =
        PrewarmController(
          repository = repository,
          prewarmUseCase =
            PrewarmUseCase(
              repository = repository,
              prewarmDisabled = flowOf(false),
              ioDispatcher = dispatcherRule.dispatcher,
              nowProvider = { currentTime },
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

  private fun practiceBlueprint(size: Int): ExamBlueprint {
    return ExamBlueprint(
      totalQuestions = size,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = size)),
    )
  }
}
