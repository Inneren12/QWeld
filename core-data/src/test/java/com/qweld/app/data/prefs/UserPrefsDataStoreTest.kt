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
    assertEquals(UserPrefsDataStore.DEFAULT_PREWARM_DISABLED, prefs.prewarmDisabled.first())
    assertEquals(UserPrefsDataStore.DEFAULT_PRACTICE_SIZE, prefs.practiceSizeFlow().first())
    assertEquals(UserPrefsDataStore.DEFAULT_LRU_CACHE_SIZE, prefs.lruCacheSizeFlow().first())
    assertEquals(UserPrefsDataStore.DEFAULT_WRONG_BIASED, prefs.wrongBiased.first())
    assertEquals(UserPrefsDataStore.DEFAULT_FALLBACK_TO_EN, prefs.fallbackToEN.first())
    assertEquals(UserPrefsDataStore.DEFAULT_HAPTICS_ENABLED, prefs.hapticsEnabled.first())
    assertEquals(UserPrefsDataStore.DEFAULT_SOUNDS_ENABLED, prefs.soundsEnabled.first())
  }

  @Test
  fun updatesPersistAcrossReads() = runTest {
    val prefs = newDataStore()

    prefs.setAnalyticsEnabled(false)
    prefs.setPrewarmDisabled(true)
    prefs.setPracticeSize(35)
    prefs.setLruCacheSize(16)
    prefs.setFallbackToEN(true)
    prefs.setHapticsEnabled(false)
    prefs.setSoundsEnabled(true)
    prefs.setWrongBiased(true)

    assertFalse(prefs.analyticsEnabled.first())
    assertTrue(prefs.prewarmDisabled.first())
    assertEquals(35, prefs.practiceSizeFlow().first())
    assertEquals(16, prefs.lruCacheSizeFlow().first())
    assertTrue(prefs.fallbackToEN.first())
    assertFalse(prefs.hapticsEnabled.first())
    assertTrue(prefs.soundsEnabled.first())
    assertTrue(prefs.wrongBiased.first())
  }

  @Test
  fun appLocale_defaultsToSystem() = runTest {
    val prefs = newDataStore()

    assertEquals(UserPrefsDataStore.DEFAULT_APP_LOCALE, prefs.appLocaleFlow().first())
  }

  @Test
  fun appLocale_persistsSelection() = runTest {
    val prefs = newDataStore()

    prefs.setAppLocale("ru")

    assertEquals("ru", prefs.appLocaleFlow().first())
  }

  @Test
  fun practiceSize_clampsToRange() = runTest {
    val prefs = newDataStore()

    prefs.setPracticeSize(UserPrefsDataStore.MIN_PRACTICE_SIZE - 2)
    assertEquals(UserPrefsDataStore.MIN_PRACTICE_SIZE, prefs.practiceSizeFlow().first())

    prefs.setPracticeSize(UserPrefsDataStore.MAX_PRACTICE_SIZE + 10)
    assertEquals(UserPrefsDataStore.MAX_PRACTICE_SIZE, prefs.practiceSizeFlow().first())
  }

  @Test
  fun lruCacheSize_clampsToRange() = runTest {
    val prefs = newDataStore()

    prefs.setLruCacheSize(UserPrefsDataStore.MIN_LRU_CACHE_SIZE - 5)
    assertEquals(UserPrefsDataStore.MIN_LRU_CACHE_SIZE, prefs.lruCacheSizeFlow().first())

    prefs.setLruCacheSize(UserPrefsDataStore.MAX_LRU_CACHE_SIZE + 8)
    assertEquals(UserPrefsDataStore.MAX_LRU_CACHE_SIZE, prefs.lruCacheSizeFlow().first())
  }

    private fun TestScope.newDataStore(): UserPrefsDataStore {
        val file = Files.createTempFile(tempDir, "prefs", ".preferences_pb")
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { file.toFile() },   // 👈 тут уже java.io.File, всё ОК
            )
        return UserPrefsDataStore(dataStore)
    }
}
