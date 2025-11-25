package com.qweld.app.data.content

import androidx.annotation.VisibleForTesting
import java.util.Locale
import timber.log.Timber

class ContentLocaleResolver(
  private val contentIndexReader: ContentIndexReader,
  private val appLocaleProvider: () -> String,
  private val systemLocalesProvider: () -> List<Locale>,
  private val fallbackLocale: String = DEFAULT_FALLBACK_LOCALE,
) {

  data class Resolution(
    val appLocaleTag: String,
    val requestedLocale: String,
    val resolvedLocale: String,
    val availableLocales: Set<String>,
    val fallbackReason: String?,
  )

  @Volatile private var cachedIndex: ContentIndexReader.Result? = null

  fun currentContentLocale(): String {
    val index = readIndex()
    val systemLocales = systemLocalesProvider()
    val resolution =
      resolve(
        appLocaleTag = appLocaleProvider().ifBlank { DEFAULT_APP_LOCALE_TAG },
        systemLocales = systemLocales,
        availableLocales = index?.locales.orEmpty(),
        fallbackLocale = fallbackLocale,
      )

    Timber.i(
      "[content_locale] app=%s system=%s chosen=%s available=%s",
      resolution.appLocaleTag,
      systemLocales.joinToString(prefix = "[", postfix = "]"),
      resolution.resolvedLocale,
      index?.locales?.keys?.joinToString(prefix = "[", postfix = "]"),
    )

    if (resolution.fallbackReason != null) {
      Timber.w(
        "[content_locale_fallback] requested=%s fallback=%s reason=%s",
        resolution.requestedLocale,
        resolution.resolvedLocale,
        resolution.fallbackReason,
      )
    }

    return resolution.resolvedLocale
  }

  fun availableLocales(): Set<String> {
    return readIndex()?.locales?.keys?.toSet() ?: emptySet()
  }

  private fun readIndex(): ContentIndexReader.Result? {
    cachedIndex?.let { return it }
    synchronized(this) {
      cachedIndex?.let { return it }
      val loaded = contentIndexReader.read()
      if (loaded != null) {
        contentIndexReader.logContentInfo(loaded)
        cachedIndex = loaded
      }
      return loaded
    }
  }

  companion object {
    internal const val DEFAULT_FALLBACK_LOCALE = "en"
    private const val DEFAULT_APP_LOCALE_TAG = "system"

    @VisibleForTesting
    internal fun resolve(
      appLocaleTag: String,
      systemLocales: List<Locale>,
      availableLocales: Map<String, ContentIndexReader.Result.Locale>,
      fallbackLocale: String = DEFAULT_FALLBACK_LOCALE,
    ): Resolution {
      val normalizedAppTag = appLocaleTag.trim().lowercase(Locale.US).ifBlank { DEFAULT_APP_LOCALE_TAG }
      val candidates = normalizeAvailable(availableLocales)
      val request =
        when (normalizedAppTag) {
          "ru", "en" -> normalizedAppTag
          DEFAULT_APP_LOCALE_TAG -> systemLocales.firstNotNullOfOrNull { locale ->
            locale.language.takeIf { it.isNotBlank() }?.lowercase(Locale.US)
          } ?: fallbackLocale
          else -> normalizedAppTag
        }
      val resolved = resolveAgainstAvailable(request, candidates, fallbackLocale)
      val fallbackReason = resolved.second
      return Resolution(
        appLocaleTag = normalizedAppTag,
        requestedLocale = request,
        resolvedLocale = resolved.first,
        availableLocales = candidates,
        fallbackReason = fallbackReason,
      )
    }

    private fun normalizeAvailable(
      availableLocales: Map<String, ContentIndexReader.Result.Locale>,
    ): Set<String> {
      val withBank = availableLocales.filterValues { it.hasBank }.keys
      val source = if (withBank.isNotEmpty()) withBank else availableLocales.keys
      return source.map { code -> code.lowercase(Locale.US) }.toSet()
    }

    private fun resolveAgainstAvailable(
      requested: String,
      available: Set<String>,
      fallbackLocale: String,
    ): Pair<String, String?> {
      if (available.isEmpty()) return fallbackLocale.lowercase(Locale.US) to "no_available_locales"
      if (available.contains(requested)) return requested to null
      val fallback = when {
        available.contains(fallbackLocale) -> fallbackLocale
        else -> available.first()
      }
      val reason = if (available.contains(fallbackLocale)) "missing_requested" else "missing_requested_and_fallback"
      return fallback to reason
    }
  }
}
