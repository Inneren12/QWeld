package com.qweld.app.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import timber.log.Timber

object LocaleController {
    fun apply(tag: String, activityToRecreate: android.app.Activity? = null) {
        val desired: LocaleListCompat = AppLocales.fromTag(tag)
        val current: LocaleListCompat = AppCompatDelegate.getApplicationLocales()

        // Ничего не делаем, если уже установлена нужная локаль
        if (current.toLanguageTags() == desired.toLanguageTags()) {
            Timber.d("[settings_locale] no-op tag=%s", tag)
            return
        }

        Timber.i("[settings_locale] apply tag=%s locales=%s",
            tag, desired.toLanguageTags())

        // Это триггерит пересоздание Activity самим AppCompat, ручной recreate() не нужен
        AppCompatDelegate.setApplicationLocales(desired)
    }
}
