package com.qweld.app.core.i18n

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
        Timber.d(
            "[current_language_debug] appLocales=%s primary=%s",
            appLocales.toLanguageTags(),
            primaryFromApp,
        )
        if (primaryFromApp != null) {
            val normalized = normalizeContentTagOrEn(primaryFromApp.language)
            Timber.i(
                "[current_language] source=appcompat raw=%s normalized=%s",
                primaryFromApp.language,
                normalized,
            )
            return normalized
        }

        val cfgLocales = context?.resources?.configuration?.locales
        val primaryFromConfig = if (cfgLocales != null && !cfgLocales.isEmpty) cfgLocales[0] else null
        if (primaryFromConfig != null) {
            val normalized = normalizeContentTagOrEn(primaryFromConfig.language)
            Timber.i(
                "[current_language] source=config raw=%s normalized=%s",
                primaryFromConfig.language,
                normalized,
            )
            return normalized
        }

        val normalizedDefault = normalizeContentTagOrEn(Locale.getDefault().language)
        Timber.i(
            "[current_language] source=default raw=%s normalized=%s",
            Locale.getDefault().language,
            normalizedDefault,
        )
        return normalizedDefault
    }

    fun apply(@Suppress("UNUSED_PARAMETER") tag: String) {
        val desired: LocaleListCompat = AppLocales.fromTag("en")
        val current: LocaleListCompat = AppCompatDelegate.getApplicationLocales()

        if (current.toLanguageTags() == desired.toLanguageTags()) {
            Timber.d("[settings_locale] no-op locale=disabled")
            return
        }

        Timber.i("[settings_locale] apply locale=en locales=%s", desired.toLanguageTags())

        // Это триггерит пересоздание Activity самим AppCompat, ручной recreate() не нужен
        AppCompatDelegate.setApplicationLocales(desired)
    }
}
