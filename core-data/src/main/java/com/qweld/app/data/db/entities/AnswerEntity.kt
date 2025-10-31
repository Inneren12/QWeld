package com.qweld.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
  tableName = "answers",
  primaryKeys = ["attempt_id", "display_index"],
  foreignKeys = [
    ForeignKey(
      entity = AttemptEntity::class,
      parentColumns = ["id"],
      childColumns = ["attempt_id"],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.NO_ACTION,
    ),
  ],
  indices = [
    Index(value = ["question_id"]),
    Index(value = ["attempt_id", "display_index"], unique = true),
  ],
)
data class AnswerEntity(
  @ColumnInfo(name = "attempt_id") val attemptId: String,
  @ColumnInfo(name = "display_index") val displayIndex: Int,
  @ColumnInfo(name = "question_id") val questionId: String,
  @ColumnInfo(name = "selected_id") val selectedId: String,
  @ColumnInfo(name = "correct_id") val correctId: String,
  @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
  @ColumnInfo(name = "time_spent_sec") val timeSpentSec: Int,
  @ColumnInfo(name = "seen_at") val seenAt: Long,
  @ColumnInfo(name = "answered_at") val answeredAt: Long,
)
