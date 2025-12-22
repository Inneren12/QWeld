package com.qweld.app.i18n

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qweld.app.MainActivity
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
 * - Users can navigate to Settings via overflow menu using stable testTags (not text selectors)
 * - Users can switch between EN and RU locales using testTag-based navigation
 * - UI labels update with real localized resources after switching locales
 * - Locale changes are reflected correctly in the Settings screen
 *
 * This test uses the REAL app UI (MainActivity with full navigation) and testTags for
 * deterministic, locale-independent navigation.
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

    // Navigate to Settings via overflow menu using testTags (NOT text selectors)
    // Step 1: Click overflow button to open dropdown
    composeTestRule.onNodeWithTag("topbar.overflow").performClick()
    composeTestRule.waitForIdle()

    // Step 2: Click Settings menu item
    composeTestRule.onNodeWithTag("topbar.menu.settings").performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Settings screen by checking the locale section is visible
    composeTestRule.onNodeWithTag("settings.locale.row").assertIsDisplayed()

    // Switch to Russian using testTag (not text selector)
    composeTestRule.onNodeWithTag("settings.locale.option.ru").performClick()
    composeTestRule.waitForIdle()

    // After locale change, verify the known RU string is displayed
    // The "Language" label should now show as "Язык" (RU)
    composeTestRule.onNodeWithText("Язык").assertIsDisplayed()

    // Switch back to English using testTag
    composeTestRule.onNodeWithTag("settings.locale.option.en").performClick()
    composeTestRule.waitForIdle()

    // Verify we're back to EN: "Language" label should appear
    composeTestRule.onNodeWithText("Language").assertIsDisplayed()
  }
}
