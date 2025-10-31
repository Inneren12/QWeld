package com.qweld.app.feature.exam.vm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.repo.UserStatsRepositoryRoom
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.model.ResumeLocaleOption
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelResumeTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  private lateinit var context: Context
  private lateinit var db: QWeldDb
  private lateinit var attemptsRepository: AttemptsRepository
  private lateinit var answersRepository: AnswersRepository
  private lateinit var statsRepository: UserStatsRepositoryRoom
  private lateinit var viewModel: ExamViewModel
  private var currentTime = 0L

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = QWeldDb.inMemory(context)
    attemptsRepository = AttemptsRepository(db.attemptDao()) { }
    answersRepository = AnswersRepository(db.answerDao())
    statsRepository = UserStatsRepositoryRoom(db.answerDao()) { }
    viewModel = createViewModel(repositoryWithQuestions(count = 2))
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun detectAndResumeRestoresAnswersAndTimer() = runTest {
    val attempt =
      AttemptEntity(
        id = TEST_ATTEMPT_ID,
        mode = ExamMode.IP_MOCK.name,
        locale = Locale.ENGLISH.language.uppercase(Locale.US),
        seed = 7L,
        questionCount = 2,
        startedAt = 0L,
      )
    db.attemptDao().insert(attempt)
    db.answerDao()
      .insertAll(
        listOf(
          AnswerEntity(
            attemptId = TEST_ATTEMPT_ID,
            displayIndex = 0,
            questionId = "Q1",
            selectedId = "Q1-A",
            correctId = "Q1-A",
            isCorrect = true,
            timeSpentSec = 30,
            seenAt = 0L,
            answeredAt = 30L,
          ),
        ),
      )
    currentTime = 3_600_000L

    viewModel.detectResume("en")
    advanceUntilIdle()

    val dialog = viewModel.uiState.value.resumeDialog
    assertNotNull(dialog)
    assertEquals(TEST_ATTEMPT_ID, dialog.attemptId)
    assertEquals(3L, dialog.remaining?.toHours())

    viewModel.resumeAttempt(TEST_ATTEMPT_ID, ResumeLocaleOption.KEEP_ORIGINAL, "en")
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.resumeDialog)
    val attemptState = viewModel.uiState.value.attempt
    assertNotNull(attemptState)
    assertEquals(ExamMode.IP_MOCK, attemptState.mode)
    assertEquals(1, attemptState.currentIndex)
    val firstQuestion = attemptState.questions.first()
    assertTrue(firstQuestion.isAnswered)

    val event = viewModel.events.first()
    assertEquals(ExamViewModel.ExamEvent.ResumeReady, event)
    val timerLabel = viewModel.uiState.value.timerLabel
    assertEquals("03:00:00", timerLabel)
  }

  private fun createViewModel(repository: AssetQuestionRepository): ExamViewModel {
    val blueprint = ExamBlueprint(
      totalQuestions = 2,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = 2)),
    )
    return ExamViewModel(
      repository = repository,
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      statsRepository = statsRepository,
      blueprintProvider = { _, _ -> blueprint },
      seedProvider = { 7L },
      attemptIdProvider = { TEST_ATTEMPT_ID },
      nowProvider = { currentTime },
      timerController = com.qweld.app.domain.exam.TimerController { },
      ioDispatcher = dispatcherRule.dispatcher,
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
    return AssetQuestionRepository(
      assetReader = AssetQuestionRepository.AssetReader(open = { questions.byteInputStream() }),
      localeResolver = { "en" },
      json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
    )
  }

  companion object {
    private const val TEST_ATTEMPT_ID = "attempt-resume"
  }
}
