package com.qweld.app.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DaoClearAllTest {
  private lateinit var context: Context
  private lateinit var db: QWeldDb
  private lateinit var attemptDao: AttemptDao
  private lateinit var answerDao: AnswerDao

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = QWeldDb.inMemory(context)
    attemptDao = db.attemptDao()
    answerDao = db.answerDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun clearAllRemovesAttemptsAndAnswers() = runTest {
    val attempt =
      AttemptEntity(
        id = "attempt-clear",
        mode = "PRACTICE",
        locale = "EN",
        seed = 99L,
        questionCount = 2,
        startedAt = 1_000L,
      )
    attemptDao.insert(attempt)

    val answers =
      listOf(
        AnswerEntity(
          attemptId = attempt.id,
          displayIndex = 0,
          questionId = "q1",
          selectedId = "a1",
          correctId = "a1",
          isCorrect = true,
          timeSpentSec = 10,
          seenAt = 1_001L,
          answeredAt = 1_010L,
        ),
        AnswerEntity(
          attemptId = attempt.id,
          displayIndex = 1,
          questionId = "q2",
          selectedId = "a2",
          correctId = "a3",
          isCorrect = false,
          timeSpentSec = 12,
          seenAt = 1_011L,
          answeredAt = 1_022L,
        ),
      )
    answerDao.insertAll(answers)

    assertTrue(attemptDao.listRecent(limit = 10).isNotEmpty())
    assertTrue(answerDao.listByAttempt(attempt.id).isNotEmpty())

    answerDao.clearAll()
    assertTrue(answerDao.listByAttempt(attempt.id).isEmpty())

    attemptDao.clearAll()
    assertTrue(attemptDao.listRecent(limit = 10).isEmpty())
  }
}
