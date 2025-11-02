package com.qweld.app.feature.exam.data

import android.content.Context
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.TaskQuota
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber

object BlueprintJsonLoader {
  const val DEFAULT_ID: String = "welder_ip_sk_202404"
  const val DEFAULT_PATH: String = "blueprints/welder_ip_sk_202404.json"

  private const val EXPECTED_TASK_COUNT = 15
  private val TASK_ID_REGEX = Regex("^[A-D]-\\d{1,2}$")

  private val json = Json { ignoreUnknownKeys = true }

  fun loadFromAssets(context: Context, path: String = DEFAULT_PATH): ExamBlueprint {
    val start = System.currentTimeMillis()
    val text = try {
      context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (notFound: FileNotFoundException) {
      Timber.e(notFound, "Failed to load blueprint asset: %s", path)
      throw IllegalStateException("Asset not found: $path", notFound)
    } catch (io: IOException) {
      Timber.e(io, "Failed to read blueprint asset: %s", path)
      throw io
    }

    val parsed = try {
      decode(text)
    } catch (error: Exception) {
      Timber.e(error, "Failed to parse blueprint asset: %s", path)
      throw error
    }

    val elapsed = System.currentTimeMillis() - start
    Timber.i(
      "[blueprint_load] path=%s id=%s total=%d tasks=%d elapsed=%dms",
      path,
      parsed.id,
      parsed.blueprint.totalQuestions,
      parsed.blueprint.taskQuotas.size,
      elapsed,
    )

    return parsed.blueprint
  }

  fun parse(text: String): ExamBlueprint {
    return try {
      decode(text).blueprint
    } catch (error: Exception) {
      Timber.e(error, "Failed to parse blueprint text")
      throw error
    }
  }

  fun mapTaskToBlock(taskId: String): String = taskId.substringBefore('-')

  private fun decode(text: String): ParsedBlueprint {
    val dto = json.decodeFromString<BlueprintDto>(text)
    return ParsedBlueprint(dto.id.ifBlank { DEFAULT_ID }, dto.toDomain())
  }

  @Serializable
  private data class BlueprintDto(
    val id: String = DEFAULT_ID,
    val trade: String? = null,
    val exam: String? = null,
    val blueprintVersion: String? = null,
    val policyVersion: String? = null,
    val questionCount: Int,
    val validFrom: String? = null,
    val sources: List<SourceDto>? = null,
    val blocks: List<BlockDto>,
  ) {
    fun toDomain(): ExamBlueprint {
      if (questionCount <= 0) {
        throw IllegalStateException("Question count must be positive, got=$questionCount")
      }

      val allTasks = blocks.flatMap { it.tasks }

      if (allTasks.size != EXPECTED_TASK_COUNT) {
        throw IllegalStateException("Tasks expected=15, got=${allTasks.size}")
      }

      val invalidTaskIds = allTasks.map { it.id }.filterNot { TASK_ID_REGEX.matches(it) }
      if (invalidTaskIds.isNotEmpty()) {
        throw IllegalStateException("Invalid taskId(s): ${invalidTaskIds.sorted()}")
      }

      val quotaSum = allTasks.sumOf { it.quota }
      if (quotaSum != questionCount) {
        throw IllegalStateException("Blueprint quotas sum=$quotaSum, expected=$questionCount")
      }

      val quotas = allTasks.map { task ->
        TaskQuota(
          taskId = task.id,
          blockId = mapTaskToBlock(task.id),
          required = task.quota,
        )
      }

      return ExamBlueprint(totalQuestions = questionCount, taskQuotas = quotas)
    }
  }

  @Serializable
  private data class BlockDto(
    val id: String,
    val title: String? = null,
    val tasks: List<TaskDto>,
  )

  @Serializable
  private data class TaskDto(
    val id: String,
    val title: String? = null,
    val quota: Int,
  )

  @Serializable
  private data class SourceDto(
    val role: String? = null,
    val title: String? = null,
    val publisher: String? = null,
    val published: String? = null,
  )

  private data class ParsedBlueprint(
    val id: String,
    val blueprint: ExamBlueprint,
  )
}
