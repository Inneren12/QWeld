package com.qweld.app.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AttemptEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class DaoAttemptTest {
  private lateinit var context: Context
  private lateinit var db: QWeldDb
  private lateinit var attemptDao: AttemptDao

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = QWeldDb.inMemory(context)
    attemptDao = db.attemptDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun insertAndFetchById() = runTest {
    val attempt =
      AttemptEntity(
        id = "attempt-1",
        mode = "PRACTICE",
        locale = "EN",
        seed = 42L,
        questionCount = 3,
        startedAt = 1_000L,
      )
    attemptDao.insert(attempt)

    val loaded = attemptDao.getById("attempt-1")
    assertNotNull(loaded)
    assertEquals("attempt-1", loaded?.id)
    assertEquals(3, loaded?.questionCount)
  }

  @Test
  fun updateFinishPersistsValues() = runTest {
    val attempt =
      AttemptEntity(
        id = "attempt-2",
        mode = "PRACTICE",
        locale = "EN",
        seed = 42L,
        questionCount = 4,
        startedAt = 2_000L,
      )
    attemptDao.insert(attempt)

    attemptDao.updateFinish("attempt-2", finishedAt = 3_000L, durationSec = 120, passThreshold = 70, scorePct = 82.5)

    val loaded = attemptDao.getById("attempt-2")
    assertEquals(3_000L, loaded?.finishedAt)
    assertEquals(120, loaded?.durationSec)
    assertEquals(70, loaded?.passThreshold)
    assertEquals(82.5, loaded?.scorePct)
  }

  @Test
  fun listRecentOrdersByStartedAtDesc() = runTest {
    val attempts =
      listOf(
        AttemptEntity("a", "PRACTICE", "EN", 1L, 5, 100L),
        AttemptEntity("b", "PRACTICE", "EN", 2L, 5, 300L),
        AttemptEntity("c", "PRACTICE", "EN", 3L, 5, 200L),
      )
    attempts.forEach { attemptDao.insert(it) }

    val recent = attemptDao.listRecent(limit = 2)
    assertEquals(listOf("b", "c"), recent.map { it.id })
  }
}
