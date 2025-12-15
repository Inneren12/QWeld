package com.qweld.app.feature.exam

import com.qweld.app.data.prefs.UserPrefs
import com.qweld.app.data.prefs.UserPrefsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake implementation of UserPrefs for testing.
 * Provides in-memory state that can be easily controlled and observed in tests.
 */
class FakeUserPrefs(
  private val _analyticsEnabled: MutableStateFlow<Boolean> = MutableStateFlow(UserPrefsDataStore.DEFAULT_ANALYTICS_ENABLED),
  private val _prewarmDisabled: MutableStateFlow<Boolean> = MutableStateFlow(UserPrefsDataStore.DEFAULT_PREWARM_DISABLED),
  private val _adaptiveExamEnabled: MutableStateFlow<Boolean> =
    MutableStateFlow(UserPrefsDataStore.DEFAULT_ADAPTIVE_EXAM_ENABLED),
  private val _practiceSize: MutableStateFlow<Int> = MutableStateFlow(UserPrefsDataStore.DEFAULT_PRACTICE_SIZE),
  private val _lruCacheSize: MutableStateFlow<Int> = MutableStateFlow(UserPrefsDataStore.DEFAULT_LRU_CACHE_SIZE),
  private val _fallbackToEN: MutableStateFlow<Boolean> = MutableStateFlow(UserPrefsDataStore.DEFAULT_FALLBACK_TO_EN),
  private val _hapticsEnabled: MutableStateFlow<Boolean> = MutableStateFlow(UserPrefsDataStore.DEFAULT_HAPTICS_ENABLED),
  private val _soundsEnabled: MutableStateFlow<Boolean> = MutableStateFlow(UserPrefsDataStore.DEFAULT_SOUNDS_ENABLED),
  private val _appLocale: MutableStateFlow<String> = MutableStateFlow(UserPrefsDataStore.DEFAULT_APP_LOCALE),
  private val _wrongBiased: MutableStateFlow<Boolean> = MutableStateFlow(UserPrefsDataStore.DEFAULT_WRONG_BIASED),
  private val _lastScope: MutableStateFlow<UserPrefsDataStore.LastScope?> = MutableStateFlow(null),
  private val _lastPracticeConfig:
    MutableStateFlow<UserPrefsDataStore.LastPracticeConfig?> = MutableStateFlow(null),
) : UserPrefs {

  override val analyticsEnabled: Flow<Boolean> = _analyticsEnabled
  override val prewarmDisabled: Flow<Boolean> = _prewarmDisabled
  override val adaptiveExamEnabled: Flow<Boolean> = _adaptiveExamEnabled
  override val fallbackToEN: Flow<Boolean> = _fallbackToEN
  override val hapticsEnabled: Flow<Boolean> = _hapticsEnabled
  override val soundsEnabled: Flow<Boolean> = _soundsEnabled
  override val wrongBiased: Flow<Boolean> = _wrongBiased

  override fun practiceSizeFlow(): Flow<Int> = _practiceSize
  override fun lruCacheSizeFlow(): Flow<Int> = _lruCacheSize
  override fun appLocaleFlow(): Flow<String> = _appLocale
  override fun readLastPracticeScope(): Flow<UserPrefsDataStore.LastScope?> = _lastScope
  override fun readLastPracticeConfig(): Flow<UserPrefsDataStore.LastPracticeConfig?> = _lastPracticeConfig

  override suspend fun setAnalyticsEnabled(value: Boolean) {
    _analyticsEnabled.value = value
  }

  override suspend fun setPrewarmDisabled(value: Boolean) {
    _prewarmDisabled.value = value
  }

  override suspend fun setAdaptiveExamEnabled(value: Boolean) {
    _adaptiveExamEnabled.value = value
  }

  override suspend fun setPracticeSize(value: Int) {
    _practiceSize.value = sanitizePracticeSize(value)
  }

  override suspend fun setLruCacheSize(value: Int) {
    _lruCacheSize.value = sanitizeLruCacheSize(value)
  }

  override suspend fun setFallbackToEN(value: Boolean) {
    _fallbackToEN.value = value
  }

  override suspend fun setHapticsEnabled(value: Boolean) {
    _hapticsEnabled.value = value
  }

  override suspend fun setSoundsEnabled(value: Boolean) {
    _soundsEnabled.value = value
  }

  override suspend fun setWrongBiased(value: Boolean) {
    _wrongBiased.value = value
  }

  override suspend fun setAppLocale(tag: String) {
    _appLocale.value = tag
  }

  override suspend fun saveLastPracticeScope(
    blocks: Set<String>,
    tasks: Set<String>,
    distribution: String,
  ) {
    _lastScope.value = UserPrefsDataStore.LastScope(
      blocks = blocks,
      tasks = tasks,
      distribution = distribution,
    )
  }

  override suspend fun saveLastPracticeConfig(
    blocks: Set<String>,
    tasks: Set<String>,
    distribution: String,
    size: Int,
    wrongBiased: Boolean,
  ) {
    saveLastPracticeScope(blocks = blocks, tasks = tasks, distribution = distribution)
    _lastPracticeConfig.value =
      UserPrefsDataStore.LastPracticeConfig(
        blocks = blocks,
        tasks = tasks,
        distribution = distribution,
        size = size,
        wrongBiased = wrongBiased,
      )
  }

  override suspend fun clear() {
    _analyticsEnabled.value = UserPrefsDataStore.DEFAULT_ANALYTICS_ENABLED
    _prewarmDisabled.value = UserPrefsDataStore.DEFAULT_PREWARM_DISABLED
    _adaptiveExamEnabled.value = UserPrefsDataStore.DEFAULT_ADAPTIVE_EXAM_ENABLED
    _practiceSize.value = UserPrefsDataStore.DEFAULT_PRACTICE_SIZE
    _lruCacheSize.value = UserPrefsDataStore.DEFAULT_LRU_CACHE_SIZE
    _fallbackToEN.value = UserPrefsDataStore.DEFAULT_FALLBACK_TO_EN
    _hapticsEnabled.value = UserPrefsDataStore.DEFAULT_HAPTICS_ENABLED
    _soundsEnabled.value = UserPrefsDataStore.DEFAULT_SOUNDS_ENABLED
    _appLocale.value = UserPrefsDataStore.DEFAULT_APP_LOCALE
    _wrongBiased.value = UserPrefsDataStore.DEFAULT_WRONG_BIASED
    _lastScope.value = null
    _lastPracticeConfig.value = null
  }

  // Replicate validation logic from UserPrefsDataStore without calling internal methods
  private fun sanitizePracticeSize(value: Int): Int {
    return value.coerceIn(MIN_PRACTICE_SIZE, MAX_PRACTICE_SIZE)
  }

  private fun sanitizeLruCacheSize(value: Int): Int {
    return value.coerceIn(MIN_LRU_CACHE_SIZE, MAX_LRU_CACHE_SIZE)
  }

  companion object {
    private const val MIN_PRACTICE_SIZE = 5
    private const val MAX_PRACTICE_SIZE = 125
    private const val MIN_LRU_CACHE_SIZE = 4
    private const val MAX_LRU_CACHE_SIZE = 32
  }
}
