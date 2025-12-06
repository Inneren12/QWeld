package com.qweld.app.domain.exam

import com.qweld.app.domain.exam.util.DefaultRandomProvider
import com.qweld.app.domain.exam.util.Hash64
import com.qweld.app.domain.exam.util.RandomProvider
import com.qweld.app.domain.exam.util.WeightedSampler
import kotlin.math.max
import kotlin.math.min

/**
 * Distributes an exam's total question count across tasks using user performance to bias the
 * allocation. Tasks with weaker performance (lower historical accuracy) are scheduled first and
 * receive more slots when the total count is limited. The final blueprint remains deterministic
 * for a given seed, stats snapshot, and source blueprint.
 */
class AdaptiveQuotaAllocator(
  private val randomProvider: RandomProvider = DefaultRandomProvider,
) {

  data class TaskSnapshot(
    val quota: TaskQuota,
    val available: Int,
    val weakness: Double,
  )

  sealed class AllocationResult {
    data class Ok(val blueprint: ExamBlueprint) : AllocationResult()
    data class Err(val deficit: Deficit) : AllocationResult()
  }

  data class Deficit(val taskId: String, val required: Int, val available: Int)

  fun allocate(
    blueprint: ExamBlueprint,
    tasks: List<TaskSnapshot>,
    seed: AttemptSeed,
  ): AllocationResult {
    if (tasks.isEmpty()) return AllocationResult.Err(Deficit("", blueprint.totalQuestions, 0))
    val availableTotal = tasks.sumOf { it.available }
    if (availableTotal < blueprint.totalQuestions) {
      val first = tasks.minByOrNull { it.available }
      return AllocationResult.Err(
        Deficit(
          taskId = first?.quota?.taskId ?: "",
          required = blueprint.totalQuestions,
          available = availableTotal,
        )
      )
    }

    val sampler = WeightedSampler(randomProvider.pcg32(Hash64.hash(seed.value, "ADAPTIVE_TASKS")))
    val orderedTasks = sampler.order(tasks) { max(it.weakness, 0.05) }.map { it.item }
    val allocation = mutableMapOf<String, Int>()
    orderedTasks.forEach { allocation[it.quota.taskId] = 0 }
    var remaining = blueprint.totalQuestions
    var progressed: Boolean
    val caps = orderedTasks.associate { task ->
      val cap = min(task.available, max(1, blueprint.totalQuestions))
      task.quota.taskId to cap
    }

    while (remaining > 0) {
      progressed = false
      for (task in orderedTasks) {
        val current = allocation.getValue(task.quota.taskId)
        val cap = caps.getValue(task.quota.taskId)
        if (current < cap) {
          allocation[task.quota.taskId] = current + 1
          remaining--
          progressed = true
          if (remaining == 0) break
        }
      }
      if (!progressed) break
    }

    if (remaining > 0) {
      val stuck = orderedTasks.firstOrNull()
      return AllocationResult.Err(
        Deficit(
          taskId = stuck?.quota?.taskId ?: "",
          required = blueprint.totalQuestions,
          available = blueprint.totalQuestions - remaining,
        )
      )
    }

    val quotas =
      orderedTasks.mapNotNull { task ->
        val required = allocation.getValue(task.quota.taskId)
        if (required > 0) {
          TaskQuota(task.quota.taskId, task.quota.blockId, required)
        } else {
          null
        }
      }
    return AllocationResult.Ok(
      ExamBlueprint(totalQuestions = blueprint.totalQuestions, taskQuotas = quotas)
    )
  }

  fun weakness(stats: Map<String, ItemStats>): Double {
    if (stats.isEmpty()) return 1.0
    val attempts = stats.values.sumOf { it.attempts }
    if (attempts == 0) return 1.0
    val correct = stats.values.sumOf { it.correct }
    val accuracy = correct.toDouble() / attempts.toDouble()
    return (1.0 - accuracy).coerceIn(0.0, 1.0)
  }
}
