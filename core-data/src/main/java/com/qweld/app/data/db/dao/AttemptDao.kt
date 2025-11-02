package com.qweld.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qweld.app.data.db.entities.AttemptEntity

@Dao
interface AttemptDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(attempt: AttemptEntity)

  @Query(
    """
    UPDATE attempts
    SET finished_at = :finishedAt,
        duration_sec = :durationSec,
        pass_threshold = :passThreshold,
        score_pct = :scorePct
    WHERE id = :attemptId
    """
  )
  suspend fun updateFinish(
    attemptId: String,
    finishedAt: Long?,
    durationSec: Int?,
    passThreshold: Int?,
    scorePct: Double?,
  )

  @Query(
    """
    UPDATE attempts
    SET finished_at = :finishedAt,
        duration_sec = NULL,
        pass_threshold = NULL,
        score_pct = NULL
    WHERE id = :id
    """
  )
  suspend fun markAborted(id: String, finishedAt: Long)

  @Query("SELECT * FROM attempts WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): AttemptEntity?

  @Query("SELECT * FROM attempts ORDER BY started_at DESC LIMIT :limit")
  suspend fun listRecent(limit: Int): List<AttemptEntity>

  @Query(
    """
    SELECT *
    FROM attempts
    WHERE finished_at IS NULL
    ORDER BY started_at DESC
    LIMIT 1
    """,
  )
  suspend fun getUnfinished(): AttemptEntity?

  @Query(
    """
    SELECT *
    FROM attempts
    WHERE finished_at IS NOT NULL
    ORDER BY finished_at DESC
    LIMIT 1
    """,
  )
  suspend fun getLastFinished(): AttemptEntity?

  @Query("DELETE FROM attempts")
  suspend fun clearAll()
}
