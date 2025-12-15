package com.qweld.app.data.prefs

import kotlinx.coroutines.flow.Flow

/**
 * Interface for user preferences storage.
 * Abstracts the underlying DataStore implementation to enable testing.
 */
interface UserPrefs {
  val analyticsEnabled: Flow<Boolean>
  val prewarmDisabled: Flow<Boolean>
  val adaptiveExamEnabled: Flow<Boolean>
  val fallbackToEN: Flow<Boolean>
  val hapticsEnabled: Flow<Boolean>
  val soundsEnabled: Flow<Boolean>
  val wrongBiased: Flow<Boolean>

  fun practiceSizeFlow(): Flow<Int>
  fun lruCacheSizeFlow(): Flow<Int>
  fun appLocaleFlow(): Flow<String>
  fun readLastPracticeScope(): Flow<UserPrefsDataStore.LastScope?>
  fun readLastPracticeConfig(): Flow<UserPrefsDataStore.LastPracticeConfig?>

  suspend fun setAnalyticsEnabled(value: Boolean)
  suspend fun setPrewarmDisabled(value: Boolean)
  suspend fun setAdaptiveExamEnabled(value: Boolean)
  suspend fun setPracticeSize(value: Int)
  suspend fun setLruCacheSize(value: Int)
  suspend fun setFallbackToEN(value: Boolean)
  suspend fun setHapticsEnabled(value: Boolean)
  suspend fun setSoundsEnabled(value: Boolean)
  suspend fun setWrongBiased(value: Boolean)
  suspend fun setAppLocale(tag: String)
  suspend fun saveLastPracticeScope(blocks: Set<String>, tasks: Set<String>, distribution: String)
  suspend fun saveLastPracticeConfig(
    blocks: Set<String>,
    tasks: Set<String>,
    distribution: String,
    size: Int,
    wrongBiased: Boolean,
  )
  suspend fun clear()
}
