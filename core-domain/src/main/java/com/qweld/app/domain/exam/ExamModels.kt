package com.qweld.app.domain.exam

import com.qweld.app.domain.adaptive.DifficultyBand
import com.qweld.app.domain.exam.util.WeightsConfig
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
    val sum = taskQuotas.sumOf { it.required }
    require(totalQuestions == sum) {
      "Blueprint total ($totalQuestions) does not equal quotas sum ($sum)"
    }
  }

  fun quotaFor(taskId: String): TaskQuota =
    taskQuotas.firstOrNull { it.taskId == taskId } ?: error("Unknown taskId $taskId in blueprint")

  companion object {
    fun default(): ExamBlueprint {
      val quotas =
        listOf(
          TaskQuota(taskId = "A-1", blockId = "A", required = 4),
          TaskQuota(taskId = "A-2", blockId = "A", required = 4),
          TaskQuota(taskId = "A-3", blockId = "A", required = 5),
          TaskQuota(taskId = "A-4", blockId = "A", required = 4),
          TaskQuota(taskId = "A-5", blockId = "A", required = 7),
          TaskQuota(taskId = "B-6", blockId = "B", required = 10),
          TaskQuota(taskId = "B-7", blockId = "B", required = 15),
          TaskQuota(taskId = "C-8", blockId = "C", required = 5),
          TaskQuota(taskId = "C-9", blockId = "C", required = 7),
          TaskQuota(taskId = "C-10", blockId = "C", required = 5),
          TaskQuota(taskId = "C-11", blockId = "C", required = 4),
          TaskQuota(taskId = "D-12", blockId = "D", required = 18),
          TaskQuota(taskId = "D-13", blockId = "D", required = 21),
          TaskQuota(taskId = "D-14", blockId = "D", required = 12),
          TaskQuota(taskId = "D-15", blockId = "D", required = 4),
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
  val stem: Map<String, String>,
  val familyId: String? = null,
  val difficulty: DifficultyBand? = null,
  val choices: List<Choice>,
  val correctChoiceId: String,
)

data class ItemStats(
  val questionId: String,
  val attempts: Int,
  val correct: Int,
  val lastAnsweredAt: Instant? = null,
  val lastAnswerCorrect: Boolean? = null,
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
  val weights: WeightsConfig = WeightsConfig(),
  val practiceWrongBiased: Boolean = false,
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
