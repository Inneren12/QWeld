package com.qweld.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
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

  /**
   * Updates remaining time for an in-progress attempt.
   * Used by autosave to persist timer state for process-death resume.
   */
  @Query(
    """
    UPDATE attempts
    SET remaining_time_ms = :remainingTimeMs
    WHERE id = :attemptId
    """
  )
  suspend fun updateRemainingTime(attemptId: String, remainingTimeMs: Long?)

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

  @Query(
    """
    SELECT
      COUNT(*) AS totalCount,
      COALESCE(SUM(CASE WHEN finished_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS finishedCount,
      COALESCE(SUM(CASE WHEN finished_at IS NULL THEN 1 ELSE 0 END), 0) AS inProgressCount,
      COALESCE(
        SUM(
          CASE
            WHEN finished_at IS NOT NULL
              AND score_pct IS NOT NULL
              AND pass_threshold IS NOT NULL
              AND score_pct < pass_threshold THEN 1
            ELSE 0
          END
        ),
        0
      ) AS failedCount,
      MAX(finished_at) AS lastFinishedAt
    FROM attempts
    """,
  )
  suspend fun getAttemptStats(): AttemptStatsRow

  @RawQuery
  suspend fun getUserVersion(query: SupportSQLiteQuery): Int

  @Query("DELETE FROM attempts")
  suspend fun clearAll()

  data class AttemptStatsRow(
    val totalCount: Int,
    val finishedCount: Int,
    val inProgressCount: Int,
    val failedCount: Int,
    val lastFinishedAt: Long?,
  )
}
