package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.repo.AnswersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract tests for ExamAutosaveController.
 *
 * These tests verify that the DefaultExamAutosaveController correctly implements
 * the ExamAutosaveController contract and properly manages autosave lifecycle.
 * This is a regression test ensuring that the autosave controller extracted from
 * ExamViewModel maintains correct behavior after DI introduction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExamAutosaveControllerTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var testScope: TestScope
  private lateinit var fakeAnswersRepository: FakeAnswersRepository
  private lateinit var fakeAutosaveFactory: FakeAutosaveFactory
  private lateinit var controller: ExamAutosaveController

  @Before
  fun setup() {
    testScope = TestScope(testDispatcher)
    fakeAnswersRepository = FakeAnswersRepository()
    fakeAutosaveFactory = FakeAutosaveFactory()

    controller = DefaultExamAutosaveController(
      answersRepository = fakeAnswersRepository,
      scope = testScope,
      ioDispatcher = testDispatcher,
      autosaveIntervalSec = 10,
      autosaveFactory = fakeAutosaveFactory::create
    )
  }

  @Test
  fun `prepare creates and configures AutosaveController`() = testScope.runTest {
    // When
    controller.prepare("attempt-123")

    // Then
    assertTrue(fakeAutosaveFactory.created, "AutosaveController should be created")
    assertEquals("attempt-123", fakeAutosaveFactory.lastAttemptId, "Should use correct attempt ID")
    assertNotNull(fakeAutosaveFactory.lastController, "Controller should be stored")
    assertTrue(fakeAutosaveFactory.lastController!!.configured, "Controller should be configured")
  }

  @Test
  fun `recordAnswer delegates to AutosaveController when prepared`() = testScope.runTest {
    // Given
    controller.prepare("attempt-123")
    val fakeController = fakeAutosaveFactory.lastController!!
    val answer = createAnswer("q1", "choice-a")

    // When
    controller.recordAnswer(answer)
    advanceUntilIdle()

    // Then
    assertEquals(1, fakeController.answerRecorded, "Answer should be recorded")
  }

  @Test
  fun `recordAnswer saves directly to repository when not prepared`() = testScope.runTest {
    // Given - not prepared, no AutosaveController
    val answer = createAnswer("q1", "choice-a")

    // When
    controller.recordAnswer(answer)
    advanceUntilIdle()

    // Then
    assertEquals(1, fakeAnswersRepository.upsertedAnswers.size, "Should save directly to repository")
    assertEquals(answer, fakeAnswersRepository.upsertedAnswers.first(), "Should save the correct answer")
  }

  @Test
  fun `flush delegates to AutosaveController`() = testScope.runTest {
    // Given
    controller.prepare("attempt-123")
    val fakeController = fakeAutosaveFactory.lastController!!

    // When
    controller.flush(force = true)

    // Then
    assertTrue(fakeController.flushed, "Should flush AutosaveController")
  }

  @Test
  fun `stop flushes and clears AutosaveController`() = testScope.runTest {
    // Given
    controller.prepare("attempt-123")
    val fakeController = fakeAutosaveFactory.lastController!!

    // When
    controller.stop()

    // Then
    assertTrue(fakeController.flushed, "Should flush before stopping")
  }

  @Test
  fun `prepare stops previous AutosaveController before creating new one`() = testScope.runTest {
    // Given - prepare first controller
    controller.prepare("attempt-1")
    val firstController = fakeAutosaveFactory.lastController!!

    // When - prepare second controller
    controller.prepare("attempt-2")
    val secondController = fakeAutosaveFactory.lastController!!

    // Then
    assertTrue(firstController.flushed, "First controller should be flushed")
    assertFalse(secondController === firstController, "Should create new controller")
    assertEquals("attempt-2", fakeAutosaveFactory.lastAttemptId, "Should use new attempt ID")
  }

  @Test
  fun `autosave ticker triggers onTick at configured interval`() = testScope.runTest {
    // Given
    controller.prepare("attempt-123")
    val fakeController = fakeAutosaveFactory.lastController!!

    // When - advance time by interval (10 seconds)
    advanceTimeBy(10_000)

    // Then
    assertTrue(fakeController.tickCount >= 1, "Should trigger at least one tick after interval")

    // When - advance another interval
    advanceTimeBy(10_000)

    // Then
    assertTrue(fakeController.tickCount >= 2, "Should trigger another tick")
  }

  @Test
  fun `stop cancels autosave ticker`() = testScope.runTest {
    // Given
    controller.prepare("attempt-123")
    val fakeController = fakeAutosaveFactory.lastController!!
    advanceTimeBy(10_000)
    val ticksBeforeStop = fakeController.tickCount

    // When
    controller.stop()
    advanceTimeBy(20_000) // Try to trigger more ticks

    // Then
    assertEquals(ticksBeforeStop, fakeController.tickCount, "Should not tick after stop")
  }

  // Helper to create test answer entity
  private fun createAnswer(questionId: String, choiceId: String): AnswerEntity {
    return AnswerEntity(
      attemptId = "attempt-123",
      displayIndex = 0,
      questionId = questionId,
      selectedId = choiceId,
      correctId = "choice-correct",
      isCorrect = choiceId == "choice-correct",
      timeSpentSec = 10,
      seenAt = System.currentTimeMillis(),
      answeredAt = System.currentTimeMillis()
    )
  }

  // Fake AnswersRepository
  private class FakeAnswersRepository : AnswersRepository {
    val upsertedAnswers = mutableListOf<AnswerEntity>()

    override suspend fun upsert(answers: List<AnswerEntity>) {
      upsertedAnswers.addAll(answers)
    }

    override suspend fun listByAttempt(attemptId: String): List<AnswerEntity> = emptyList()
    override suspend fun listWrongByAttempt(attemptId: String): List<String> = emptyList()
    override suspend fun countByQuestion(questionId: String): AnswerDao.QuestionAggregate? = null
    override suspend fun bulkCountByQuestions(questionIds: List<String>): List<AnswerDao.QuestionAggregate> {
      return questionIds.map { questionId ->
        AnswerDao.QuestionAggregate(
          questionId = questionId,
          attempts = 0,
          correct = 0,
          lastAnsweredAt = null,
          lastIsCorrect = null
        )
      }
    }
    override suspend fun countAll(): Int = 0
    override suspend fun clearAll() {}
  }

  // Fake AutosaveController
  private class FakeAutosaveController(val attemptId: String) : AutosaveController(
    attemptId = attemptId,
    answersRepository = object : AnswersRepository {
      override suspend fun upsert(answers: List<AnswerEntity>) {}
      override suspend fun listByAttempt(attemptId: String): List<AnswerEntity> = emptyList()
      override suspend fun listWrongByAttempt(attemptId: String): List<String> = emptyList()
      override suspend fun countByQuestion(questionId: String): AnswerDao.QuestionAggregate? = null
      override suspend fun bulkCountByQuestions(questionIds: List<String>): List<AnswerDao.QuestionAggregate> {
        return questionIds.map { questionId ->
          AnswerDao.QuestionAggregate(
            questionId = questionId,
            attempts = 0,
            correct = 0,
            lastAnsweredAt = null,
            lastIsCorrect = null
          )
        }
      }
      override suspend fun countAll(): Int = 0
      override suspend fun clearAll() {}
    },
    scope = TestScope(),
    ioDispatcher = StandardTestDispatcher()
  ) {
    var configured = false
    var answerRecorded = 0
    var tickCount = 0
    var flushed = false

    override fun configure(intervalSec: Int) {
      configured = true
    }

    override fun onAnswer(
      questionId: String,
      choiceId: String,
      correctChoiceId: String,
      isCorrect: Boolean,
      displayIndex: Int,
      timeSpentSec: Int,
      seenAt: Long,
      answeredAt: Long
    ) {
      answerRecorded++
    }

    override fun onTick() {
      tickCount++
    }

    override fun flush(force: Boolean) {
      flushed = true
    }
  }

  // Fake factory to track controller creation
  private class FakeAutosaveFactory {
    var created = false
    var lastAttemptId: String? = null
    var lastController: FakeAutosaveController? = null

    fun create(attemptId: String): AutosaveController {
      created = true
      lastAttemptId = attemptId
      lastController = FakeAutosaveController(attemptId)
      return lastController!!
    }
  }
}
