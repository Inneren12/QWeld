package com.qweld.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qweld.app.data.db.entities.QueuedQuestionReportEntity

@Dao
interface QueuedQuestionReportDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: QueuedQuestionReportEntity): Long

  @Query(
    "SELECT * FROM queued_question_reports ORDER BY created_at ASC LIMIT :limit",
  )
  suspend fun listOldest(limit: Int = 50): List<QueuedQuestionReportEntity>

  @Query(
    "UPDATE queued_question_reports SET attempt_count = attempt_count + 1, last_attempt_at = :lastAttemptAt WHERE id = :id",
  )
  suspend fun incrementAttempt(id: Long, lastAttemptAt: Long)

  @Query("DELETE FROM queued_question_reports WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("SELECT * FROM queued_question_reports WHERE id = :id")
  suspend fun getById(id: Long): QueuedQuestionReportEntity?

  @Query("SELECT COUNT(*) FROM queued_question_reports")
  suspend fun count(): Int
}
