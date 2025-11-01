package com.qweld.app.data.content

import android.content.Context
import java.io.FileNotFoundException
import java.io.InputStream
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

  companion object {
    private const val INDEX_PATH = "questions/index.json"

    private val DEFAULT_JSON =
      Json {
        ignoreUnknownKeys = true
        prettyPrint = false
      }
  }
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
