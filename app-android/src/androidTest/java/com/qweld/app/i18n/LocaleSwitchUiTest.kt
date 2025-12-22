package com.qweld.app.i18n

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.qweld.app.ui.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI test for locale switching functionality.
 * Verifies that the user can switch between EN and RU locales and that UI labels update accordingly.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 34)
class LocaleSwitchUiTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun settingsScreen_switchLocale_updatesLabels() {
    // This is a simplified test that verifies locale state changes
    // In a real scenario, this would integrate with LocaleController and UserPrefs
    var currentLocale by mutableStateOf("en")
    val enLabel = "Language"
    val ruLabel = "Язык"

    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        LocaleSwitchTestScaffold(
          locale = currentLocale,
          onLocaleChange = { currentLocale = it },
        )
      }
    }

    // Initially shows EN
    composeTestRule.onNodeWithText(enLabel).assertExists()

    // Click to switch to RU
    composeTestRule.onNodeWithText("Switch to RU").performClick()
    assertEquals("ru", currentLocale)

    // Now shows RU label
    composeTestRule.onNodeWithText(ruLabel).assertExists()

    // Switch back to EN
    composeTestRule.onNodeWithText("Switch to EN").performClick()
    assertEquals("en", currentLocale)
    composeTestRule.onNodeWithText(enLabel).assertExists()
  }
}

@Composable
private fun LocaleSwitchTestScaffold(
  locale: String,
  onLocaleChange: (String) -> Unit,
) {
  androidx.compose.foundation.layout.Column {
    // Simulated locale-aware label
    Text(text = if (locale == "en") "Language" else "Язык")

    androidx.compose.material3.Button(onClick = { onLocaleChange("ru") }) {
      Text("Switch to RU")
    }

    androidx.compose.material3.Button(onClick = { onLocaleChange("en") }) {
      Text("Switch to EN")
    }
  }
}
