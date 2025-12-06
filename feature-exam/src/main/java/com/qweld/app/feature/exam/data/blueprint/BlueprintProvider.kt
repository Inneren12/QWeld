package com.qweld.app.feature.exam.data.blueprint

import android.content.Context
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.feature.exam.data.BlueprintJsonLoader
import java.io.InputStream

/** Identifies a concrete blueprint variant that can be loaded from assets or other sources. */
enum class BlueprintId {
  WELDER_IP_SK_202404,
  WELDER_IP_2024,
}

/** Simple abstraction for retrieving decoded [ExamBlueprint] instances. */
fun interface BlueprintProvider {
  fun load(id: BlueprintId = BlueprintCatalog.DEFAULT_ID): ExamBlueprint
}

/**
 * Maps [BlueprintId] values to asset paths. Adding new blueprints only requires updating this map
 * rather than sprinkling literals throughout the codebase.
 */
object BlueprintCatalog {
  const val ASSET_PREFIX = "blueprints/"
  val DEFAULT_ID: BlueprintId = BlueprintId.WELDER_IP_SK_202404

  private val pathMap:
    Map<BlueprintId, String> =
      mapOf(
        BlueprintId.WELDER_IP_SK_202404 to "${ASSET_PREFIX}welder_ip_sk_202404.json",
        BlueprintId.WELDER_IP_2024 to "${ASSET_PREFIX}welder_ip_2024.json",
      )

  fun pathFor(id: BlueprintId): String = pathMap[id] ?: error("No blueprint path for $id")
}

/**
 * Loads blueprints from the app's assets. Results are cached per-id to avoid redundant decoding.
 */
class AssetBlueprintProvider(
  private val assetOpener: (String) -> InputStream?,
  private val loader: BlueprintJsonLoader = BlueprintJsonLoader(),
) : BlueprintProvider {

  constructor(context: Context, loader: BlueprintJsonLoader = BlueprintJsonLoader()) : this(
    assetOpener = { path -> kotlin.runCatching { context.assets.open(path) }.getOrNull() },
    loader = loader,
  )

  private val cache = mutableMapOf<BlueprintId, ExamBlueprint>()

  override fun load(id: BlueprintId): ExamBlueprint {
    return cache.getOrPut(id) {
      loader.loadFromAssets(assetOpener, BlueprintCatalog.pathFor(id))
    }
  }
}
