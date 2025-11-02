package com.qweld.app.feature.exam.data

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

class BlueprintJsonLoaderTest {
  private val loader = BlueprintJsonLoader()

  @Test
  fun decodeValidBlueprint() {
    val blueprint = loader.decode(blueprintJson())

    assertEquals(125, blueprint.totalQuestions)
    assertEquals(15, blueprint.taskQuotas.size)
    val blocks = blueprint.taskQuotas.groupBy { it.blockId }
    assertEquals(setOf("A", "B", "C", "D"), blocks.keys)
    val taskIds = blueprint.taskQuotas.map { it.taskId }.toSet()
    assertTrue(taskIds.containsAll(DEFAULT_TASK_IDS))
  }

  @Test
  fun rejectsBlueprintWithIncorrectTaskCount() {
    val error = assertFailsWith<IllegalArgumentException> {
      loader.decode(
        blueprintJson {
          val lastBlock = it.last()
          lastBlock.tasks.removeLast()
        }
      )
    }

    assertEquals("Blueprint must contain exactly 15 tasks (found 14)", error.message)
  }

  @Test
  fun rejectsTaskWithWrongBlockPrefix() {
    val error = assertFailsWith<IllegalArgumentException> {
      loader.decode(
        blueprintJson {
          val firstBlock = it.first()
          firstBlock.tasks[1] = firstBlock.tasks[1].copy(id = "B-99")
        }
      )
    }

    assertEquals("Task ids must match their block prefix: [B-99]", error.message)
  }

  @Test
  fun quotaZeroThrows() {
    val error = assertFailsWith<IllegalArgumentException> {
      loader.decode(
        blueprintJson {
          val firstBlock = it.first()
          firstBlock.tasks[1] = firstBlock.tasks[1].copy(quota = 0)
        }
      )
    }

    assertEquals("Each task quota must be positive: [A-2]", error.message)
  }

  @Test
  fun rejectsWhenSumDoesNotMatchQuestionCount() {
    val error = assertFailsWith<IllegalArgumentException> {
      loader.decode(blueprintJson(questionCount = 126))
    }

    assertEquals(
      "Blueprint total (126) does not equal quotas sum (125)",
      error.message,
    )
  }

  private fun blueprintJson(
    questionCount: Int? = null,
    mutate: (MutableList<BlockSpec>) -> Unit = {},
  ): String {
    val blocks = DEFAULT_BLOCKS.map { block -> block.copy(tasks = block.tasks.map { it.copy() }.toMutableList()) }
      .toMutableList()
    mutate(blocks)
    val total = questionCount ?: blocks.sumOf { block -> block.tasks.sumOf { it.quota } }
    return buildString {
      append('{')
      append("\"questionCount\": ")
      append(total)
      append(',')
      append("\"blocks\": [")
      blocks.forEachIndexed { blockIndex, block ->
        if (blockIndex > 0) append(',')
        append('{')
        append("\"id\": \"")
        append(block.id)
        append("\", \"tasks\": [")
        block.tasks.forEachIndexed { taskIndex, task ->
          if (taskIndex > 0) append(',')
          append('{')
          append("\"id\": \"")
          append(task.id)
          append("\", \"quota\": ")
          append(task.quota)
          append('}')
        }
        append(']')
        append('}')
      }
      append(']')
      append('}')
    }
  }

  private data class BlockSpec(
    val id: String,
    val tasks: MutableList<TaskSpec>,
  )

  private data class TaskSpec(
    val id: String,
    val quota: Int,
  )

  companion object {
    private val DEFAULT_BLOCKS =
      mutableListOf(
        BlockSpec(
          id = "A",
          tasks =
            mutableListOf(
              TaskSpec("A-1", 8),
              TaskSpec("A-2", 6),
              TaskSpec("A-3", 6),
              TaskSpec("A-4", 5),
            ),
        ),
        BlockSpec(
          id = "B",
          tasks =
            mutableListOf(
              TaskSpec("B-5", 8),
              TaskSpec("B-6", 9),
              TaskSpec("B-7", 8),
              TaskSpec("B-8", 8),
            ),
        ),
        BlockSpec(
          id = "C",
          tasks =
            mutableListOf(
              TaskSpec("C-9", 9),
              TaskSpec("C-10", 9),
              TaskSpec("C-11", 9),
              TaskSpec("C-12", 9),
            ),
        ),
        BlockSpec(
          id = "D",
          tasks =
            mutableListOf(
              TaskSpec("D-13", 11),
              TaskSpec("D-14", 10),
              TaskSpec("D-15", 10),
            ),
        ),
      )

    private val DEFAULT_TASK_IDS =
      DEFAULT_BLOCKS.flatMap { block -> block.tasks.map { it.id } }.toSet()
  }
}
