package com.qweld.app.data.content

import android.content.Context
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

class ContentIndexReader
@JvmOverloads
constructor(
  private val assetLoader: AssetLoader,
  private val json: Json = DEFAULT_JSON,
) {

  interface AssetLoader {
    fun open(path: String): InputStream
  }

  constructor(context: Context, json: Json = DEFAULT_JSON) : this(
    assetLoader = object : AssetLoader {
      private val appContext = context.applicationContext

      override fun open(path: String): InputStream {
        return appContext.assets.open(path)
      }
    },
    json = json,
  )

  data class Result(
    val index: ContentIndex,
    val rawJson: String,
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
    return try {
      val payload = assetLoader.open(INDEX_PATH).use { stream ->
        stream.bufferedReader().use { reader -> reader.readText() }
      }
      val index = json.decodeFromString(ContentIndex.serializer(), payload)
      Result(index = index, rawJson = payload)
    } catch (error: FileNotFoundException) {
      null
    } catch (error: Exception) {
      Timber.e(error, "[content_index] failed to parse index asset")
      null
    }
  }

  fun verify(result: Result? = read()): List<Mismatch> {
    val mismatches = mutableListOf<Mismatch>()
    val resolved = result
    if (resolved == null) {
      mismatches +=
        Mismatch(
          locale = null,
          path = INDEX_PATH,
          expectedHash = null,
          actualHash = null,
          reason = Mismatch.Reason.INDEX_MISSING,
        )
      Timber.w("[content_verify] ok=false mismatches=%d", mismatches.size)
      return mismatches
    }

    resolved.index.locales.forEach { (locale, localeInfo) ->
      val bankPath = "${QUESTIONS_ROOT}/$locale/$BANK_FILENAME"
      val bankHash = calculateHash(bankPath)
      if (bankHash == null) {
        mismatches +=
          Mismatch(
            locale = locale,
            path = bankPath,
            expectedHash = localeInfo.sha256.bank,
            actualHash = null,
            reason = Mismatch.Reason.FILE_MISSING,
          )
      } else if (!bankHash.equals(localeInfo.sha256.bank, ignoreCase = true)) {
        mismatches +=
          Mismatch(
            locale = locale,
            path = bankPath,
            expectedHash = localeInfo.sha256.bank,
            actualHash = bankHash,
            reason = Mismatch.Reason.HASH_MISMATCH,
          )
      }

      localeInfo.sha256.tasks.forEach { (taskId, expectedHash) ->
        val taskPath = "${QUESTIONS_ROOT}/$locale/$TASKS_DIR/$taskId.json"
        val actualHash = calculateHash(taskPath)
        if (actualHash == null) {
          mismatches +=
            Mismatch(
              locale = locale,
              path = taskPath,
              expectedHash = expectedHash,
              actualHash = null,
              reason = Mismatch.Reason.FILE_MISSING,
            )
        } else if (!actualHash.equals(expectedHash, ignoreCase = true)) {
          mismatches +=
            Mismatch(
              locale = locale,
              path = taskPath,
              expectedHash = expectedHash,
              actualHash = actualHash,
              reason = Mismatch.Reason.HASH_MISMATCH,
            )
        }
      }
    }

    Timber.i("[content_verify] ok=%s mismatches=%d", mismatches.isEmpty(), mismatches.size)
    return mismatches
  }

  private fun calculateHash(path: String): String? {
    return try {
      assetLoader.open(path).use { stream -> stream.toSha256() }
    } catch (error: FileNotFoundException) {
      null
    } catch (error: Exception) {
      Timber.e(error, "[content_verify] failed to hash asset path=%s", path)
      null
    }
  }

  companion object {
    private const val INDEX_PATH = "questions/index.json"
    private const val QUESTIONS_ROOT = "questions"
    private const val BANK_FILENAME = "bank.v1.json"
    private const val TASKS_DIR = "tasks"

    private val DEFAULT_JSON =
      Json {
        ignoreUnknownKeys = true
        prettyPrint = false
      }
  }
}

private fun InputStream.toSha256(): String {
  val digest = MessageDigest.getInstance("SHA-256")
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  while (true) {
    val read = read(buffer)
    if (read == -1) {
      break
    }
    digest.update(buffer, 0, read)
  }
  return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}

@Serializable
data class ContentIndex(
  val schema: String,
  val locales: Map<String, ContentIndexLocale>,
  val generatedAt: String,
)

@Serializable
data class ContentIndexLocale(
  val total: Int,
  val tasks: Map<String, Int>,
  @SerialName("sha256") val sha256: ContentIndexLocaleHashes,
)

@Serializable
data class ContentIndexLocaleHashes(
  val bank: String,
  val tasks: Map<String, String>,
)
