package com.qweld.app.data.content.questions

import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import timber.log.Timber

class IntegrityMismatchException(
  val path: String,
  val expected: String?,
  val actual: String?,
) : Exception("Integrity mismatch for $path (expected=$expected actual=$actual)")

class AssetIntegrityGuard(
  private val manifest: IndexParser.Manifest,
  private val opener: (String) -> InputStream,
  cacheSize: Int = 128,
  private val digestFactory: () -> MessageDigest = { MessageDigest.getInstance("SHA-256") },
) {

  data class IntegrityMetrics(
    var hit: Int = 0,
    var miss: Int = 0,
    var fallback: Int = 0,
    var fail: Int = 0,
  )

  val metrics = IntegrityMetrics()

  private val cache =
    object : LinkedHashMap<String, String>(cacheSize, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean {
        return size > cacheSize
      }
    }

  fun readVerified(pathPreferred: String, altPath: String? = null): ByteArray {
    val primary = verify(pathPreferred)
    if (primary.success) {
      handleSuccess(primary, isFallback = false)
      return primary.bytes!!
    }

    logMiss(primary)

    if (altPath != null) {
      val fallback = verify(altPath)
      if (fallback.success) {
        handleSuccess(fallback, isFallback = true)
        return fallback.bytes!!
      }
      logMiss(fallback)
      metrics.fail += 1
      logFailure(fallback)
      throw IntegrityMismatchException(
        path = fallback.path,
        expected = fallback.expected,
        actual = fallback.actual,
      )
    }

    metrics.fail += 1
    logFailure(primary)
    throw IntegrityMismatchException(
      path = primary.path,
      expected = primary.expected,
      actual = primary.actual,
    )
  }

  private fun handleSuccess(result: VerificationResult, isFallback: Boolean) {
    if (result.cached) {
      metrics.hit += 1
    } else if (!isFallback) {
      metrics.miss += 1
    }

    if (isFallback) {
      metrics.fallback += 1
    }

    val status = when {
      isFallback -> "fallback"
      result.cached -> "hit"
      else -> "miss"
    }
    val sha = result.actual ?: result.expected ?: "unknown"
    Timber.i("[integrity_check] path=%s status=%s sha=%s", result.path, status, sha)
  }

  private fun logMiss(result: VerificationResult) {
    val expectedValue = result.expected ?: "unknown"
    val actualValue = result.actual ?: "missing"
    Timber.w("[integrity_miss] path=%s expected=%s actual=%s", result.path, expectedValue, actualValue)
  }

  private fun logFailure(result: VerificationResult) {
    val sha = result.actual ?: "missing"
    Timber.w("[integrity_check] path=%s status=fail sha=%s", result.path, sha)
  }

  private fun verify(path: String): VerificationResult {
    val normalized = manifest.normalize(path)
    val expected = manifest.expectedFor(path)?.lowercase(Locale.US)
    val cachedSha = expected?.let { cache[normalized]?.takeIf { cached -> cached.equals(it, ignoreCase = true) } }
    if (cachedSha != null) {
      val bytes = readBytes(path)
      if (bytes == null) {
        cache.remove(normalized)
        return VerificationResult(path, null, expected, null, cached = false, success = false)
      }
      return VerificationResult(
        path = path,
        bytes = bytes,
        expected = expected,
        actual = cachedSha,
        cached = true,
        success = true,
      )
    }

    val bytes = readBytes(path) ?: return VerificationResult(path, null, expected, null, cached = false, success = false)
    val actual = digest(bytes)
    val normalizedSha = actual.lowercase(Locale.US)
    return if (!expected.isNullOrBlank() && actual.equals(expected, ignoreCase = true)) {
      cache[normalized] = normalizedSha
      VerificationResult(path, bytes, expected, normalizedSha, cached = false, success = true)
    } else {
      cache.remove(normalized)
      VerificationResult(path, null, expected, normalizedSha, cached = false, success = false)
    }
  }

  private fun readBytes(path: String): ByteArray? {
    return try {
      opener(path).use { stream -> stream.readBytes() }
    } catch (error: FileNotFoundException) {
      null
    } catch (error: Exception) {
      Timber.e(error, "[integrity_check] failed to read asset path=%s", path)
      null
    }
  }

  private fun digest(bytes: ByteArray): String {
    val digest = digestFactory().digest(bytes)
    val builder = StringBuilder(digest.size * 2)
    for (byte in digest) {
      val value = byte.toInt() and 0xFF
      builder.append(HEX[value ushr 4])
      builder.append(HEX[value and 0x0F])
    }
    return builder.toString()
  }

  private data class VerificationResult(
    val path: String,
    val bytes: ByteArray?,
    val expected: String?,
    val actual: String?,
    val cached: Boolean,
    val success: Boolean,
  )

  companion object {
    private val HEX = "0123456789abcdef".toCharArray()
  }
}
