package com.qweld.app.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale
import timber.log.Timber

object LocaleController {
    @Volatile
    private var lastAppliedTag: String? = null

    @Synchronized
    fun apply(rawTag: String) {
        val tag = rawTag.trim().lowercase(Locale.ROOT)

        val desired =
            when {
                tag.isBlank() || tag == "system" -> LocaleListCompat.getEmptyLocaleList()
                else -> LocaleListCompat.forLanguageTags(tag) // supports en-CA, uk-UA, etc.
            }

        val desiredTags = desired.toLanguageTags()
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()

        // If already applied, do nothing (prevents startup thrash and duplicate observers)
        if (desiredTags == currentTags && lastAppliedTag == tag) {
            Timber.i("[settings_locale] apply_skipped=true tag=%s locales=%s", tag, desiredTags)
            return
        }
        if (desiredTags == currentTags) {
            lastAppliedTag = tag
            Timber.i("[settings_locale] apply_skipped=true tag=%s locales=%s", tag, desiredTags)
            return
        }

        lastAppliedTag = tag
        Timber.i("[settings_locale] apply tag=%s locales=%s (current=%s)", tag, desiredTags, currentTags)
        AppCompatDelegate.setApplicationLocales(desired)
    }

    /**
     * Returns current locale as a Compose State that triggers recomposition when locale changes.
     * Use this in Composables to ensure UI updates when locale changes.
     */
    @Composable
    fun currentLocaleAsState(): State<Locale> {
        // LocalConfiguration triggers recomposition when configuration (including locale) changes
        val configuration = LocalConfiguration.current
        return produceState(
            initialValue = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault(),
            key1 = configuration
        ) {
            value = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        }
    }
}
