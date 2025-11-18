package com.qweld.app.core.i18n

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import timber.log.Timber

object LocaleController {
    annotation class currentLanguage

    fun currentLanguage(context: Context? = null): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val primaryFromApp = appLocales.get(0)
        if (primaryFromApp != null) {
            return normalizeContentTagOrEn(primaryFromApp.language)
        }

        val cfgLocales = context?.resources?.configuration?.locales
        if (cfgLocales != null && !cfgLocales.isEmpty) {
            return normalizeContentTagOrEn(cfgLocales[0].language)
        }

        return normalizeContentTagOrEn(Locale.getDefault().language)
    }

    fun apply(tag: String, activityToRecreate: Activity? = null) {
        val desired: LocaleListCompat = AppLocales.fromTag(tag)
        val current: LocaleListCompat = AppCompatDelegate.getApplicationLocales()

        // Ничего не делаем, если уже установлена нужная локаль
        if (current.toLanguageTags() == desired.toLanguageTags()) {
            Timber.d("[settings_locale] no-op tag=%s", tag)
            return
        }

        Timber.i(
            "[settings_locale] apply tag=%s locales=%s",
            tag,
            desired.toLanguageTags(),
        )

        // Это триггерит пересоздание Activity самим AppCompat, ручной recreate() не нужен
        AppCompatDelegate.setApplicationLocales(desired)
    }
}
