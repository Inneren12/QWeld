package com.qweld.app.i18n

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qweld.app.MainActivity
import com.qweld.app.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Real end-to-end UI test for locale switching functionality.
 *
 * Verifies that:
 * - Users can navigate to Settings via testTag-based overflow menu
 * - Users can switch between EN and RU locales using testTag-based locale options
 * - UI labels update with real localized resources (R.string.language: "Language" → "Язык")
 * - Locale changes persist after activity recreation (handled by AppCompatDelegate)
 *
 * This test uses the REAL Settings screen with LocaleController integration,
 * not a test scaffold with local state.
 * Navigation uses testTags (topbar.overflow, topbar.menu.settings, settings.locale.option.*)
 * to avoid brittle text-based selectors.
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class LocaleSwitchUiTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  @Test
  fun settingsScreen_switchLocale_updatesLabelsWithRealResources() {
    // Wait for app to load
    composeTestRule.waitForIdle()

    // Navigate to Settings via testTag-based navigation
    composeTestRule.onNodeWithTag("topbar.overflow").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("topbar.menu.settings").performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Settings screen (locale row is visible)
    composeTestRule.onNodeWithTag("settings.locale.row").assertIsDisplayed()

    // Verify initial locale label (English: "Language")
    val enLanguageLabel = localizedString(R.string.language, "en")
    composeTestRule.onNodeWithText(enLanguageLabel).assertExists()

    // Switch to Russian using testTag
    composeTestRule.onNodeWithTag("settings.locale.option.ru").performClick()
    composeTestRule.waitForIdle()

    // After locale change, activity may recreate. Wait and verify.
    // The "Language" label should now show as "Язык" (RU)
    val ruLanguageLabel = localizedString(R.string.language, "ru")
    composeTestRule.onNodeWithText(ruLanguageLabel).assertExists()

    // Verify we're still on Settings screen
    composeTestRule.onNodeWithTag("settings.locale.row").assertIsDisplayed()

    // Switch back to English using testTag
    composeTestRule.onNodeWithTag("settings.locale.option.en").performClick()
    composeTestRule.waitForIdle()

    // Verify we're back to EN: "Language" label should appear
    composeTestRule.onNodeWithText(enLanguageLabel).assertExists()
    composeTestRule.onNodeWithTag("settings.locale.row").assertIsDisplayed()
  }

  /**
   * Helper to resolve a string resource under a specific locale.
   * Creates a configuration context with the given locale and returns the localized string.
   */
  private fun localizedString(resId: Int, localeTag: String): String {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val locale = Locale.forLanguageTag(localeTag)
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocales(LocaleList(locale))
    val localizedContext = context.createConfigurationContext(configuration)
    return localizedContext.getString(resId)
  }
}
