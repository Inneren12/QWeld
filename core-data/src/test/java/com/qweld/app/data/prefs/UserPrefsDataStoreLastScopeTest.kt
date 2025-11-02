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
