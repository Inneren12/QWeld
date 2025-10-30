package com.qweld.app.domain.exam

import java.time.Duration
import java.time.Instant

enum class ExamMode {
  IP_MOCK,
  PRACTICE,
  ADAPTIVE,
}

data class TaskQuota(
  val taskId: String,
  val blockId: String,
  val required: Int,
)

data class ExamBlueprint(
  val totalQuestions: Int,
  val taskQuotas: List<TaskQuota>,
) {
  val taskCount: Int = taskQuotas.size

  init {
    require(totalQuestions == taskQuotas.sumOf { it.required }) {
      "Blueprint total ($totalQuestions) does not equal quotas sum (${taskQuotas.sumOf { it.required }})"
    }
  }

  fun quotaFor(taskId: String): TaskQuota =
    taskQuotas.firstOrNull { it.taskId == taskId } ?: error("Unknown taskId $taskId in blueprint")

  companion object {
    fun default(): ExamBlueprint {
      val quotas =
        listOf(
          TaskQuota(taskId = "A-1", blockId = "A", required = 9),
          TaskQuota(taskId = "A-2", blockId = "A", required = 8),
          TaskQuota(taskId = "A-3", blockId = "A", required = 8),
          TaskQuota(taskId = "A-4", blockId = "A", required = 8),
          TaskQuota(taskId = "B-1", blockId = "B", required = 9),
          TaskQuota(taskId = "B-2", blockId = "B", required = 8),
          TaskQuota(taskId = "B-3", blockId = "B", required = 8),
          TaskQuota(taskId = "B-4", blockId = "B", required = 8),
          TaskQuota(taskId = "C-1", blockId = "C", required = 9),
          TaskQuota(taskId = "C-2", blockId = "C", required = 8),
          TaskQuota(taskId = "C-3", blockId = "C", required = 8),
          TaskQuota(taskId = "C-4", blockId = "C", required = 8),
          TaskQuota(taskId = "D-1", blockId = "D", required = 9),
          TaskQuota(taskId = "D-2", blockId = "D", required = 8),
          TaskQuota(taskId = "D-3", blockId = "D", required = 9),
        )
      return ExamBlueprint(totalQuestions = 125, taskQuotas = quotas)
    }
  }
}

data class Choice(
  val id: String,
  val text: Map<String, String>,
)

data class Question(
  val id: String,
  val taskId: String,
  val blockId: String,
  val locale: String,
  val familyId: String? = null,
  val choices: List<Choice>,
  val correctChoiceId: String,
)

data class ItemStats(
  val questionId: String,
  val attempts: Int,
  val correct: Int,
  val lastAnsweredAt: Instant? = null,
) {
  fun isFresh(now: Instant, freshDays: Long): Boolean {
    if (attempts == 0) return true
    val last = lastAnsweredAt ?: return false
    val elapsed = Duration.between(last, now)
    return elapsed.toDays() >= freshDays
  }
}

@JvmInline value class AttemptSeed(val value: Long)

data class ExamAssemblyConfig(
  val halfLifeCorrect: Double = 2.0,
  val noveltyBoost: Double = 2.0,
  val minWeight: Double = 0.05,
  val maxWeight: Double = 4.0,
  val freshDays: Long = 14,
  val antiClusterSwaps: Int = 10,
  val allowFallbackToEN: Boolean = false,
)

data class AssembledQuestion(
  val question: Question,
  val choices: List<Choice>,
  val correctIndex: Int,
)

data class ExamAttempt(
  val mode: ExamMode,
  val locale: String,
  val seed: AttemptSeed,
  val questions: List<AssembledQuestion>,
  val blueprint: ExamBlueprint,
) {
  fun correctPositionHistogram(): Map<String, Int> {
    val labels = listOf("A", "B", "C", "D")
    val counts = mutableMapOf<String, Int>()
    labels.forEach { counts[it] = 0 }
    questions.forEach { q ->
      val label = labels[q.correctIndex]
      counts[label] = counts.getValue(label) + 1
    }
    return counts
  }
}
