package com.qweld.app.domain.exam

import com.qweld.app.domain.Outcome
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class AdaptiveExamAssemblerTest {

  @Test
  fun `adaptive mode favors weaker tasks`() {
    val blueprint =
      ExamBlueprint(
        totalQuestions = 5,
        taskQuotas = listOf(TaskQuota("A-1", "A", 3), TaskQuota("B-1", "B", 2)),
      )
    val questions = generateQuestions("A-1", 5) + generateQuestions("B-1", 5)
    val stats =
      mapOf(
        questions.first { it.taskId == "A-1" }.id to ItemStats(questionId = "", attempts = 10, correct = 9),
        questions.first { it.taskId == "B-1" }.id to ItemStats(questionId = "", attempts = 10, correct = 2),
      )
    val assembler =
      ExamAssembler(
        questionRepository = FakeQuestionRepository(questions),
        statsRepository = FakeUserStatsRepository(stats),
        clock = fixedClock(),
      )

    val result =
      runBlocking {
        assembler.assemble(
          userId = "user",
          mode = ExamMode.ADAPTIVE,
          locale = "EN",
          seed = AttemptSeed(42L),
          blueprint = blueprint,
        )
      }

    val attempt = (result as Outcome.Ok).value.exam
    val counts = attempt.questions.groupingBy { it.question.taskId }.eachCount()
    assertEquals(5, attempt.questions.size)
    assertEquals(5, attempt.blueprint.totalQuestions)
    assertTrue(counts.getValue("B-1") > counts.getValue("A-1"))
    val blueprintCounts = attempt.blueprint.taskQuotas.associate { it.taskId to it.required }
    assertTrue(blueprintCounts.getValue("B-1") > blueprintCounts.getValue("A-1"))
  }

  @Test
  fun `adaptive selection is deterministic for the same seed`() {
    val blueprint = ExamBlueprint(totalQuestions = 3, taskQuotas = listOf(TaskQuota("A-1", "A", 3)))
    val questions = generateQuestions("A-1", 5)
    val assembler =
      ExamAssembler(
        questionRepository = FakeQuestionRepository(questions),
        statsRepository = FakeUserStatsRepository(),
        clock = fixedClock(),
      )

    val first =
      runBlocking {
        (assembler.assemble("user", ExamMode.ADAPTIVE, "EN", AttemptSeed(99L), blueprint) as Outcome.Ok).value.exam
      }
    val second =
      runBlocking {
        (assembler.assemble("user", ExamMode.ADAPTIVE, "EN", AttemptSeed(99L), blueprint) as Outcome.Ok).value.exam
      }

    assertEquals(first.questions.map { it.question.id }, second.questions.map { it.question.id })

    val differentSeed =
      runBlocking {
        (assembler.assemble("user", ExamMode.ADAPTIVE, "EN", AttemptSeed(100L), blueprint) as Outcome.Ok).value.exam
      }
    assertNotEquals(first.questions.map { it.question.id }, differentSeed.questions.map { it.question.id })
  }
}
