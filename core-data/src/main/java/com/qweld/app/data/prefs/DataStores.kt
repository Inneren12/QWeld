package com.qweld.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Единственный DataStore на процесс. Объявлять ЭТОТ делегат нужно ОДИН раз в проекте.
val Context.userPrefsDataStore by preferencesDataStore(
      name = "user_prefs"
)
