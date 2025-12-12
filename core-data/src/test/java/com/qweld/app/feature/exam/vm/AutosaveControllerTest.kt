package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.repo.AnswersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutosaveControllerTest {

  @Test
  fun `onAnswer writes immediately`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dao = FakeAnswerDao()
    val repository = AnswersRepository(dao)
    val controller = AutosaveController("attempt", repository, this, dispatcher)

    controller.onAnswer(
      questionId = "q1",
      choiceId = "c1",
      correctChoiceId = "c1",
      isCorrect = true,
      displayIndex = 0,
      timeSpentSec = 12,
      seenAt = 1_000L,
      answeredAt = 2_000L,
    )
    advanceUntilIdle()

    assertEquals(1, dao.calls.size)
    val stored = dao.calls.single()
    assertEquals("q1", stored.single().questionId)
    assertEquals(12, stored.single().timeSpentSec)
  }

  @Test
  fun `onTick batches dirty answers`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dao = FakeAnswerDao(mutableListOf(true, true, false))
    val repository = AnswersRepository(dao)
    val controller = AutosaveController("attempt", repository, this, dispatcher)

    controller.onAnswer(
      questionId = "q1",
      choiceId = "c1",
      correctChoiceId = "c1",
      isCorrect = true,
      displayIndex = 0,
      timeSpentSec = 5,
      seenAt = 1_000L,
      answeredAt = 2_000L,
    )
    controller.onAnswer(
      questionId = "q2",
      choiceId = "c2",
      correctChoiceId = "c2",
      isCorrect = false,
      displayIndex = 1,
      timeSpentSec = 6,
      seenAt = 2_000L,
      answeredAt = 3_000L,
    )
    advanceUntilIdle()
    // First two immediate writes failed, so nothing stored yet.
    assertEquals(0, dao.calls.size)

    controller.onTick()
    advanceUntilIdle()

    assertEquals(1, dao.calls.size)
    val stored = dao.calls.single()
    assertEquals(listOf("q1", "q2"), stored.map { it.questionId })
    assertTrue(stored.all { it.timeSpentSec > 0 })
  }

  @Test
  fun `flush forced writes even when clean`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dao = FakeAnswerDao()
    val repository = AnswersRepository(dao)
    val controller = AutosaveController("attempt", repository, this, dispatcher)

    controller.onAnswer(
      questionId = "q1",
      choiceId = "c1",
      correctChoiceId = "c1",
      isCorrect = true,
      displayIndex = 0,
      timeSpentSec = 8,
      seenAt = 1_000L,
      answeredAt = 2_000L,
    )
    controller.onAnswer(
      questionId = "q2",
      choiceId = "c2",
      correctChoiceId = "c2",
      isCorrect = false,
      displayIndex = 1,
      timeSpentSec = 9,
      seenAt = 2_000L,
      answeredAt = 3_000L,
    )
    advanceUntilIdle()
    // Two immediate writes
    assertEquals(2, dao.calls.size)

    controller.flush(force = false)
    advanceUntilIdle()
    // No dirty answers so no additional writes
    assertEquals(2, dao.calls.size)

    controller.flush(force = true)
    advanceUntilIdle()

    assertEquals(3, dao.calls.size)
    val forced = dao.calls.last()
    assertEquals(setOf("q1", "q2"), forced.map { it.questionId }.toSet())
  }

    private class FakeAnswerDao(
        private val failureSequence: MutableList<Boolean> = mutableListOf(),
        ) : AnswerDao {
            val calls = mutableListOf<List<AnswerEntity>>()

        override suspend fun insertAll(answers: List<AnswerEntity>) {
            val shouldFail = if (failureSequence.isNotEmpty()) failureSequence.removeAt(0) else false
            if (shouldFail) {
                throw RuntimeException("simulated failure")
            }
            calls += answers.map { it.copy() }
        }

        override suspend fun listByAttempt(attemptId: String): List<AnswerEntity> =
            throw UnsupportedOperationException()

        override suspend fun countByQuestion(questionId: String): AnswerDao.QuestionAggregate? =
            throw UnsupportedOperationException()

        override suspend fun bulkCountByQuestions(questionIds: List<String>): List<AnswerDao.QuestionAggregate> =
            throw UnsupportedOperationException()

        override suspend fun countAll(): Int =
            calls.sumOf { it.size }

        override suspend fun listWrongByAttempt(attemptId: String): List<String> =
            emptyList() // 👈 добавили новый метод

        override suspend fun clearAll() =
            throw UnsupportedOperationException()
    }
}
