package com.qweld.app.data.content

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentLocaleResolverTest {

  @Test
  fun resolvesRussianWhenExplicit() {
    val available = mapOf("en" to localeInfo(true), "ru" to localeInfo(true))

    val resolution =
      ContentLocaleResolver.resolve(
        appLocaleTag = "ru",
        systemLocales = listOf(Locale.ENGLISH),
        availableLocales = available,
      )

    assertEquals("ru", resolution.resolvedLocale)
    assertEquals(null, resolution.fallbackReason)
  }

  @Test
  fun fallsBackToSystemLocaleWhenAppIsSystem() {
    val available = mapOf("en" to localeInfo(true), "ru" to localeInfo(true))

    val resolution =
      ContentLocaleResolver.resolve(
        appLocaleTag = "system",
        systemLocales = listOf(Locale("ru", "UA"), Locale.ENGLISH),
        availableLocales = available,
      )

    assertEquals("ru", resolution.resolvedLocale)
    assertEquals(null, resolution.fallbackReason)
  }

  @Test
  fun fallsBackToEnglishWhenRequestedMissing() {
    val available = mapOf("en" to localeInfo(true))

    val resolution =
      ContentLocaleResolver.resolve(
        appLocaleTag = "ru",
        systemLocales = emptyList(),
        availableLocales = available,
      )

    assertEquals("en", resolution.resolvedLocale)
    assertEquals("missing_requested", resolution.fallbackReason)
  }

  @Test
  fun fallsBackToAnyWhenNoEnglish() {
    val available = mapOf("ru" to localeInfo(true))

    val resolution =
      ContentLocaleResolver.resolve(
        appLocaleTag = "en",
        systemLocales = emptyList(),
        availableLocales = available,
      )

    assertEquals("ru", resolution.resolvedLocale)
    assertEquals("missing_requested_and_fallback", resolution.fallbackReason)
  }

  @Test
  fun prefersLocalesWithBank() {
    val available = mapOf("ru" to localeInfo(false), "en" to localeInfo(true))

    val resolution =
      ContentLocaleResolver.resolve(
        appLocaleTag = "ru",
        systemLocales = emptyList(),
        availableLocales = available,
      )

    assertEquals("en", resolution.resolvedLocale)
    assertEquals("missing_requested", resolution.fallbackReason)
  }

  private fun localeInfo(hasBank: Boolean) =
    ContentIndexReader.Result.Locale(
      blueprintId = null,
      bankVersion = null,
      taskIds = emptyList(),
      hasBank = hasBank,
      hasTaskLabels = false,
    )
}
