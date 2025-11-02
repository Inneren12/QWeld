package com.qweld.app.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import timber.log.Timber

object LocaleController {
  fun apply(tag: String) {
    val locales =
      when (tag) {
        "system" -> LocaleListCompat.getEmptyLocaleList()
        "en" -> LocaleListCompat.forLanguageTags("en")
        "ru" -> LocaleListCompat.forLanguageTags("ru")
        else -> LocaleListCompat.getEmptyLocaleList()
      }
    AppCompatDelegate.setApplicationLocales(locales)
    Timber.i("[settings_locale] apply tag=%s locales=%s", tag, locales)
  }
}
