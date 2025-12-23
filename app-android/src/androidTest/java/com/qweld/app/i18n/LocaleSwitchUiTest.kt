package com.qweld.app.i18n

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 *
 * Uses Hilt DI test infrastructure to properly inject MainActivity dependencies.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LocaleSwitchUiTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun setup() {
    hiltRule.inject()
  }


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

  @Test
  fun settingsScreen_switchLocale_updatesLabelsWithRealResources() {
    // Get expected localized strings for assertions (resource-based, not hardcoded)
    val enLanguageLabel = localizedString(R.string.language, "en")
    val ruLanguageLabel = localizedString(R.string.language, "ru")

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

    // Force deterministic baseline: switch to EN first (regardless of device locale)
    composeTestRule.onNodeWithTag("settings.locale.option.en").performClick()
    composeTestRule.waitForIdle()

    // Verify EN label is displayed (resource-based)
    composeTestRule.onNodeWithText(enLanguageLabel).assertIsDisplayed()

    // Switch to Russian using testTag (not text selector)
    composeTestRule.onNodeWithTag("settings.locale.option.ru").performClick()
    composeTestRule.waitForIdle()

    // After locale change, verify the RU string is displayed (resource-based)
    composeTestRule.onNodeWithText(ruLanguageLabel).assertIsDisplayed()

    // Switch back to English using testTag
    composeTestRule.onNodeWithTag("settings.locale.option.en").performClick()
    composeTestRule.waitForIdle()

    // Verify we're back to EN: English label should appear (resource-based)
    composeTestRule.onNodeWithText(enLanguageLabel).assertIsDisplayed()
  }
}
