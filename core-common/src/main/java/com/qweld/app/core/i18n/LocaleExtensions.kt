package com.qweld.app.core.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Единая точка правды для активного языка приложения (то, что видит UI и ресурсы).
 * Порядок источников:
 * 1) AppCompatDelegate.getApplicationLocales() (app-locales)
 * 2) context.resources.configuration.locales[0]
 * 3) Locale.getDefault()
 *
 * Возвращает двубуквенный тег ("en" | "ru"), в lower-case.
 */
fun currentAppLocaleTag(context: Context): String {
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val primaryFromApp = appLocales.get(0)
    if (primaryFromApp != null) {
        return primaryFromApp.language.lowercase(Locale.ROOT)
    }
    val cfgLocales = context.resources.configuration.locales
    if (!cfgLocales.isEmpty) {
        return cfgLocales[0].language.lowercase(Locale.ROOT)
    }
    return Locale.getDefault().language.lowercase(Locale.ROOT)
}

/**
 * Безопасный фолбэк для контента: если запрошенный tag отсутствует,
 * используйте "en". Эта функция НЕ проверяет наличие файлов, она лишь
 * нормализует ожидаемые значения тега.
 */
fun normalizeContentTagOrEn(tag: String?): String {
    val t = (tag ?: "").lowercase(Locale.ROOT)
    return if (t == "ru" || t == "en") t else "en"
}

/**
 * Удобный помощник, чтобы получить тег для контента прямо из контекста.
 */
fun resolveContentLocaleTag(context: Context): String {
    return normalizeContentTagOrEn(currentAppLocaleTag(context))
}
