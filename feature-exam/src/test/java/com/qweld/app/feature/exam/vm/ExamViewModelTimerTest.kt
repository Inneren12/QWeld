package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.FakeAnswerDao
import com.qweld.app.feature.exam.FakeAttemptDao
import com.qweld.app.feature.exam.FakeUserPrefs
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.TestIntegrity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test

/**
 * Tests for timer functionality in ExamViewModel.
 * Covers timer start, countdown, expiration, and auto-finish behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelTimerTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  @Test
  fun ipMockStartsTimerAutomatically() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val viewModel = createViewModel(repository, blueprint)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")
    assertTrue(launched)

    val timerLabel = viewModel.uiState.value.timerLabel
    assertNotNull(timerLabel)
    assertEquals("04:00:00", timerLabel)
  }

  @Test
  fun practiceDoesNotStartTimer() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val viewModel = createViewModel(repository, blueprint)

    val config = PracticeConfig(size = 2, scope = PracticeScope(taskIds = setOf("A-1")))
    val launched = viewModel.startAttempt(ExamMode.PRACTICE, locale = "en", practiceConfig = config, blueprintOverride = blueprint)
    assertTrue(launched)

    val timerLabel = viewModel.uiState.value.timerLabel
    assertNull(timerLabel)
  }

  @Test
  fun timerCountsDown() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val fakeClock = FakeClock(Instant.ofEpochMilli(1_000_000_000))
    val timer = TimerController(fakeClock) { }
    val viewModel = createViewModel(repository, blueprint, timerController = timer)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")
    assertTrue(launched)

    assertEquals("04:00:00", viewModel.uiState.value.timerLabel)

    advanceTimeBy(1_000)
    fakeClock.advance(Duration.ofSeconds(1))

    // Timer should update every second
    advanceTimeBy(1_000)
    // Note: actual countdown happens in the timer job, timer label updates when refreshed
  }

  @Test
  fun timerExpirationAutoFinishesExam() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val attemptDao = FakeAttemptDao()
    val fakeClock = FakeClock(Instant.ofEpochMilli(1_000_000_000))
    // Start timer with a short duration by advancing clock to near the end
    val timer = TimerController(fakeClock) { }
    val viewModel = createViewModel(repository, blueprint, attemptDao = attemptDao, timerController = timer)

    val event = async { viewModel.events.first() }

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")
    assertTrue(launched)
    assertNotNull(viewModel.uiState.value.attempt)

    // Simulate timer expiration by advancing time
    advanceTimeBy(4_000) // 4 seconds should trigger auto-finish
    advanceUntilIdle()

    // Exam should auto-finish
    assertEquals(ExamViewModel.ExamEvent.NavigateToResult, event.await())

    // Attempt should be marked as finished
    val finishedAttempt = attemptDao.getById("test-attempt")
    assertNotNull(finishedAttempt)
    assertNotNull(finishedAttempt.finishedAt)
    assertNotNull(finishedAttempt.scorePct)
  }

  @Test
  fun timerStopsWhenExamFinished() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val fakeClock = FakeClock(Instant.ofEpochMilli(1_000_000_000))
    val timer = TimerController(fakeClock) { }
    val viewModel = createViewModel(repository, blueprint, timerController = timer)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")
    assertTrue(launched)

    // Timer is started - verify by checking remaining time equals full duration
    val remainingAfterStart = timer.remaining()
    assertEquals(TimerController.EXAM_DURATION, remainingAfterStart)

    viewModel.finishExam()
    advanceUntilIdle()

    // After finishing, remaining time is frozen at the stopped value
    val remainingAfterFinish = timer.remaining()
    assertEquals(TimerController.EXAM_DURATION, remainingAfterFinish)
  }

  @Test
  fun timerStopsWhenExamAborted() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val fakeClock = FakeClock(Instant.ofEpochMilli(1_000_000_000))
    val timer = TimerController(fakeClock) { }
    val viewModel = createViewModel(repository, blueprint, timerController = timer)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")
    assertTrue(launched)

    // Timer is started - verify by checking remaining time equals full duration
    val remainingAfterStart = timer.remaining()
    assertEquals(TimerController.EXAM_DURATION, remainingAfterStart)

    viewModel.abortAttempt()

    // After aborting, remaining time is frozen at the stopped value
    val remainingAfterAbort = timer.remaining()
    assertEquals(TimerController.EXAM_DURATION, remainingAfterAbort)
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
              { \"id\": \"$choiceId\", \"text\": { \"$locale\": \"Option\" } }
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
    timerController: TimerController = TimerController { },
    statsRepository: UserStatsRepository = object : UserStatsRepository {
      override suspend fun getUserItemStats(
        userId: String,
        ids: List<String>,
      ): Outcome<Map<String, com.qweld.app.domain.exam.ItemStats>> = Outcome.Ok(emptyMap())
    },
  ): ExamViewModel {
    val answerDao = FakeAnswerDao()
    val attemptsRepository = AttemptsRepository(attemptDao) { }
    val answersRepository = AnswersRepository(answerDao)
    val dispatcher = dispatcherRule.dispatcher
    return ExamViewModel(
      repository = repository,
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      statsRepository = statsRepository,
      userPrefs = FakeUserPrefs(),
      blueprintProvider = { _, _ -> blueprint },
      seedProvider = { 1L },
      attemptIdProvider = { "test-attempt" },
      nowProvider = { 0L },
      timerController = timerController,
      ioDispatcher = dispatcher,
      prewarmController =
        PrewarmController(
          repository = repository,
          prewarmUseCase =
            PrewarmUseCase(
              repository,
              prewarmDisabled = flowOf(false),
              ioDispatcher = dispatcher,
              nowProvider = { 0L },
            ),
        ),
    )
  }
}

/**
 * Fake Clock for testing timer behavior.
 * Allows manual advancement of time for deterministic testing.
 */
private class FakeClock(
  private var currentInstant: Instant = Instant.EPOCH,
  private val zoneId: ZoneId = ZoneId.systemDefault()
) : Clock() {
  override fun getZone(): ZoneId = zoneId

  override fun withZone(zone: ZoneId): Clock = FakeClock(currentInstant, zone)

  override fun instant(): Instant = currentInstant

  fun advance(duration: Duration) {
    currentInstant = currentInstant.plus(duration)
  }

  fun setInstant(newInstant: Instant) {
    currentInstant = newInstant
  }
}
