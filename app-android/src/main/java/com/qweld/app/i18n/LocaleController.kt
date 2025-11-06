package com.qweld.app.i18n

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import timber.log.Timber

object LocaleController {

    /**
     * Применяет локаль ко всему приложению. Для Compose безопаснее
     * форснуть recreate у верхней активити.
     */
    fun apply(tag: String, activityToRecreate: Activity? = null) {
        val locales: LocaleListCompat = AppLocales.fromTag(tag)
        val current: LocaleListCompat = AppCompatDelegate.getApplicationLocales()
        // no-op, если локали уже совпадают (избегаем лишних перезапусков)
        if (current.toLanguageTags() == locales.toLanguageTags()) {
            Timber.d("[settings_locale] no-op tag=%s", tag)
            return
        }
        Timber.i("[settings_locale] apply tag=%s locales=%s", tag, locales.toLanguageTags())
        AppCompatDelegate.setApplicationLocales(locales)
        // Обычно AppCompat сам пересоздаёт активити. Если хочешь, оставь явно:
        // activityToRecreate?.recreate()
    }
}

