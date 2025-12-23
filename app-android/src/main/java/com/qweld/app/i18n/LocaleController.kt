package com.qweld.app.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import timber.log.Timber

object LocaleController {
  @Volatile
  private var lastAppliedTag: String? = null

  fun apply(tag: String) {
    // Normalize tag
    val normalizedTag = tag.trim().lowercase()

    // Compute desired locales
    val desiredLocales =
      when (normalizedTag) {
        "system", "" -> LocaleListCompat.getEmptyLocaleList()
        "en" -> LocaleListCompat.forLanguageTags("en")
        "ru" -> LocaleListCompat.forLanguageTags("ru")
        else -> LocaleListCompat.getEmptyLocaleList()
      }

    // Get current applied locales
    val currentLocales = AppCompatDelegate.getApplicationLocales()

    // Check if already applied (idempotency)
    val alreadyApplied = (desiredLocales == currentLocales) && (lastAppliedTag == normalizedTag)

    if (alreadyApplied) {
      Timber.i("[settings_locale] apply tag=%s locales=%s apply_skipped=true", normalizedTag, desiredLocales)
      return
    }

    // Apply locale change
    lastAppliedTag = normalizedTag
    AppCompatDelegate.setApplicationLocales(desiredLocales)
    Timber.i("[settings_locale] apply tag=%s locales=%s apply_skipped=false", normalizedTag, desiredLocales)
  }
}
