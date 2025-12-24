package com.qweld.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.qweld.core.data.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class UserPrefsDataStore internal constructor(
  private val dataStore: DataStore<Preferences>,
) : UserPrefs {

  @JvmOverloads
  constructor(
    context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    storeName: String = DATA_STORE_NAME,
  ) : this(getOrCreateDataStore(context = context, scope = scope, storeName = storeName))

  override val analyticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[ANALYTICS_ENABLED_KEY] ?: DEFAULT_ANALYTICS_ENABLED
  }

  override val prewarmDisabled: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[PREWARM_DISABLED_KEY] ?: DEFAULT_PREWARM_DISABLED
  }

  override val adaptiveExamEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[ADAPTIVE_EXAM_ENABLED_KEY] ?: DEFAULT_ADAPTIVE_EXAM_ENABLED
  }

  override fun practiceSizeFlow(): Flow<Int> {
    return dataStore.data.map { preferences ->
      sanitizePracticeSize(preferences[PRACTICE_SIZE_KEY] ?: DEFAULT_PRACTICE_SIZE)
    }
  }

  override fun lruCacheSizeFlow(): Flow<Int> {
    return dataStore.data.map { preferences ->
      sanitizeLruCacheSize(preferences[LRU_CACHE_SIZE_KEY] ?: DEFAULT_LRU_CACHE_SIZE)
    }
  }

  override val fallbackToEN: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[FALLBACK_TO_EN_KEY] ?: DEFAULT_FALLBACK_TO_EN
  }

  override val hapticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[HAPTICS_ENABLED_KEY] ?: DEFAULT_HAPTICS_ENABLED
  }

  override val soundsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[SOUNDS_ENABLED_KEY] ?: DEFAULT_SOUNDS_ENABLED
  }

  override fun appLocaleFlow(): Flow<String> {
    return dataStore.data.map { preferences -> preferences[APP_LOCALE_KEY] ?: DEFAULT_APP_LOCALE }
  }

  override val wrongBiased: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[WRONG_BIASED_KEY] ?: DEFAULT_WRONG_BIASED
  }

  override fun readLastPracticeScope(): Flow<LastScope?> {
    return dataStore.data.map { preferences ->
      val blocksRaw = preferences[LAST_SCOPE_BLOCKS_KEY].orEmpty()
      val tasksRaw = preferences[LAST_SCOPE_TASKS_KEY].orEmpty()
      val distributionRaw = preferences[LAST_SCOPE_DISTRIBUTION_KEY].orEmpty()
      if (blocksRaw.isBlank() && tasksRaw.isBlank() && distributionRaw.isBlank()) {
        return@map null
      }
      val distribution = normalizeDistribution(distributionRaw) ?: return@map null
      val blocks = toNormalizedSet(blocksRaw)
      val tasks = toNormalizedSet(tasksRaw)
      LastScope(blocks = blocks, tasks = tasks, distribution = distribution)
    }
  }

  override fun readLastPracticeConfig(): Flow<LastPracticeConfig?> {
    return dataStore.data.map { preferences ->
      val scope = preferences[LAST_SCOPE_DISTRIBUTION_KEY].orEmpty()
      val size = preferences[LAST_PRACTICE_SIZE_KEY] ?: return@map null
      val wrongBiased = preferences[LAST_PRACTICE_WRONG_BIASED_KEY]
      if (scope.isBlank()) return@map null
      val distribution = normalizeDistribution(scope) ?: return@map null
      val blocks = toNormalizedSet(preferences[LAST_SCOPE_BLOCKS_KEY].orEmpty())
      val tasks = toNormalizedSet(preferences[LAST_SCOPE_TASKS_KEY].orEmpty())
      val sanitizedSize = sanitizePracticeSize(size)
      LastPracticeConfig(
        blocks = blocks,
        tasks = tasks,
        distribution = distribution,
        size = sanitizedSize,
        wrongBiased = wrongBiased ?: DEFAULT_WRONG_BIASED,
      )
    }
  }

  override suspend fun setAnalyticsEnabled(value: Boolean) {
    dataStore.edit { preferences -> preferences[ANALYTICS_ENABLED_KEY] = value }
  }

  override suspend fun setPrewarmDisabled(value: Boolean) {
    dataStore.edit { preferences -> preferences[PREWARM_DISABLED_KEY] = value }
  }

  override suspend fun setAdaptiveExamEnabled(value: Boolean) {
    dataStore.edit { preferences -> preferences[ADAPTIVE_EXAM_ENABLED_KEY] = value }
  }

  override suspend fun setPracticeSize(value: Int) {
    val sanitized = sanitizePracticeSize(value)
    dataStore.edit { preferences -> preferences[PRACTICE_SIZE_KEY] = sanitized }
  }

  override suspend fun setLruCacheSize(value: Int) {
    val sanitized = sanitizeLruCacheSize(value)
    dataStore.edit { preferences -> preferences[LRU_CACHE_SIZE_KEY] = sanitized }
  }

  override suspend fun setFallbackToEN(value: Boolean) {
    dataStore.edit { preferences -> preferences[FALLBACK_TO_EN_KEY] = value }
  }

  override suspend fun setHapticsEnabled(value: Boolean) {
    dataStore.edit { preferences -> preferences[HAPTICS_ENABLED_KEY] = value }
  }

  override suspend fun setSoundsEnabled(value: Boolean) {
    dataStore.edit { preferences -> preferences[SOUNDS_ENABLED_KEY] = value }
  }

  override suspend fun setWrongBiased(value: Boolean) {
    dataStore.edit { preferences -> preferences[WRONG_BIASED_KEY] = value }
  }

  override suspend fun setAppLocale(tag: String) {
    dataStore.edit { preferences -> preferences[APP_LOCALE_KEY] = tag }
  }

  override suspend fun saveLastPracticeScope(
    blocks: Set<String>,
    tasks: Set<String>,
    distribution: String,
  ) {
    val normalizedDistribution = normalizeDistribution(distribution) ?: return
    val normalizedBlocks = normalizeAndJoin(blocks)
    val normalizedTasks = normalizeAndJoin(tasks)
    dataStore.edit { preferences ->
      preferences[LAST_SCOPE_BLOCKS_KEY] = normalizedBlocks
      preferences[LAST_SCOPE_TASKS_KEY] = normalizedTasks
      preferences[LAST_SCOPE_DISTRIBUTION_KEY] = normalizedDistribution
      preferences.remove(LAST_PRACTICE_SIZE_KEY)
      preferences.remove(LAST_PRACTICE_WRONG_BIASED_KEY)
    }
  }

  override suspend fun saveLastPracticeConfig(
    blocks: Set<String>,
    tasks: Set<String>,
    distribution: String,
    size: Int,
    wrongBiased: Boolean,
  ) {
    val normalizedDistribution = normalizeDistribution(distribution) ?: return
    val normalizedBlocks = normalizeAndJoin(blocks)
    val normalizedTasks = normalizeAndJoin(tasks)
    val sanitizedSize = sanitizePracticeSize(size)
    dataStore.edit { preferences ->
      preferences[LAST_SCOPE_BLOCKS_KEY] = normalizedBlocks
      preferences[LAST_SCOPE_TASKS_KEY] = normalizedTasks
      preferences[LAST_SCOPE_DISTRIBUTION_KEY] = normalizedDistribution
      preferences[LAST_PRACTICE_SIZE_KEY] = sanitizedSize
      preferences[LAST_PRACTICE_WRONG_BIASED_KEY] = wrongBiased
    }
  }

  override suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  companion object {
    private const val TAG = "UserPrefsDataStore"
    val DEFAULT_ANALYTICS_ENABLED: Boolean = BuildConfig.ENABLE_ANALYTICS
    const val DEFAULT_PRACTICE_SIZE: Int = 20
    const val MIN_PRACTICE_SIZE: Int = 5
    const val MAX_PRACTICE_SIZE: Int = 125
    const val DEFAULT_PREWARM_DISABLED: Boolean = false
    const val DEFAULT_ADAPTIVE_EXAM_ENABLED: Boolean = false
    const val DEFAULT_LRU_CACHE_SIZE: Int = 8
    const val MIN_LRU_CACHE_SIZE: Int = 4
    const val MAX_LRU_CACHE_SIZE: Int = 32
    const val DEFAULT_WRONG_BIASED: Boolean = false
    const val DEFAULT_FALLBACK_TO_EN: Boolean = false
    const val DEFAULT_HAPTICS_ENABLED: Boolean = true
    const val DEFAULT_SOUNDS_ENABLED: Boolean = false
    const val DEFAULT_APP_LOCALE: String = "system"
    const val DEFAULT_LAST_PRACTICE_SIZE: Int = DEFAULT_PRACTICE_SIZE

    private const val DATA_STORE_NAME = "user_prefs"
    private val ANALYTICS_ENABLED_KEY = booleanPreferencesKey("analytics_enabled")
    private val PREWARM_DISABLED_KEY = booleanPreferencesKey("prewarm_disabled")
    private val ADAPTIVE_EXAM_ENABLED_KEY = booleanPreferencesKey("adaptive_exam_enabled")
    private val PRACTICE_SIZE_KEY = intPreferencesKey("practice_size")
    private val LRU_CACHE_SIZE_KEY = intPreferencesKey("lru_cache_size")
    private val FALLBACK_TO_EN_KEY = booleanPreferencesKey("allow_fallback_en")
    private val HAPTICS_ENABLED_KEY = booleanPreferencesKey("haptics_enabled")
    private val SOUNDS_ENABLED_KEY = booleanPreferencesKey("sounds_enabled")
    private val APP_LOCALE_KEY = stringPreferencesKey("app_locale")
    private val WRONG_BIASED_KEY = booleanPreferencesKey("wrong_biased")
    private val LAST_SCOPE_BLOCKS_KEY = stringPreferencesKey("last_scope_blocks")
    private val LAST_SCOPE_TASKS_KEY = stringPreferencesKey("last_scope_tasks")
    private val LAST_SCOPE_DISTRIBUTION_KEY = stringPreferencesKey("last_scope_distribution")
    private val LAST_PRACTICE_SIZE_KEY = intPreferencesKey("last_practice_size")
    private val LAST_PRACTICE_WRONG_BIASED_KEY = booleanPreferencesKey("last_practice_wrong_biased")

    internal fun defaultPreferences(): Preferences {
      return preferencesOf(
        ANALYTICS_ENABLED_KEY to DEFAULT_ANALYTICS_ENABLED,
        PREWARM_DISABLED_KEY to DEFAULT_PREWARM_DISABLED,
        ADAPTIVE_EXAM_ENABLED_KEY to DEFAULT_ADAPTIVE_EXAM_ENABLED,
        PRACTICE_SIZE_KEY to DEFAULT_PRACTICE_SIZE,
        LRU_CACHE_SIZE_KEY to DEFAULT_LRU_CACHE_SIZE,
        FALLBACK_TO_EN_KEY to DEFAULT_FALLBACK_TO_EN,
        HAPTICS_ENABLED_KEY to DEFAULT_HAPTICS_ENABLED,
        SOUNDS_ENABLED_KEY to DEFAULT_SOUNDS_ENABLED,
        APP_LOCALE_KEY to DEFAULT_APP_LOCALE,
        WRONG_BIASED_KEY to DEFAULT_WRONG_BIASED,
        LAST_SCOPE_BLOCKS_KEY to "",
        LAST_SCOPE_TASKS_KEY to "",
        LAST_SCOPE_DISTRIBUTION_KEY to "",
        LAST_PRACTICE_SIZE_KEY to DEFAULT_LAST_PRACTICE_SIZE,
        LAST_PRACTICE_WRONG_BIASED_KEY to DEFAULT_WRONG_BIASED,
      )
    }

    // DataStore must be a process-singleton per file. In instrumented tests, Hilt components can be
    // recreated within the same process; without caching we'd create multiple DataStores for the same file.
    private val dataStoreCache = ConcurrentHashMap<String, DataStore<Preferences>>()

    private fun getOrCreateDataStore(
      context: Context,
      scope: CoroutineScope,
      storeName: String,
    ): DataStore<Preferences> {
      return dataStoreCache.getOrPut(storeName) {
        createDataStore(context = context, scope = scope, storeName = storeName)
      }
    }

    fun createDataStore(
      context: Context,
      scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
      storeName: String = DATA_STORE_NAME,
    ): DataStore<Preferences> =
      PreferenceDataStoreFactory.create(
        corruptionHandler =
          ReplaceFileCorruptionHandler { throwable ->
              Timber.tag(TAG).w(throwable, "[datastore_recover] store=%s cause=corruption", storeName)
            defaultPreferences()
          },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(storeName) },
      )

    internal fun sanitizePracticeSize(value: Int): Int {
      return value.coerceIn(MIN_PRACTICE_SIZE, MAX_PRACTICE_SIZE)
    }

    internal fun sanitizeLruCacheSize(value: Int): Int {
      return value.coerceIn(MIN_LRU_CACHE_SIZE, MAX_LRU_CACHE_SIZE)
    }

    private fun normalizeAndJoin(values: Set<String>): String {
      return values
        .asSequence()
        .map { it.trim().uppercase(Locale.US) }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
        .joinToString(separator = ",")
    }

    private fun toNormalizedSet(raw: String): Set<String> {
      if (raw.isBlank()) return emptySet()
      return raw.split(',')
        .asSequence()
        .map { it.trim().uppercase(Locale.US) }
        .filter { it.isNotBlank() }
        .toCollection(LinkedHashSet())
    }

    private fun normalizeDistribution(value: String): String? {
      return when (value.uppercase(Locale.US)) {
        "PROPORTIONAL" -> "Proportional"
        "EVEN" -> "Even"
        else -> null
      }
    }
  }

  data class LastScope(
    val blocks: Set<String>,
    val tasks: Set<String>,
    val distribution: String,
  )

  data class LastPracticeConfig(
    val blocks: Set<String>,
    val tasks: Set<String>,
    val distribution: String,
    val size: Int,
    val wrongBiased: Boolean,
  )
}
