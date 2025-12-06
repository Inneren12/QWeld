package com.qweld.app.feature.exam.vm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.repo.UserStatsRepositoryRoom
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.feature.exam.FakeUserPrefs
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.TestIntegrity
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelPersistTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  private lateinit var context: Context
  private lateinit var db: QWeldDb
  private lateinit var attemptsRepository: AttemptsRepository
  private lateinit var answersRepository: AnswersRepository
  private lateinit var statsRepository: UserStatsRepositoryRoom
  private lateinit var viewModel: ExamViewModel
  private var currentTime = 1_000L

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = QWeldDb.inMemory(context)
    attemptsRepository = AttemptsRepository(db.attemptDao()) { }
    answersRepository = AnswersRepository(db.answerDao())
    statsRepository = UserStatsRepositoryRoom(db.answerDao()) { }
    viewModel = createViewModel(repositoryWithQuestions(count = 1))
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun persistsAttemptAnswersAndFinishMetadata() = runTest {
    val launched = viewModel.startAttempt(ExamMode.PRACTICE, locale = "en")
    assertTrue(launched)
    advanceUntilIdle()

    val storedAttempt = requireNotNull(db.attemptDao().getById(TEST_ATTEMPT_ID))
    assertEquals(TEST_ATTEMPT_ID, storedAttempt.id)
    assertEquals("EN", storedAttempt.locale)
    assertEquals(1, storedAttempt.questionCount)
    assertEquals(1_000L, storedAttempt.startedAt)
    assertNull(storedAttempt.finishedAt)

    val attemptQuestion = checkNotNull(viewModel.uiState.value.attempt?.currentQuestion())
    val choice = attemptQuestion.choices.first()
    currentTime += 4_000L
    viewModel.submitAnswer(choice.id)
    advanceUntilIdle()

    val answers = db.answerDao().listByAttempt(TEST_ATTEMPT_ID)
    assertEquals(1, answers.size)
    val answer = answers.first()
    assertEquals(TEST_ATTEMPT_ID, answer.attemptId)
    assertEquals(attemptQuestion.id, answer.questionId)
    assertTrue(answer.isCorrect)
    assertEquals(4, answer.timeSpentSec)
    assertEquals(1_000L, answer.seenAt)
    assertEquals(5_000L, answer.answeredAt)

    currentTime += 6_000L
    viewModel.finishExam()
    advanceUntilIdle()

    val finishedAttempt = requireNotNull(db.attemptDao().getById(TEST_ATTEMPT_ID))
    assertEquals(11_000L, finishedAttempt.finishedAt)
    assertEquals(10, finishedAttempt.durationSec)
    assertNull(finishedAttempt.passThreshold)
    assertEquals(100.0, finishedAttempt.scorePct)

    val statsOutcome = statsRepository.getUserItemStats("local_user", listOf(attemptQuestion.id))
    require(statsOutcome is Outcome.Ok)
    val stats = statsOutcome.value
    val questionStats = stats[attemptQuestion.id]
    assertNotNull(questionStats)
    assertEquals(1, questionStats.attempts)
    assertEquals(1, questionStats.correct)
    val lastAnsweredAt = questionStats.lastAnsweredAt
    assertNotNull(lastAnsweredAt)
    assertEquals(5_000L, lastAnsweredAt.toEpochMilli())
  }

  private fun createViewModel(repository: AssetQuestionRepository): ExamViewModel {
    val blueprint = ExamBlueprint(
      totalQuestions = 1,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = 1)),
    )
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
      seedProvider = { 1L },
      attemptIdProvider = { TEST_ATTEMPT_ID },
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

  companion object {
    private const val TEST_ATTEMPT_ID = "attempt-test"
  }
}
