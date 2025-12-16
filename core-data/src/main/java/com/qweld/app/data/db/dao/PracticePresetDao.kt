package com.qweld.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qweld.app.data.db.entities.PracticePresetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for practice presets.
 *
 * Supports CRUD operations for named practice configurations.
 */
@Dao
interface PracticePresetDao {
  /**
   * Inserts a new practice preset.
   * If a preset with the same name exists, the insert fails (ABORT strategy).
   *
   * @return the row ID of the inserted preset
   */
  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(preset: PracticePresetEntity): Long

  /**
   * Updates an existing practice preset.
   * Sets updated_at to current time automatically.
   *
   * @return number of rows updated (1 if successful, 0 if preset not found)
   */
  @Update
  suspend fun update(preset: PracticePresetEntity): Int

  /**
   * Deletes a practice preset by ID.
   */
  @Query("DELETE FROM practice_presets WHERE id = :id")
  suspend fun deleteById(id: Long)

  /**
   * Deletes a practice preset by name.
   */
  @Query("DELETE FROM practice_presets WHERE name = :name")
  suspend fun deleteByName(name: String)

  /**
   * Retrieves a practice preset by ID.
   */
  @Query("SELECT * FROM practice_presets WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): PracticePresetEntity?

  /**
   * Retrieves a practice preset by name.
   */
  @Query("SELECT * FROM practice_presets WHERE name = :name LIMIT 1")
  suspend fun getByName(name: String): PracticePresetEntity?

  /**
   * Lists all practice presets ordered by creation time (most recent first).
   */
  @Query("SELECT * FROM practice_presets ORDER BY created_at DESC")
  suspend fun listAll(): List<PracticePresetEntity>

  /**
   * Observes all practice presets as a Flow for reactive UI updates.
   */
  @Query("SELECT * FROM practice_presets ORDER BY created_at DESC")
  fun observeAll(): Flow<List<PracticePresetEntity>>

  /**
   * Counts the number of saved presets.
   */
  @Query("SELECT COUNT(*) FROM practice_presets")
  suspend fun count(): Int

  /**
   * Clears all practice presets (for testing/reset).
   */
  @Query("DELETE FROM practice_presets")
  suspend fun clearAll()
}
