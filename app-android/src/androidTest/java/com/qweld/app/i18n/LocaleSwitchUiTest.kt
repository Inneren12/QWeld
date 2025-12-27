package com.qweld.app.i18n

import android.content.res.Configuration
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import com.qweld.app.MainActivity
import com.qweld.app.R
import com.qweld.app.testing.ComposeStability
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
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
@HiltAndroidTest
class LocaleSwitchUiTest {
    private val hiltRule = HiltAndroidRule(this)
    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun ensureComposeReady(timeoutMs: Long = 10_000) {
        ComposeStability.ensureComposeReady(composeTestRule, timeoutMs)
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(hiltRule)     // MUST run before activity is launched
        .around(composeTestRule) // launches MainActivity

    @Before
    fun setUp() {
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

    /**
     * Get current activity locale - used to verify actual configuration changes
     */
    private fun getCurrentActivityLocale(): Locale {
        return composeTestRule.activity.resources.configuration.locales[0]
    }

    private fun scrollToTag(tag: String) {
        // Settings screen uses Column + verticalScroll, so prefer scrolling via the scroll container.
        runCatching {
            composeTestRule
                .onNodeWithTag("settings.scroll")
                .performScrollToNode(hasTestTag(tag))
        }.onFailure {
            runCatching { composeTestRule.onNodeWithTag(tag).performScrollTo() }
        }
    }

    private fun scrollToText(text: String) {
        runCatching {
            composeTestRule
                .onNodeWithTag("settings.scroll")
                .performScrollToNode(hasText(text))
        }.onFailure {
            runCatching { composeTestRule.onNodeWithText(text).performScrollTo() }
        }
    }

    /**
     * Wait for Activity's configuration to actually change to the expected locale.
     * This is more reliable than just waiting for text, because it verifies the system changed.
     */
    private fun waitForLocaleChange(expectedLocale: Locale, timeoutMs: Long = 8_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var lastSeenLocale: Locale? = null

        while (SystemClock.uptimeMillis() < deadline) {
            try {
                ensureComposeReady(timeoutMs = 2000)
                val currentLocale = getCurrentActivityLocale()
                lastSeenLocale = currentLocale

                if (currentLocale.language == expectedLocale.language) {
                    // Found the expected locale! Give it a moment to settle
                    SystemClock.sleep(500)
                    return
                }
            } catch (e: Exception) {
                // Activity might be recreating, keep trying
            }
            SystemClock.sleep(200)
        }

        throw AssertionError(
            "Locale did not change to ${expectedLocale.language} within ${timeoutMs}ms. " +
                "Last seen locale: ${lastSeenLocale?.language ?: "unknown"}"
        )
    }

    /**
     * Wait for a specific localized text to appear after locale change.
     * This verifies that UI has actually recomposed with new strings.
     */
    private fun waitForLocalizedText(text: String, timeoutMs: Long = 5_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var lastError: Throwable? = null

        while (SystemClock.uptimeMillis() < deadline) {
            try {
                ensureComposeReady(timeoutMs = 2000)
                scrollToText(text)
                composeTestRule.onNodeWithText(text).assertExists()
                return
            } catch (e: AssertionError) {
                lastError = e
                SystemClock.sleep(100)
            } catch (e: IllegalStateException) {
                lastError = e
                SystemClock.sleep(200)
            }
        }

        throw AssertionError(
            "Localized text '$text' did not appear within ${timeoutMs}ms",
            lastError
        )
    }

    @Test
    fun settingsScreen_switchLocale_updatesLabelsWithRealResources() {
        ensureComposeReady()

        val enLanguageLabel = localizedString(R.string.language, "en")
        val ruLanguageLabel = localizedString(R.string.language, "ru")

        val enLocale = Locale.forLanguageTag("en")
        val ruLocale = Locale.forLanguageTag("ru")

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

        // CRITICAL: Wait for configuration to actually change to EN
        waitForLocaleChange(enLocale)

        // Now verify EN text appears
        waitForLocalizedText(enLanguageLabel)
        composeTestRule.onNodeWithText(enLanguageLabel).assertIsDisplayed()

        // Switch to Russian using testTag (not text selector)
        scrollToTag("settings.locale.option.ru")
        composeTestRule.onNodeWithTag("settings.locale.option.ru").assertExists().performClick()

        // CRITICAL: Wait for configuration to actually change to RU
        waitForLocaleChange(ruLocale, timeoutMs = 10_000)

        // After locale change, verify the RU string is displayed (resource-based)
        waitForLocalizedText(ruLanguageLabel, timeoutMs = 6_000)
        composeTestRule.onNodeWithText(ruLanguageLabel).assertIsDisplayed()

        // Switch back to English using testTag
        scrollToTag("settings.locale.option.en")
        composeTestRule.onNodeWithTag("settings.locale.option.en").assertExists().performClick()

        // Wait for configuration to change back to EN
        waitForLocaleChange(enLocale)

        // Verify we're back to EN: English label should appear (resource-based)
        waitForLocalizedText(enLanguageLabel)
        composeTestRule.onNodeWithText(enLanguageLabel).assertIsDisplayed()
    }
}
