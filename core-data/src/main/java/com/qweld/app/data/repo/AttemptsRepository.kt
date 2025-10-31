package com.qweld.app.data.repo

import android.util.Log
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AttemptEntity

class AttemptsRepository(
  private val attemptDao: AttemptDao,
  private val logger: (String) -> Unit = { Log.i(TAG, it) },
) {
  suspend fun save(attempt: AttemptEntity) {
    attemptDao.insert(attempt)
    logger("[attempt_save] id=${attempt.id} items=${attempt.questionCount}")
  }

  suspend fun markFinished(
    attemptId: String,
    finishedAt: Long?,
    durationSec: Int?,
    passThreshold: Int?,
    scorePct: Double?,
  ) {
    attemptDao.updateFinish(attemptId, finishedAt, durationSec, passThreshold, scorePct)
  }

  suspend fun getById(id: String): AttemptEntity? = attemptDao.getById(id)

  suspend fun listRecent(limit: Int): List<AttemptEntity> = attemptDao.listRecent(limit)

  suspend fun getUnfinished(): AttemptEntity? = attemptDao.getUnfinished()

  suspend fun clearAll() {
    attemptDao.clearAll()
    logger("[attempt_clear_all]")
  }

  private companion object {
    private const val TAG = "AttemptsRepository"
  }
}
