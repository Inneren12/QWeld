package com.qweld.core.model

import java.util.Locale

object GreetingProvider {
    fun greetingForLocale(locale: Locale): String {
        return when (locale.language) {
            "ru" -> "Привет, QWeld"
            else -> "Hello QWeld"
        }
    }
}
