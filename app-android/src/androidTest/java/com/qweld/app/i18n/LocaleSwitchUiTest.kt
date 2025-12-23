package com.qweld.app.i18n

import android.content.res.Configuration
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
//import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qweld.app.MainActivity
import com.qweld.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Real end-to-end UI test for locale switching functionality.
 *
 * Verifies that:
 * - Users can navigate to Settings via overflow menu using stable testTags (not text selectors)
 * - Users can switch between EN and RU locales using testTag-based navigation
 * - UI labels update with real localized resources after switching locales
 * - Locale changes are reflected correctly in the Settings screen
 *
 * This test uses the REAL app UI (MainActivity with full navigation) and testTags for
 * deterministic, locale-independent navigation.
 */
@RunWith(AndroidJUnit4::class)
class LocaleSwitchUiTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()


  /**
   * Helper to get a localized string for a specific locale tag.
   * Creates a configuration context with the requested locale and resolves the string resource.
   */
  private fun localizedString(@StringRes id: Int, localeTag: String): String {
    val locale = Locale.forLanguageTag(localeTag)
    val config = Configuration(composeTestRule.activity.resources.configuration)
    config.setLocale(locale)
    val localizedContext = composeTestRule.activity.createConfigurationContext(config)
    return localizedContext.getString(id)
  }

  /**
   * Locale apply / config changes can push MainActivity to STOPPED briefly.
   * When that happens Compose roots are detached and ComposeTestRule throws
   * "No compose hierarchies found". This helper forces RESUMED and waits until
   * at least one Compose root is registered again.
   */
  private fun ensureComposeReady(timeoutMs: Long = 10_000) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
      // Bring ActivityScenario back to foreground if it was STOPPED.
      composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
      try {
        // If no roots exist, this throws IllegalStateException ("No compose hierarchies found")
        composeTestRule.onAllNodes(isRoot()).fetchSemanticsNodes()
        return
      } catch (_: IllegalStateException) {
        SystemClock.sleep(50)
      }
    }
    throw AssertionError("Compose hierarchies did not appear within ${timeoutMs}ms")
  }

  private fun scrollToTag(tag: String) {
    runCatching { composeTestRule.onNodeWithTag(tag).performScrollTo() }
  }

  private fun scrollToText(text: String) {
    runCatching { composeTestRule.onNodeWithText(text).performScrollTo() }
  }

  @Test
  fun settingsScreen_switchLocale_updatesLabelsWithRealResources() {
    // Critical: make sure the activity is RESUMED and Compose root exists before first click.
    ensureComposeReady()

    // Get expected localized strings for assertions (resource-based, not hardcoded)
    val enLanguageLabel = localizedString(R.string.language, "en")
    val ruLanguageLabel = localizedString(R.string.language, "ru")

    // Wait for app to load
    composeTestRule.waitForIdle()
      ensureComposeReady()

    // Navigate to Settings via overflow menu using testTags (NOT text selectors)
    // Step 1: Click overflow button to open dropdown
      scrollToTag("topbar.overflow")
      composeTestRule.onNodeWithTag("topbar.overflow").assertExists().performClick()
    composeTestRule.waitForIdle()
      ensureComposeReady()

    // Step 2: Click Settings menu item
      scrollToTag("topbar.menu.settings")
      composeTestRule.onNodeWithTag("topbar.menu.settings").assertExists().performClick()
    composeTestRule.waitForIdle()
      ensureComposeReady()

    // Verify we're on Settings screen by checking the locale section is visible
      scrollToTag("settings.locale.row")
      composeTestRule.onNodeWithTag("settings.locale.row").assertIsDisplayed()

    // Force deterministic baseline: switch to EN first (regardless of device locale)
      scrollToTag("settings.locale.option.en")
      composeTestRule.onNodeWithTag("settings.locale.option.en").assertExists().performClick()
    composeTestRule.waitForIdle()
      ensureComposeReady()

    // Verify EN label is displayed (resource-based)
      scrollToText(enLanguageLabel)
      composeTestRule.onNodeWithText(enLanguageLabel).assertIsDisplayed()

    // Switch to Russian using testTag (not text selector)
      scrollToTag("settings.locale.option.ru")
      composeTestRule.onNodeWithTag("settings.locale.option.ru").assertExists().performClick()
    composeTestRule.waitForIdle()
      ensureComposeReady()

    // After locale change, verify the RU string is displayed (resource-based)
      scrollToText(ruLanguageLabel)
      composeTestRule.onNodeWithText(ruLanguageLabel).assertIsDisplayed()

    // Switch back to English using testTag
      scrollToTag("settings.locale.option.en")
      composeTestRule.onNodeWithTag("settings.locale.option.en").assertExists().performClick()
      composeTestRule.waitForIdle()
      ensureComposeReady()

    // Verify we're back to EN: English label should appear (resource-based)
      scrollToText(enLanguageLabel)
      composeTestRule.onNodeWithText(enLanguageLabel).assertIsDisplayed()
  }
}
