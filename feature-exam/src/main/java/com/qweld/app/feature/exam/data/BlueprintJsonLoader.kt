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
    val allTasks = dto.blocks.flatMap { it.tasks }
    val invalid = allTasks.filter { it.quota <= 0 }.map { it.id }
    require(invalid.isEmpty()) { "Each task quota must be positive: ${invalid.sorted()}" }

    val quotas = dto.blocks.flatMap { block ->
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
  }
}
