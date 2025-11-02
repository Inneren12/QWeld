package com.qweld.app.feature.exam.data

import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.TaskQuota
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class BlueprintJsonLoader(
  private val json: Json = DEFAULT_JSON,
) {
  fun decode(payload: String): ExamBlueprint {
    val dto = json.decodeFromString(BlueprintDTO.serializer(), payload)
    val blocks = dto.blocks
    val allTasks = blocks.flatMap { it.tasks }

    require(allTasks.size == EXPECTED_TASK_COUNT) {
      "Blueprint must contain exactly $EXPECTED_TASK_COUNT tasks (found ${allTasks.size})"
    }

    require(blocks.none { it.id.isBlank() }) { "Block ids must be non-blank" }

    val invalidTaskIds = mutableListOf<String>()
    val duplicateTaskIds = mutableListOf<String>()
    val seenTaskIds = mutableSetOf<String>()
    for (block in blocks) {
      val prefix = "${block.id}-"
      for (task in block.tasks) {
        if (!task.id.startsWith(prefix)) {
          invalidTaskIds += task.id
        }
        if (!seenTaskIds.add(task.id)) {
          duplicateTaskIds += task.id
        }
      }
    }

    require(duplicateTaskIds.isEmpty()) {
      "Duplicate task ids detected: ${duplicateTaskIds.sorted()}"
    }

    require(invalidTaskIds.isEmpty()) {
      "Task ids must match their block prefix: ${invalidTaskIds.sorted()}"
    }

    val invalid = allTasks.filter { it.quota <= 0 }.map { it.id }
    require(invalid.isEmpty()) { "Each task quota must be positive: ${invalid.sorted()}" }

    val quotas = blocks.flatMap { block ->
      block.tasks.map { task ->
        TaskQuota(
          taskId = task.id,
          blockId = block.id,
          required = task.quota,
        )
      }
    }

    return ExamBlueprint(
      totalQuestions = dto.questionCount,
      taskQuotas = quotas,
    )
  }

  @Serializable
  private data class BlueprintDTO(
    val questionCount: Int,
    val blocks: List<BlockDTO> = emptyList(),
  )

  @Serializable
  private data class BlockDTO(
    val id: String,
    val tasks: List<TaskDTO> = emptyList(),
  )

  @Serializable
  private data class TaskDTO(
    val id: String,
    val quota: Int,
  )

  companion object {
    val DEFAULT_JSON: Json = Json { ignoreUnknownKeys = true }
    private const val EXPECTED_TASK_COUNT = 15
  }
}
