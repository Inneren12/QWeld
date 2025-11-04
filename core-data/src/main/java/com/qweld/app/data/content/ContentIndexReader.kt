package com.qweld.app.data.content

import android.content.Context
import com.qweld.app.data.content.questions.AssetIntegrityGuard
import com.qweld.app.data.content.questions.IndexParser
import com.qweld.app.data.content.questions.IntegrityMismatchException
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.LinkedHashMap
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.parseToJsonElement
import kotlinx.serialization.json.put
import timber.log.Timber

class ContentIndexReader
@JvmOverloads
constructor(
  private val assetLoader: AssetLoader,
  private val json: Json = DEFAULT_JSON,
  private val parser: IndexParser = IndexParser(json),
) {

  interface AssetLoader {
    fun open(path: String): InputStream
    fun list(path: String): List<String>
  }

  constructor(context: Context, json: Json = DEFAULT_JSON) : this(
    assetLoader = object : AssetLoader {
      private val appContext = context.applicationContext

      override fun open(path: String): InputStream {
        return appContext.assets.open(path)
      }

      override fun list(path: String): List<String> {
        return try {
          appContext.assets.list(path)?.toList() ?: emptyList()
        } catch (_: Exception) {
          emptyList()
        }
      }
    },
    json = json,
    parser = IndexParser(json),
  )

  class Result internal constructor(
    val locales: Map<String, Locale>,
    val rawJson: String,
    internal val manifests: Map<String, IndexParser.Manifest>,
  ) {
    data class Locale(
      val manifestPath: String,
      val blueprintId: String?,
      val bankVersion: String?,
      val files: List<FileEntry>,
    )
  }

  data class FileEntry(
    val path: String,
    val sha256: String,
  )

  data class Mismatch(
    val locale: String?,
    val path: String,
    val expectedHash: String?,
    val actualHash: String?,
    val reason: Reason,
  ) {
    enum class Reason {
      INDEX_MISSING,
      FILE_MISSING,
      HASH_MISMATCH,
    }
  }

  fun read(): Result? {
    val candidates =
      assetLoader
        .list(QUESTIONS_ROOT)
        .filter { isLocaleCandidate(it) }
        .sorted()
    if (candidates.isEmpty()) {
      Timber.w("[content_index] no locale directories under %s", QUESTIONS_ROOT)
      return null
    }

    val locales = LinkedHashMap<String, Result.Locale>()
    val manifests = LinkedHashMap<String, IndexParser.Manifest>()
    val elements = LinkedHashMap<String, JsonObject>()

    candidates.forEach { candidate ->
      val manifestPath = manifestPath(candidate)
      val localeCode = candidate.lowercase(Locale.US)
      val payload =
        try {
          assetLoader.open(manifestPath).use { stream ->
            stream.bufferedReader().use { reader -> reader.readText() }
          }
        } catch (error: FileNotFoundException) {
          Timber.w(
            error,
            "[content_index] missing manifest locale=%s path=%s",
            localeCode,
            manifestPath,
          )
          return@forEach
        } catch (error: Exception) {
          Timber.e(
            error,
            "[content_index] failed to read manifest locale=%s path=%s",
            localeCode,
            manifestPath,
          )
          return@forEach
        }

      val manifest =
        try {
          parser.parse(payload)
        } catch (error: Exception) {
          Timber.e(
            error,
            "[content_index] failed to parse manifest locale=%s",
            localeCode,
          )
          return@forEach
        }

      val files =
        manifest
          .expectedMap()
          .entries
          .map { (path, hash) -> FileEntry(path = path, sha256 = hash) }
          .sortedBy { it.path }

      locales[localeCode] =
        Result.Locale(
          manifestPath = manifestPath,
          blueprintId = manifest.blueprintId,
          bankVersion = manifest.bankVersion,
          files = files,
        )
      manifests[localeCode] = manifest
      runCatching { json.parseToJsonElement(payload).jsonObject }
        .onSuccess { element -> elements[localeCode] = element }
        .onFailure { error ->
          Timber.w(
            error,
            "[content_index] failed to parse manifest locale=%s for aggregation",
            localeCode,
          )
        }
    }

    if (locales.isEmpty()) {
      Timber.w("[content_index] unable to load any locale manifests candidates=%s", candidates)
      return null
    }

    val aggregated =
      buildJsonObject {
        locales.keys.sorted().forEach { locale ->
          elements[locale]?.let { element -> put(locale, element) }
        }
      }
    val rawJson = json.encodeToString(JsonObject.serializer(), aggregated)
    return Result(locales = locales, rawJson = rawJson, manifests = manifests)
  }

  fun verify(result: Result? = read()): List<Mismatch> {
    val mismatches = mutableListOf<Mismatch>()
    val localeSources =
      assetLoader
        .list(QUESTIONS_ROOT)
        .filter { isLocaleCandidate(it) }
        .associateBy({ it.lowercase(Locale.US) }, { it })

    if (localeSources.isEmpty()) {
      mismatches +=
        Mismatch(
          locale = null,
          path = QUESTIONS_ROOT,
          expectedHash = null,
          actualHash = null,
          reason = Mismatch.Reason.INDEX_MISSING,
        )
      Timber.w("[content_verify] ok=false mismatches=%d", mismatches.size)
      return mismatches
    }

    val resolved = result
    if (resolved == null) {
      localeSources.forEach { (locale, source) ->
        mismatches +=
          Mismatch(
            locale = locale,
            path = manifestPath(source),
            expectedHash = null,
            actualHash = null,
            reason = Mismatch.Reason.INDEX_MISSING,
          )
      }
      Timber.w("[content_verify] ok=false mismatches=%d", mismatches.size)
      return mismatches
    }

    localeSources.forEach { (locale, source) ->
      val manifest = resolved.manifests[locale]
      val localeInfo = resolved.locales[locale]
      if (manifest == null || localeInfo == null) {
        mismatches +=
          Mismatch(
            locale = locale,
            path = manifestPath(source),
            expectedHash = null,
            actualHash = null,
            reason = Mismatch.Reason.INDEX_MISSING,
          )
        return@forEach
      }

      val guard =
        AssetIntegrityGuard(
          manifest = manifest,
          opener = { path -> assetLoader.open(path) },
        )

      manifest.paths().forEach { path ->
        try {
          guard.readVerified(path)
        } catch (error: IntegrityMismatchException) {
          val reason =
            if (error.actual.isNullOrBlank()) {
              Mismatch.Reason.FILE_MISSING
            } else {
              Mismatch.Reason.HASH_MISMATCH
            }
          mismatches +=
            Mismatch(
              locale = locale,
              path = error.path,
              expectedHash = error.expected,
              actualHash = error.actual,
              reason = reason,
            )
        } catch (error: Exception) {
          Timber.e(error, "[content_verify] failed to verify path=%s locale=%s", path, locale)
          mismatches +=
            Mismatch(
              locale = locale,
              path = path,
              expectedHash = manifest.expectedFor(path),
              actualHash = null,
              reason = Mismatch.Reason.FILE_MISSING,
            )
        }
      }
    }

    Timber.i("[content_verify] ok=%s mismatches=%d", mismatches.isEmpty(), mismatches.size)
    return mismatches
  }

  private fun manifestPath(source: String): String {
    return "$QUESTIONS_ROOT/$source/$INDEX_FILENAME"
  }

  private fun isLocaleCandidate(name: String): Boolean {
    if (name.isBlank()) return false
    var letterCount = 0
    for (ch in name) {
      if (ch.isLetter()) {
        letterCount += 1
        continue
      }
      if (ch == '-' || ch == '_') {
        continue
      }
      return false
    }
    return letterCount >= 2
  }

  companion object {
    private const val QUESTIONS_ROOT = "questions"
    private const val INDEX_FILENAME = "index.json"

    private val DEFAULT_JSON =
      Json {
        ignoreUnknownKeys = true
        prettyPrint = true
      }
  }
}
