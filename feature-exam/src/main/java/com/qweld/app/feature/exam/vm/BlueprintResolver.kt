package com.qweld.app.feature.exam.vm

import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.feature.exam.data.blueprint.BlueprintCatalog
import com.qweld.app.feature.exam.data.blueprint.BlueprintId
import com.qweld.app.feature.exam.data.blueprint.BlueprintProvider
import kotlin.math.min

class BlueprintResolver(
  private val provider: BlueprintProvider,
  private val defaultId: BlueprintId = BlueprintCatalog.DEFAULT_ID,
  private val adaptiveSize: Int = DEFAULT_ADAPTIVE_SIZE,
) {
  fun forMode(mode: ExamMode, practiceSize: Int): ExamBlueprint {
    val base = provider.load(defaultId)
    return when (mode) {
      ExamMode.PRACTICE -> buildTrimmedBlueprint(base, practiceSize)
      ExamMode.ADAPTIVE -> buildTrimmedBlueprint(base, adaptiveSize)
      ExamMode.IP_MOCK -> base
    }
  }

  private fun buildTrimmedBlueprint(base: ExamBlueprint, requestedSize: Int): ExamBlueprint {
    val sanitizedSize = requestedSize.coerceAtLeast(1)
    if (sanitizedSize >= base.totalQuestions) return base
    var remaining = sanitizedSize
    val quotas = mutableListOf<TaskQuota>()
    for (quota in base.taskQuotas) {
      if (remaining <= 0) break
      val take = min(remaining, quota.required)
      if (take > 0) {
        quotas += TaskQuota(quota.taskId, quota.blockId, take)
        remaining -= take
      }
    }
    if (remaining > 0 && base.taskQuotas.isNotEmpty()) {
      val last = base.taskQuotas.last()
      quotas += TaskQuota(last.taskId, last.blockId, remaining)
    }
    return ExamBlueprint(totalQuestions = sanitizedSize, taskQuotas = quotas)
  }

  companion object {
    const val DEFAULT_ADAPTIVE_SIZE: Int = PracticeConfig.DEFAULT_SIZE
  }
}

/**
 * Simple provider used in unit tests or as a fallback when assets are unavailable.
 */
class StaticBlueprintProvider(private val blueprint: ExamBlueprint = ExamBlueprint.default()) :
  BlueprintProvider {
  override fun load(id: BlueprintId): ExamBlueprint = blueprint
}
