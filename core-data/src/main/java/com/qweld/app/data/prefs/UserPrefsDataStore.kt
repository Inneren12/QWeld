package com.qweld.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.qweld.core.data.BuildConfig
import java.util.LinkedHashSet
import java.util.Locale

class UserPrefsDataStore internal constructor(
  private val dataStore: DataStore<Preferences>,
) {

  @JvmOverloads
  constructor(
    context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
  ) : this(
      PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(DATA_STORE_NAME) },
      ),
    )

  val analyticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[ANALYTICS_ENABLED_KEY] ?: DEFAULT_ANALYTICS_ENABLED
  }

  val practiceSize: Flow<Int> = dataStore.data.map { preferences ->
    sanitizePracticeSize(preferences[PRACTICE_SIZE_KEY] ?: DEFAULT_PRACTICE_SIZE)
  }

  val fallbackToEN: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[FALLBACK_TO_EN_KEY] ?: DEFAULT_FALLBACK_TO_EN
  }

  val hapticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[HAPTICS_ENABLED_KEY] ?: DEFAULT_HAPTICS_ENABLED
  }

  val soundsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[SOUNDS_ENABLED_KEY] ?: DEFAULT_SOUNDS_ENABLED
  }

  val wrongBiased: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[WRONG_BIASED_KEY] ?: DEFAULT_WRONG_BIASED
  }

  fun readLastPracticeScope(): Flow<LastScope?> {
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

  @Deprecated("Use fallbackToEN instead", replaceWith = ReplaceWith("fallbackToEN"))
  val allowFallbackToEN: Flow<Boolean> = fallbackToEN

  suspend fun setAnalyticsEnabled(value: Boolean) {
    dataStore.edit { preferences -> preferences[ANALYTICS_ENABLED_KEY] = value }
  }

  suspend fun setPracticeSize(value: Int) {
    val sanitized = sanitizePracticeSize(value)
    dataStore.edit { preferences -> preferences[PRACTICE_SIZE_KEY] = sanitized }
  }

  suspend fun setFallbackToEN(value: Boolean) {
    dataStore.edit { preferences -> preferences[FALLBACK_TO_EN_KEY] = value }
  }

  suspend fun setAllowFallbackToEN(value: Boolean) {
    setFallbackToEN(value)
  }

  suspend fun setHapticsEnabled(value: Boolean) {
    dataStore.edit { preferences -> preferences[HAPTICS_ENABLED_KEY] = value }
  }

  suspend fun setSoundsEnabled(value: Boolean) {
    dataStore.edit { preferences -> preferences[SOUNDS_ENABLED_KEY] = value }
  }

  suspend fun setWrongBiased(value: Boolean) {
    dataStore.edit { preferences -> preferences[WRONG_BIASED_KEY] = value }
  }

  suspend fun saveLastPracticeScope(
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
    }
  }

  suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  companion object {
    val DEFAULT_ANALYTICS_ENABLED: Boolean = BuildConfig.ENABLE_ANALYTICS
    const val DEFAULT_PRACTICE_SIZE: Int = 20
    const val DEFAULT_WRONG_BIASED: Boolean = false
    const val DEFAULT_FALLBACK_TO_EN: Boolean = false
    const val DEFAULT_HAPTICS_ENABLED: Boolean = true
    const val DEFAULT_SOUNDS_ENABLED: Boolean = false

    val PRACTICE_SIZE_PRESETS: Set<Int> = setOf(10, 20, 30)

    private const val DATA_STORE_NAME = "user_prefs"
    private val ANALYTICS_ENABLED_KEY = booleanPreferencesKey("analytics_enabled")
    private val PRACTICE_SIZE_KEY = intPreferencesKey("practice_size")
    private val FALLBACK_TO_EN_KEY = booleanPreferencesKey("allow_fallback_en")
    private val HAPTICS_ENABLED_KEY = booleanPreferencesKey("haptics_enabled")
    private val SOUNDS_ENABLED_KEY = booleanPreferencesKey("sounds_enabled")
    private val WRONG_BIASED_KEY = booleanPreferencesKey("wrong_biased")
    private val LAST_SCOPE_BLOCKS_KEY = stringPreferencesKey("last_scope_blocks")
    private val LAST_SCOPE_TASKS_KEY = stringPreferencesKey("last_scope_tasks")
    private val LAST_SCOPE_DISTRIBUTION_KEY = stringPreferencesKey("last_scope_distribution")

    private fun sanitizePracticeSize(value: Int): Int {
      return if (value in PRACTICE_SIZE_PRESETS) value else DEFAULT_PRACTICE_SIZE
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
}
