package com.qweld.app.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLocales {
    val supported = setOf("en", "ru")

    fun apply(tag: String) {
        val safe = tag.ifBlank { "en" }.lowercase()
        val final = if (safe in supported) safe else "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(final))
    }
}
