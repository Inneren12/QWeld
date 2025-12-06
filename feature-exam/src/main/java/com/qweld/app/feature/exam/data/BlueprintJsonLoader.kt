package com.qweld.app.feature.exam.data

import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.core.common.logging.LogTag
import com.qweld.core.common.logging.Logx
import java.io.InputStream
import kotlinx.serialization.SerialName
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

    require(blocks.none { it.resolvedId.isBlank() }) { "Block ids must be non-blank" }

    val invalidTaskIds = mutableListOf<String>()
    val duplicateTaskIds = mutableListOf<String>()
    val seenTaskIds = mutableSetOf<String>()
    for (block in blocks) {
      val prefix = "${block.resolvedId}-"
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

    val quotas =
      blocks.flatMap { block ->
        val blockId = block.resolvedId
        block.tasks.map { task ->
          TaskQuota(
            taskId = task.id,
            blockId = blockId,
            required = task.quota,
          )
        }
      }

    return ExamBlueprint(
      totalQuestions = dto.resolvedTotal,
      taskQuotas = quotas,
    )
  }

  fun loadFromAssets(assetOpener: (String) -> InputStream?, path: String): ExamBlueprint {
    val payload =
      assetOpener(path)?.use { stream ->
        stream.bufferedReader().use { it.readText() }
      } ?: throw IllegalArgumentException("Blueprint asset not found: $path")
    val blueprint = decode(payload)
    Logx.i(
      LogTag.BLUEPRINT_LOAD,
      "asset",
      "path" to path,
      "total" to blueprint.totalQuestions,
      "tasks" to blueprint.taskQuotas.size,
    )
    return blueprint
  }

  @Serializable
  private data class BlueprintDTO(
    @SerialName("questionCount") val questionCount: Int? = null,
    @SerialName("totalQuestions") val totalQuestions: Int? = null,
    val blocks: List<BlockDTO> = emptyList(),
  ) {
    val resolvedTotal: Int
      get() = questionCount ?: totalQuestions ?: error("Blueprint missing question count")
  }

  @Serializable
  private data class BlockDTO(
    @SerialName("id") val id: String = "",
    @SerialName("code") val code: String? = null,
    val tasks: List<TaskDTO> = emptyList(),
  ) {
    val resolvedId: String
      get() = id.ifBlank { code ?: "" }
  }

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
