package com.qweld.app.data.repo

import com.qweld.app.data.db.dao.PracticePresetDao
import com.qweld.app.data.db.entities.PracticePresetEntity
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Repository for managing named practice presets.
 *
 * Provides CRUD operations and validation for practice configurations that users can save and reuse.
 */
class PracticePresetsRepository(
  private val dao: PracticePresetDao,
  private val logger: (String) -> Unit = { Timber.tag(TAG).i(it) },
) {

  /**
   * Saves a new practice preset.
   *
   * @return the ID of the newly created preset, or null if a preset with the same name already exists
   */
  suspend fun save(preset: PracticePresetEntity): Long? {
    return try {
      val now = System.currentTimeMillis()
      val normalized = preset.copy(
        name = preset.name.trim(),
        createdAt = now,
        updatedAt = now,
      )

      if (normalized.name.isBlank()) {
        logger("[preset_save] rejected=true reason=empty_name")
        return null
      }

      val id = dao.insert(normalized)
      logger("[preset_save] id=$id name='${normalized.name}' size=${normalized.size}")
      id
    } catch (e: Exception) {
      logger("[preset_save] failed name='${preset.name}' error=${e.message}")
      null
    }
  }

  /**
   * Updates an existing practice preset.
   *
   * @return true if the preset was updated, false if not found or update failed
   */
  suspend fun update(preset: PracticePresetEntity): Boolean {
    return try {
      val normalized = preset.copy(
        name = preset.name.trim(),
        updatedAt = System.currentTimeMillis(),
      )

      if (normalized.name.isBlank()) {
        logger("[preset_update] rejected=true reason=empty_name id=${normalized.id}")
        return false
      }

      val rowsAffected = dao.update(normalized)
      val success = rowsAffected > 0
      logger("[preset_update] id=${normalized.id} name='${normalized.name}' success=$success")
      success
    } catch (e: Exception) {
      logger("[preset_update] failed id=${preset.id} name='${preset.name}' error=${e.message}")
      false
    }
  }

  /**
   * Deletes a practice preset by ID.
   */
  suspend fun deleteById(id: Long) {
    dao.deleteById(id)
    logger("[preset_delete] id=$id")
  }

  /**
   * Deletes a practice preset by name.
   */
  suspend fun deleteByName(name: String) {
    dao.deleteByName(name)
    logger("[preset_delete] name='$name'")
  }

  /**
   * Retrieves a practice preset by ID.
   */
  suspend fun getById(id: Long): PracticePresetEntity? = dao.getById(id)

  /**
   * Retrieves a practice preset by name.
   */
  suspend fun getByName(name: String): PracticePresetEntity? = dao.getByName(name)

  /**
   * Lists all saved practice presets ordered by creation time (most recent first).
   */
  suspend fun listAll(): List<PracticePresetEntity> = dao.listAll()

  /**
   * Observes all practice presets as a Flow for reactive UI updates.
   */
  fun observeAll(): Flow<List<PracticePresetEntity>> = dao.observeAll()

  /**
   * Counts the number of saved presets.
   */
  suspend fun count(): Int = dao.count()

  /**
   * Checks if a preset with the given name already exists.
   */
  suspend fun existsByName(name: String): Boolean {
    return dao.getByName(name) != null
  }

  /**
   * Clears all practice presets (for testing/reset).
   */
  suspend fun clearAll() {
    dao.clearAll()
    logger("[preset_clear_all]")
  }

  companion object {
    private const val TAG = "PracticePresetsRepository"
  }
}
