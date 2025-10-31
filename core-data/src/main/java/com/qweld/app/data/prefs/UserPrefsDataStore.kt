package com.qweld.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPrefsDataStore @JvmOverloads constructor(
  context: Context,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  private val dataStore: DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
      scope = scope,
      produceFile = { context.preferencesDataStoreFile(DATA_STORE_NAME) },
    )

  val practiceSize: Flow<Int> = dataStore.data.map { preferences ->
    preferences[PRACTICE_SIZE_KEY] ?: DEFAULT_PRACTICE_SIZE
  }

  val allowFallbackToEN: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[ALLOW_FALLBACK_TO_EN_KEY] ?: DEFAULT_ALLOW_FALLBACK_TO_EN
  }

  suspend fun setPracticeSize(value: Int) {
    dataStore.edit { preferences -> preferences[PRACTICE_SIZE_KEY] = value }
  }

  suspend fun setAllowFallbackToEN(value: Boolean) {
    dataStore.edit { preferences -> preferences[ALLOW_FALLBACK_TO_EN_KEY] = value }
  }

  suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  companion object {
    const val DEFAULT_PRACTICE_SIZE: Int = 20
    const val DEFAULT_ALLOW_FALLBACK_TO_EN: Boolean = false

    private const val DATA_STORE_NAME = "user_prefs"
    private val PRACTICE_SIZE_KEY = intPreferencesKey("practice_size")
    private val ALLOW_FALLBACK_TO_EN_KEY = booleanPreferencesKey("allow_fallback_en")
  }
}
