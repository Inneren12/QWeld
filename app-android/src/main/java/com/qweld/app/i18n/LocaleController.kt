package com.qweld.app.i18n

import androidx.appcompat.app.AppCompatDelegate
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
}
