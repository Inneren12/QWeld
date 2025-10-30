package com.qweld.app

import com.qweld.core.model.GreetingProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingProviderTest {
    @Test
    fun greetingProvider_returnsLocalizedMessages() {
        assertEquals("Hello QWeld", GreetingProvider.greetingForLocale(Locale.ENGLISH))
        assertEquals("Привет, QWeld", GreetingProvider.greetingForLocale(Locale.forLanguageTag("ru")))
    }
}
