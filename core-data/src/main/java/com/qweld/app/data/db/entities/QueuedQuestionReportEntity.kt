package com.qweld.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "queued_question_reports",
  indices = [
    Index(value = ["created_at"], orders = [Index.Order.ASC]),
    Index(value = ["question_id"]),
  ],
)
data class QueuedQuestionReportEntity(
  @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
  @ColumnInfo(name = "question_id") val questionId: String,
  @ColumnInfo(name = "locale") val locale: String,
  @ColumnInfo(name = "reason_code") val reasonCode: String,
  @ColumnInfo(name = "payload") val payload: String,
  @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
  @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
  @ColumnInfo(name = "created_at") val createdAt: Long,
)
