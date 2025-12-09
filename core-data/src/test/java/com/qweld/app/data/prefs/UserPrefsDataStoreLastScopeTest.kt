package com.qweld.app.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UserPrefsDataStoreLastScopeTest {

  private lateinit var tempDir: Path

  @Before
  fun setUp() {
    tempDir = Files.createTempDirectory("user-prefs-last-scope-test")
  }

  @After
  fun tearDown() {
    tempDir.toFile().deleteRecursively()
  }

  @Test
  fun saveAndReadLastScope() = runTest {
    val prefs = newDataStore()

    assertNull(prefs.readLastPracticeScope().first())
    assertNull(prefs.readLastPracticeConfig().first())

    prefs.saveLastPracticeScope(
      blocks = setOf("b", "A"),
      tasks = setOf("a-1", "B-6"),
      distribution = "even",
    )

    val stored = prefs.readLastPracticeScope().first()
    requireNotNull(stored)
    assertEquals(setOf("A", "B"), stored.blocks)
    assertEquals(setOf("A-1", "B-6"), stored.tasks)
    assertEquals("Even", stored.distribution)
  }

  @Test
  fun saveAndReadLastPracticeConfig() = runTest {
    val prefs = newDataStore()

    prefs.saveLastPracticeConfig(
      blocks = setOf("C"),
      tasks = setOf("C-1"),
      distribution = "Proportional",
      size = 7,
      wrongBiased = true,
    )

    val stored = prefs.readLastPracticeConfig().first()
    requireNotNull(stored)
    assertEquals(setOf("C"), stored.blocks)
    assertEquals(setOf("C-1"), stored.tasks)
    assertEquals("Proportional", stored.distribution)
    assertEquals(7, stored.size)
    assertEquals(true, stored.wrongBiased)
  }

  @Test
  fun lastPracticeConfigNotRestoredWhenSizeMissing() = runTest {
    val prefs = newDataStore()

    prefs.saveLastPracticeScope(
      blocks = setOf("D"),
      tasks = setOf("D-1"),
      distribution = "Even",
    )

    val stored = prefs.readLastPracticeConfig().first()
    assertNull(stored)
  }

  @Test
  fun lastPracticeConfigSanitizesAndNormalizes() = runTest {
    val prefs = newDataStore()

    prefs.saveLastPracticeConfig(
      blocks = setOf(" c ", "b"),
      tasks = setOf("b-1", " C-2"),
      distribution = "even",
      size = UserPrefsDataStore.MAX_PRACTICE_SIZE + 50,
      wrongBiased = false,
    )

    val stored = prefs.readLastPracticeConfig().first()
    requireNotNull(stored)
    assertEquals(setOf("B", "C"), stored.blocks)
    assertEquals(setOf("B-1", "C-2"), stored.tasks)
    assertEquals(UserPrefsDataStore.MAX_PRACTICE_SIZE, stored.size)
    assertEquals("Even", stored.distribution)
    assertEquals(false, stored.wrongBiased)
  }

  private fun TestScope.newDataStore(): UserPrefsDataStore {
    val file = Files.createTempFile(tempDir, "prefs", ".preferences_pb")
    val dataStore =
      PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { file.toFile() },
      )
    return UserPrefsDataStore(dataStore)
  }
}
