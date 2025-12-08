package com.qweld.app.feature.exam.data

import com.qweld.app.feature.exam.data.blueprint.AssetBlueprintProvider
import com.qweld.app.feature.exam.data.blueprint.BlueprintId
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Ignore

@Ignore("Pending blueprint schema alignment")
class BlueprintProviderTest {
  private val assetOpener: (String) -> java.io.InputStream? = { path ->
    val file = File("app-android/src/main/assets/$path")
    file.takeIf { it.exists() }?.inputStream()
  }

  @Test
  fun `loads default welder blueprint`() {
    val provider = AssetBlueprintProvider(assetOpener)

    val blueprint = provider.load(BlueprintId.WELDER_IP_SK_202404)

    assertEquals(125, blueprint.totalQuestions)
    assertTrue(blueprint.taskQuotas.any { it.taskId == "A-1" && it.required > 0 })
  }

  @Test
  fun `loads alternate schema`() {
    val provider = AssetBlueprintProvider(assetOpener)

    val blueprint = provider.load(BlueprintId.WELDER_IP_2024)

    assertEquals(125, blueprint.totalQuestions)
    assertTrue(blueprint.taskQuotas.all { it.blockId.isNotBlank() })
  }
}
