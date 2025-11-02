package com.qweld.app.data.content.questions

import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class AssetIntegrityGuardTest {

  private val parser = IndexParser()
  private val logs = mutableListOf<String>()

  @Before
  fun setUp() {
    Timber.plant(object : Timber.Tree() {
      override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        logs += message
      }
    })
  }

  @After
  fun tearDown() {
    Timber.uprootAll()
    logs.clear()
  }

  @Test
  fun hitUsesCachedVerification() {
    val payload = "[{\"id\":\"Q1\"}]".toByteArray()
    val sha = sha256(payload)
    val manifest =
      parser.parse(
        """
          {
            "files": {
              "questions/en/bank.v1.json": { "sha256": "$sha" }
            }
          }
        """
          .trimIndent(),
      )
    val loader = mapOf("questions/en/bank.v1.json" to payload).toLoader()
    val guard = AssetIntegrityGuard(manifest, loader)

    val first = guard.readVerified("questions/en/bank.v1.json")
    val second = guard.readVerified("questions/en/bank.v1.json")

    assertEquals(payload.toList(), first.toList())
    assertEquals(payload.toList(), second.toList())
    assertEquals(1, guard.metrics.hit)
    assertEquals(1, guard.metrics.miss)
    assertEquals(0, guard.metrics.fallback)
    assertEquals(0, guard.metrics.fail)
  }

  @Test
  fun fallbackReturnsAlternatePath() {
    val primary = "bad".toByteArray()
    val alternate = "good".toByteArray()
    val manifest =
      parser.parse(
        """
          {
            "files": {
              "questions/en/bank.v1.json": { "sha256": "${sha256(alternate)}" },
              "questions/en/bank.v1.json.gz": { "sha256": "${sha256(primary)}" }
            }
          }
        """
          .trimIndent(),
      )
    val loader =
      mapOf(
          "questions/en/bank.v1.json" to alternate,
          "questions/en/bank.v1.json.gz" to "wrong".toByteArray(),
        )
        .toLoader()
    val guard = AssetIntegrityGuard(manifest, loader)

    val data = guard.readVerified("questions/en/bank.v1.json.gz", altPath = "questions/en/bank.v1.json")

    assertEquals(alternate.toList(), data.toList())
    assertEquals(0, guard.metrics.hit)
    assertEquals(0, guard.metrics.miss)
    assertEquals(1, guard.metrics.fallback)
    assertEquals(0, guard.metrics.fail)
  }

  @Test
  fun failThrowsMismatchException() {
    val payload = "bad".toByteArray()
    val manifest =
      parser.parse(
        """
          {
            "files": {
              "questions/en/bank.v1.json": { "sha256": "${sha256("good".toByteArray())}" }
            }
          }
        """
          .trimIndent(),
      )
    val loader = mapOf("questions/en/bank.v1.json" to payload).toLoader()
    val guard = AssetIntegrityGuard(manifest, loader)

    assertFailsWith<IntegrityMismatchException> {
      guard.readVerified("questions/en/bank.v1.json")
    }
    assertEquals(1, guard.metrics.fail)
    val missLog = logs.firstOrNull { it.startsWith("[integrity] miss") }
    check(missLog?.contains("expected=") == true)
  }

  private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
  }

  private fun Map<String, ByteArray>.toLoader(): (String) -> InputStream {
    return { path ->
      val data = this[path] ?: throw FileNotFoundException(path)
      data.inputStream()
    }
  }
}
