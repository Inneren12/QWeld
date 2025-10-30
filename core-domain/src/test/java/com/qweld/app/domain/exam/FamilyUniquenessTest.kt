package com.qweld.app.domain.exam

import com.qweld.app.domain.exam.errors.ExamAssemblyException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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

    val attempt =
      assembler.assemble(
        userId = "user",
        mode = ExamMode.PRACTICE,
        locale = "EN",
        seed = AttemptSeed(7L),
        blueprint = blueprint,
      )

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

    val error =
      assertFailsWith<ExamAssemblyException.Deficit> {
        assembler.assemble(
          userId = "user",
          mode = ExamMode.PRACTICE,
          locale = "EN",
          seed = AttemptSeed(8L),
          blueprint = blueprint,
        )
      }
    val detail = error.details.single()
    assertEquals("A-1", detail.taskId)
    assertTrue(detail.familyDuplicates)
    assertEquals(1, detail.have)
    assertEquals(1, detail.missing)
  }
}
