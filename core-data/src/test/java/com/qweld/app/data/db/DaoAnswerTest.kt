package com.qweld.app.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DaoAnswerTest {
  private lateinit var context: Context
  private lateinit var db: QWeldDb
  private lateinit var answerDao: AnswerDao

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = QWeldDb.inMemory(context)
    answerDao = db.answerDao()
    runBlocking {
      val attempt =
        AttemptEntity(
          id = "attempt-answers",
          mode = "PRACTICE",
          locale = "EN",
          seed = 99L,
          questionCount = 3,
          startedAt = 10_000L,
        )
      db.attemptDao().insert(attempt)
    }
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun insertAllAndListByAttemptOrdersByDisplayIndex() = runTest {
    val answers =
      listOf(
        answer(displayIndex = 2, questionId = "Q2", isCorrect = false, answeredAt = 11_000L),
        answer(displayIndex = 0, questionId = "Q0", isCorrect = true, answeredAt = 12_000L),
        answer(displayIndex = 1, questionId = "Q1", isCorrect = true, answeredAt = 13_000L),
      )
    answerDao.insertAll(answers)

    val stored = answerDao.listByAttempt("attempt-answers")
    assertEquals(listOf(0, 1, 2), stored.map { it.displayIndex })
  }

  @Test
  fun countByQuestionReturnsAttemptsAndCorrect() = runTest {
    val answers =
      listOf(
        answer(displayIndex = 0, questionId = "Q1", isCorrect = true, answeredAt = 14_000L),
        answer(displayIndex = 1, questionId = "Q1", isCorrect = false, answeredAt = 15_000L),
        answer(displayIndex = 2, questionId = "Q2", isCorrect = true, answeredAt = 16_000L),
      )
    answerDao.insertAll(answers)

    val aggregate = answerDao.countByQuestion("Q1")
    assertEquals(2, aggregate?.attempts)
    assertEquals(1, aggregate?.correct)
    assertEquals(15_000L, aggregate?.lastAnsweredAt)
  }
  @Test
  fun upsertReplacesExistingAnswer() = runTest {
    val initial =
      answer(displayIndex = 0, questionId = "Q4", isCorrect = false, answeredAt = 30_000L)
    val updated =
      initial.copy(
        selectedId = "selected-updated",
        correctId = "correct-updated",
        isCorrect = true,
        timeSpentSec = 25,
        answeredAt = 31_000L,
      )
    answerDao.insertAll(listOf(initial))
    answerDao.insertAll(listOf(updated))

    val stored = answerDao.listByAttempt("attempt-answers")
    val replacement = stored.single { it.displayIndex == 0 }
    assertEquals("selected-updated", replacement.selectedId)
    assertEquals(true, replacement.isCorrect)
    assertEquals(25, replacement.timeSpentSec)
    assertEquals(31_000L, replacement.answeredAt)
  }


  @Test
  fun bulkCountByQuestionsAggregatesById() = runTest {
    val answers =
      listOf(
        answer(displayIndex = 0, questionId = "Q1", isCorrect = true, answeredAt = 20_000L),
        answer(displayIndex = 1, questionId = "Q1", isCorrect = true, answeredAt = 21_000L),
        answer(displayIndex = 2, questionId = "Q2", isCorrect = false, answeredAt = 22_000L),
        answer(displayIndex = 3, questionId = "Q3", isCorrect = true, answeredAt = 23_000L),
      )
    answerDao.insertAll(answers)

    val aggregates = answerDao.bulkCountByQuestions(listOf("Q1", "Q2", "Q3"))
    val map = aggregates.associateBy { it.questionId }
    assertEquals(2, map["Q1"]?.attempts)
    assertEquals(2, map["Q1"]?.correct)
    assertEquals(22_000L, map["Q2"]?.lastAnsweredAt)
    assertTrue(map.containsKey("Q3"))
  }

  private fun answer(
    displayIndex: Int,
    questionId: String,
    isCorrect: Boolean,
    answeredAt: Long,
  ): AnswerEntity {
    return AnswerEntity(
      attemptId = "attempt-answers",
      displayIndex = displayIndex,
      questionId = questionId,
      selectedId = "selected-$displayIndex",
      correctId = "correct-$displayIndex",
      isCorrect = isCorrect,
      timeSpentSec = 10,
      seenAt = answeredAt - 5,
      answeredAt = answeredAt,
    )
  }
}
