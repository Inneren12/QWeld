package com.qweld.app.feature.exam.data

import android.content.Context
import java.io.InputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import com.qweld.app.domain.exam.ExamBlueprint

class AppRulesLoader(
  private val assetOpener: (String) -> InputStream?,
  private val blueprintLoader: BlueprintJsonLoader = BlueprintJsonLoader(),
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  constructor(context: Context, blueprintLoader: BlueprintJsonLoader = BlueprintJsonLoader()) : this(
    assetOpener = { path -> kotlin.runCatching { context.assets.open(path) }.getOrNull() },
    blueprintLoader = blueprintLoader,
  )

  fun load(path: String): LoadedRules {
    val payload =
      assetOpener(path)?.use { stream ->
        stream.bufferedReader().use { it.readText() }
      } ?: throw IllegalArgumentException("Rules asset not found: $path")

    val dto = json.decodeFromString(RulesDto.serializer(), payload)
    Timber.i("[rules_load] path=%s id=%s version=%s", path, dto.id, dto.version ?: "unknown")
    val blueprintPath = normalizeBlueprintPath(dto.blueprintPath ?: dto.blueprint)
      ?: throw IllegalArgumentException("Rules missing blueprint reference")
    val blueprint = blueprintLoader.loadFromAssets(assetOpener, blueprintPath)
    return LoadedRules(
      id = dto.id,
      version = dto.version,
      blueprintPath = blueprintPath,
      blueprint = blueprint,
    )
  }

  data class LoadedRules(
    val id: String,
    val version: String?,
    val blueprintPath: String,
    val blueprint: ExamBlueprint,
  )

  @Serializable
  private data class RulesDto(
    val id: String,
    val version: String? = null,
    @SerialName("blueprint") val blueprint: String? = null,
    @SerialName("blueprintPath") val blueprintPath: String? = null,
  )

  private fun normalizeBlueprintPath(path: String?): String? {
    if (path == null) return null
    return when {
      path.startsWith("app-android/src/main/assets/") -> path.removePrefix("app-android/src/main/assets/")
      path.startsWith("content/") -> path.removePrefix("content/")
      else -> path
    }
  }
}
