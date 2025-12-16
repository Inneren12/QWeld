package com.qweld.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted practice preset configuration.
 *
 * Allows users to save and reuse frequently used practice configurations with custom names.
 * In addition to named presets, a special "last used" preset is always maintained via DataStore.
 */
@Entity(
  tableName = "practice_presets",
  indices = [
    Index(
      name = "idx_practice_presets_name",
      value = ["name"],
      unique = true,
    ),
    Index(
      name = "idx_practice_presets_created_at",
      value = ["created_at"],
      orders = [Index.Order.DESC],
    ),
  ],
)
data class PracticePresetEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = "id")
  val id: Long = 0,

  /**
   * User-facing preset name (e.g., "Quick Review", "Section A Focus").
   * Must be unique across all presets.
   */
  @ColumnInfo(name = "name")
  val name: String,

  /**
   * Comma-separated uppercase block IDs (e.g., "A,B,C").
   * Empty if taskIds is used instead.
   */
  @ColumnInfo(name = "blocks")
  val blocks: String = "",

  /**
   * Comma-separated uppercase task IDs (e.g., "A-1,A-2,B-3").
   * Takes precedence over blocks if non-empty.
   */
  @ColumnInfo(name = "task_ids")
  val taskIds: String = "",

  /**
   * Distribution type: "Proportional" or "Even".
   */
  @ColumnInfo(name = "distribution")
  val distribution: String = "Proportional",

  /**
   * Number of questions for this preset.
   * Clamped to valid range [5, 125].
   */
  @ColumnInfo(name = "size")
  val size: Int = 20,

  /**
   * Whether to enable wrong-biased sampling (prefer previously incorrectly answered questions).
   */
  @ColumnInfo(name = "wrong_biased")
  val wrongBiased: Boolean = false,

  /**
   * Timestamp when this preset was created (milliseconds since epoch).
   */
  @ColumnInfo(name = "created_at")
  val createdAt: Long = System.currentTimeMillis(),

  /**
   * Timestamp when this preset was last updated (milliseconds since epoch).
   */
  @ColumnInfo(name = "updated_at")
  val updatedAt: Long = System.currentTimeMillis(),
)
