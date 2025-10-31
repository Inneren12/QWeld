package com.qweld.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.qweld.core.data.BuildConfig

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
    preferences[PRACTICE_SIZE_KEY] ?: DEFAULT_PRACTICE_SIZE
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

  @Deprecated("Use fallbackToEN instead", replaceWith = ReplaceWith("fallbackToEN"))
  val allowFallbackToEN: Flow<Boolean> = fallbackToEN

  suspend fun setAnalyticsEnabled(value: Boolean) {
    dataStore.edit { preferences -> preferences[ANALYTICS_ENABLED_KEY] = value }
  }

  suspend fun setPracticeSize(value: Int) {
    dataStore.edit { preferences -> preferences[PRACTICE_SIZE_KEY] = value }
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

  suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  companion object {
    val DEFAULT_ANALYTICS_ENABLED: Boolean = BuildConfig.ENABLE_ANALYTICS
    const val DEFAULT_PRACTICE_SIZE: Int = 20
    const val DEFAULT_FALLBACK_TO_EN: Boolean = false
    const val DEFAULT_HAPTICS_ENABLED: Boolean = true
    const val DEFAULT_SOUNDS_ENABLED: Boolean = false

    private const val DATA_STORE_NAME = "user_prefs"
    private val ANALYTICS_ENABLED_KEY = booleanPreferencesKey("analytics_enabled")
    private val PRACTICE_SIZE_KEY = intPreferencesKey("practice_size")
    private val FALLBACK_TO_EN_KEY = booleanPreferencesKey("allow_fallback_en")
    private val HAPTICS_ENABLED_KEY = booleanPreferencesKey("haptics_enabled")
    private val SOUNDS_ENABLED_KEY = booleanPreferencesKey("sounds_enabled")
  }
}
