package com.qweld.app.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AttemptEntity
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AttemptDaoAbortTest {
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
  fun markAbortedSetsFinishedAtAndClearsResults() = runTest {
    val attempt =
      AttemptEntity(
        id = "attempt-abort",
        mode = "PRACTICE",
        locale = "EN",
        seed = 99L,
        questionCount = 5,
        startedAt = 123L,
        finishedAt = null,
        durationSec = 45,
        passThreshold = 70,
        scorePct = 88.0,
      )
    attemptDao.insert(attempt)

    attemptDao.markAborted(id = "attempt-abort", finishedAt = 1_000L)

    val loaded = attemptDao.getById("attempt-abort")
    assertNotNull(loaded)
    assertEquals(1_000L, loaded.finishedAt)
    assertEquals(null, loaded.durationSec)
    assertEquals(null, loaded.passThreshold)
    assertEquals(null, loaded.scorePct)
  }
}
