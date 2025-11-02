package com.qweld.app.domain.exam

import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class FamilyUniquenessTest {
  private val blueprint =
    ExamBlueprint(
      totalQuestions = 2,
      taskQuotas = listOf(TaskQuota("A-1", "A", 2)),
    )

  @Test
  fun `assembler keeps only one question per family`() {
    val questions =
      listOf(
        buildQuestion("A-1", 0, familyId = "F1"),
        buildQuestion("A-1", 1, familyId = "F1"),
        buildQuestion("A-1", 2, familyId = "F2"),
      )
    val repo = FakeQuestionRepository(questions)
    val assembler =
      ExamAssembler(
        questionRepository = repo,
        statsRepository = FakeUserStatsRepository(),
        clock = fixedClock(),
      )

    val result =
      runBlocking {
        assembler.assemble(
          userId = "user",
          mode = ExamMode.PRACTICE,
          locale = "EN",
          seed = AttemptSeed(7L),
          blueprint = blueprint,
        )
      }

    require(result is ExamAssembler.AssemblyResult.Ok)
    val attempt = result.exam

    val families = attempt.questions.mapNotNull { it.question.familyId }
    assertEquals(2, families.size)
    assertEquals(families.toSet().size, families.size)
  }

  @Test
  fun `deficit triggered when unique families insufficient`() {
    val questions =
      listOf(
        buildQuestion("A-1", 0, familyId = "F1"),
        buildQuestion("A-1", 1, familyId = "F1"),
      )
    val repo = FakeQuestionRepository(questions)
    val assembler =
      ExamAssembler(
        questionRepository = repo,
        statsRepository = FakeUserStatsRepository(),
        clock = fixedClock(),
      )

    val result =
      runBlocking {
        assembler.assemble(
          userId = "user",
          mode = ExamMode.PRACTICE,
          locale = "EN",
          seed = AttemptSeed(8L),
          blueprint = blueprint,
        )
      }
    require(result is ExamAssembler.AssemblyResult.Deficit)
    assertEquals("A-1", result.taskId)
    assertEquals(2, blueprint.quotaFor("A-1").required)
    assertEquals(1, result.have)
    assertEquals(1, result.missing)
  }
}
