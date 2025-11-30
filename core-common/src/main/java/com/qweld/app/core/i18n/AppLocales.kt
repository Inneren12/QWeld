package com.qweld.app.core.i18n

import androidx.core.os.LocaleListCompat

object AppLocales {
    const val TAG_SYSTEM = "system"

    /** Мапим наш строковый тег к LocaleListCompat. */
    fun fromTag(tag: String): LocaleListCompat {
        val norm = tag.trim().lowercase().replace('_', '-')
        return when (norm) {
            TAG_SYSTEM -> LocaleListCompat.getEmptyLocaleList() // «следовать системе»
            "ru", "ru-ru" -> LocaleListCompat.forLanguageTags("ru")
            "en", "en-us", "en-gb", "en-ca", "en-au" -> LocaleListCompat.forLanguageTags("en")
            // На случай, если пришёл другой валидный BCP‑47
            else -> LocaleListCompat.forLanguageTags(norm)
        }
    }
}
