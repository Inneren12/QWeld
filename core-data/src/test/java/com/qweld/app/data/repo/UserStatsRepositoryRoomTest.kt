package com.qweld.app.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserStatsRepositoryRoomTest {
  private lateinit var context: Context
  private lateinit var db: QWeldDb
  private lateinit var answerDao: AnswerDao
  private lateinit var repository: UserStatsRepositoryRoom

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = QWeldDb.inMemory(context)
    answerDao = db.answerDao()
    repository = UserStatsRepositoryRoom(answerDao) { }
    runBlocking {
      db.attemptDao().insert(
        AttemptEntity(
          id = "attempt-1",
          mode = "PRACTICE",
          locale = "EN",
          seed = 7L,
          questionCount = 4,
          startedAt = 50_000L,
        )
      )
    }
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun aggregatesStatsPerQuestion() = runTest {
    val answers =
      listOf(
        answer(displayIndex = 0, questionId = "Q1", isCorrect = true, answeredAt = 60_000L),
        answer(displayIndex = 1, questionId = "Q1", isCorrect = false, answeredAt = 61_000L),
        answer(displayIndex = 2, questionId = "Q2", isCorrect = true, answeredAt = 62_000L),
      )
    answerDao.insertAll(answers)

    val stats = repository.getUserItemStats("user-1", listOf("Q1", "Q2", "Q1", "Q3"))
    val q1 = stats["Q1"]
    assertNotNull(q1)
    assertEquals(2, q1?.attempts)
    assertEquals(1, q1?.correct)
    assertEquals(61_000L, q1?.lastAnsweredAt?.toEpochMilli())
    assertEquals(false, q1?.lastAnswerCorrect)

    val q2 = stats["Q2"]
    assertEquals(1, q2?.attempts)
    assertEquals(1, q2?.correct)
    assertEquals(62_000L, q2?.lastAnsweredAt?.toEpochMilli())
    assertEquals(true, q2?.lastAnswerCorrect)

    assertTrue("Q3" !in stats)
  }

  private fun answer(
    displayIndex: Int,
    questionId: String,
    isCorrect: Boolean,
    answeredAt: Long,
  ): AnswerEntity {
    return AnswerEntity(
      attemptId = "attempt-1",
      displayIndex = displayIndex,
      questionId = questionId,
      selectedId = "selected-$displayIndex",
      correctId = "correct-$displayIndex",
      isCorrect = isCorrect,
      timeSpentSec = 12,
      seenAt = answeredAt - 6,
      answeredAt = answeredAt,
    )
  }
}
