package com.qweld.app.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserPrefsDataStoreTest {

  private lateinit var tempDir: Path

  @Before
  fun setUp() {
    tempDir = Files.createTempDirectory("user-prefs-test")
  }

  @After
  fun tearDown() {
    tempDir.toFile().deleteRecursively()
  }

  @Test
  fun defaultsMatchBuildConfig() = runTest {
    val prefs = newDataStore()

    assertEquals(UserPrefsDataStore.DEFAULT_ANALYTICS_ENABLED, prefs.analyticsEnabled.first())
    assertEquals(UserPrefsDataStore.DEFAULT_PRACTICE_SIZE, prefs.practiceSize.first())
    assertEquals(UserPrefsDataStore.DEFAULT_FALLBACK_TO_EN, prefs.fallbackToEN.first())
    assertEquals(UserPrefsDataStore.DEFAULT_HAPTICS_ENABLED, prefs.hapticsEnabled.first())
    assertEquals(UserPrefsDataStore.DEFAULT_SOUNDS_ENABLED, prefs.soundsEnabled.first())
  }

  @Test
  fun updatesPersistAcrossReads() = runTest {
    val prefs = newDataStore()

    prefs.setAnalyticsEnabled(false)
    prefs.setPracticeSize(35)
    prefs.setFallbackToEN(true)
    prefs.setHapticsEnabled(false)
    prefs.setSoundsEnabled(true)

    assertFalse(prefs.analyticsEnabled.first())
    assertEquals(35, prefs.practiceSize.first())
    assertTrue(prefs.fallbackToEN.first())
    assertFalse(prefs.hapticsEnabled.first())
    assertTrue(prefs.soundsEnabled.first())
  }

  private fun TestScope.newDataStore(): UserPrefsDataStore {
    val file = Files.createTempFile(tempDir, "prefs", ".preferences_pb")
    val dataStore =
      PreferenceDataStoreFactory.createWithPath(
        scope = backgroundScope,
        produceFile = { file },
      )
    return UserPrefsDataStore(dataStore)
  }
}
