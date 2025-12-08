package com.qweld.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowLog
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class UserPrefsDataStoreCorruptionTest {

  @Test
  fun corruptedFile_isRecoveredWithDefaultsAndLogged() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storeName = uniqueStoreName()
    val dataStoreFile = context.preferencesDataStoreFile(storeName)
    prepareFile(dataStoreFile, "corrupted")

    ShadowLog.clear()

    val prefs = UserPrefsDataStore(context, scope = backgroundScope, storeName = storeName)

    assertEquals(UserPrefsDataStore.DEFAULT_ANALYTICS_ENABLED, prefs.analyticsEnabled.first())

    val recoverLog =
      ShadowLog.getLogs().firstOrNull {
        it.tag == "UserPrefsDataStore" &&
          it.msg == "[datastore_recover] store=$storeName cause=corruption"
      }

    assertNotNull(recoverLog)

    advanceUntilIdle()

    clearFile(dataStoreFile)
  }

  @Test
  fun validFile_isNotOverwritten() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storeName = uniqueStoreName()
    val dataStoreFile = context.preferencesDataStoreFile(storeName)
    clearFile(dataStoreFile)

    val prefs = UserPrefsDataStore(context, scope = backgroundScope, storeName = storeName)

    prefs.setAnalyticsEnabled(false)
    advanceUntilIdle()

    ShadowLog.clear()

    assertFalse(prefs.analyticsEnabled.first())

    val recoverLogs =
      ShadowLog.getLogs().filter {
        it.tag == "UserPrefsDataStore" &&
          it.msg == "[datastore_recover] store=$storeName cause=corruption"
      }

    assertTrue(recoverLogs.isEmpty())

    advanceUntilIdle()

    clearFile(dataStoreFile)
  }

  private fun uniqueStoreName(): String {
    return "user_prefs_test_${UUID.randomUUID()}"
  }

  private fun prepareFile(file: File, contents: String) {
    file.parentFile?.mkdirs()
    file.writeText(contents)
  }

  private fun clearFile(file: File) {
    if (file.exists()) {
      file.delete()
    }
  }
}
