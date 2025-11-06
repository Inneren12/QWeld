package com.qweld.app.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import timber.log.Timber

object LocaleController {
    /**
     * Применяет локаль ко всему приложению.
     * AppCompat сам пересоздаёт активити; ручной recreate() не нужен.
     */
    fun apply(tag: String) {
        val locales: LocaleListCompat = AppLocales.fromTag(tag)
        val current: LocaleListCompat = AppCompatDelegate.getApplicationLocales()
        // no-op, если локали совпадают — избегаем лишних рестартов
        if (current.toLanguageTags() == locales.toLanguageTags()) {
            Timber.d("[settings_locale] no-op tag=%s", tag)
            return
        }
        Timber.i("[settings_locale] apply tag=%s locales=%s", tag, locales.toLanguageTags())
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
