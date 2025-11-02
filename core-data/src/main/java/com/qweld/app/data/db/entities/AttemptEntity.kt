package com.qweld.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "attempts",
  indices = [
    Index(
      name = "idx_attempts_started_at",
      value = ["started_at"],
      orders = [Index.Order.DESC],
    ),
  ],
)
data class AttemptEntity(
  @PrimaryKey @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "mode") val mode: String,
  @ColumnInfo(name = "locale") val locale: String,
  @ColumnInfo(name = "seed") val seed: Long,
  @ColumnInfo(name = "question_count") val questionCount: Int,
  @ColumnInfo(name = "started_at") val startedAt: Long,
  @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
  @ColumnInfo(name = "duration_sec") val durationSec: Int? = null,
  @ColumnInfo(name = "pass_threshold") val passThreshold: Int? = null,
  @ColumnInfo(name = "score_pct") val scorePct: Double? = null,
)
