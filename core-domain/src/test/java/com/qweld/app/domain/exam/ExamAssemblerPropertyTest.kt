package com.qweld.app.domain.exam

import com.qweld.app.domain.Outcome
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ExamAssemblerPropertyTest {
  private val blueprint =
    ExamBlueprint(
      totalQuestions = 12,
      taskQuotas =
        listOf(
          TaskQuota(taskId = "A-1", blockId = "A", required = 4),
          TaskQuota(taskId = "B-1", blockId = "B", required = 4),
          TaskQuota(taskId = "C-1", blockId = "C", required = 4),
        ),
    )

  private val questions =
    listOf("A-1", "B-1", "C-1")
      .flatMap { taskId ->
        generateQuestions(
          taskId = taskId,
          count = 16,
          familyIdProvider = { index -> "${taskId}-F$index" },
        )
      }

  private val assembler =
    ExamAssembler(
      questionRepository = FakeQuestionRepository(questions),
      statsRepository = FakeUserStatsRepository(),
      clock = fixedClock(),
    )

  private val seeds: LongRange = 0L..199L

  @Test
  fun `assembling with same seed is stable`() {
    repeatWithSeeds(seeds) { seed ->
      val first = assemble(seed)
      val second = assemble(seed)
      val firstIds = first.questions.map { it.question.id }
      val secondIds = second.questions.map { it.question.id }
      assertEquals(
        firstIds,
        secondIds,
        "Seed $seed produced mismatched question ordering",
      )
    }
  }

  @Test
  fun `task quotas are satisfied for all seeds`() {
    repeatWithSeeds(seeds) { seed ->
      val attempt = assemble(seed)
      val total = attempt.questions.size
      assertEquals(
        blueprint.totalQuestions,
        total,
        "Seed $seed total questions mismatch: expected ${blueprint.totalQuestions} got $total",
      )
      blueprint.taskQuotas.forEach { quota ->
        val selected = attempt.questions.count { it.question.taskId == quota.taskId }
        assertEquals(
          quota.required,
          selected,
          "Seed $seed taskId=${quota.taskId} quota mismatch: expected ${quota.required} got $selected",
        )
      }
    }
  }

  @Test
  fun `assembled exams contain unique family ids`() {
    repeatWithSeeds(seeds) { seed ->
      val attempt = assemble(seed)
      val families = attempt.questions.mapNotNull { it.question.familyId }
      val duplicates = families.groupingBy { it }.eachCount().filterValues { it > 1 }
      if (duplicates.isNotEmpty()) {
        val detail = duplicates.entries.joinToString { (family, count) -> "$family -> $count" }
        fail("Seed $seed has duplicate families: $detail")
      }
      assertTrue(
        families.size == families.toSet().size,
        "Seed $seed has duplicate families despite filter",
      )
    }
  }

  private fun assemble(seed: Long): ExamAttempt {
    val result =
      runBlocking {
        assembler.assemble(
          userId = "user",
          mode = ExamMode.PRACTICE,
          locale = "EN",
          seed = AttemptSeed(seed),
          blueprint = blueprint,
        )
      }
    return when (result) {
      is Outcome.Ok -> result.value.exam
      is Outcome.Err.QuotaExceeded ->
        fail(
          "Seed $seed triggered deficit for task ${result.taskId}: " +
            "required=${result.required} have=${result.have}",
        )
      is Outcome.Err -> fail("Seed $seed produced unexpected outcome ${result::class.simpleName}")
    }
  }

  private fun repeatWithSeeds(range: LongRange, block: (Long) -> Unit) {
    range.forEach(block)
  }
}
