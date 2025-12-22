package com.qweld.app.i18n

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qweld.app.MainActivity
import com.qweld.app.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real end-to-end UI test for locale switching functionality.
 *
 * Verifies that:
 * - Users can navigate to Settings and switch between EN and RU locales
 * - UI labels update with real localized resources (R.string.language: "Language" → "Язык")
 * - Locale changes persist after activity recreation (handled by AppCompatDelegate)
 *
 * This test uses the REAL Settings screen with LocaleController integration,
 * not a test scaffold with local state.
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
    val context = composeTestRule.activity

    // Wait for app to load
    composeTestRule.waitForIdle()

    // Navigate to Settings
    // Look for the Settings menu button (adjust based on actual navigation)
    val settingsLabel = context.getString(R.string.menu_settings)
    composeTestRule.onNodeWithText(settingsLabel).performClick()
    composeTestRule.waitForIdle()

    // Verify initial locale label (English: "Language")
    val enLanguageLabel = context.getString(R.string.language)
    composeTestRule.onNodeWithText(enLanguageLabel).assertExists()

    // Switch to Russian
    val ruOption = context.getString(R.string.language_ru) // "Russian" in current locale
    composeTestRule.onNodeWithText(ruOption).performClick()
    composeTestRule.waitForIdle()

    // After locale change, activity may recreate. Wait and re-fetch strings.
    // The "Language" label should now show as "Язык" (RU)
    // We can't fetch strings from context anymore because locale changed,
    // so we assert the known RU string directly
    composeTestRule.onNodeWithText("Язык").assertExists()

    // Switch back to English
    // In RU locale, the English option shows as "Английский"
    composeTestRule.onNodeWithText("Английский").performClick()
    composeTestRule.waitForIdle()

    // Verify we're back to EN: "Language" label should appear
    composeTestRule.onNodeWithText("Language").assertExists()
  }
}
