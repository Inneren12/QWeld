package com.qweld.app.domain.exam

import com.qweld.app.domain.Outcome
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class DeterminismTest {
  private val blueprint =
    ExamBlueprint(
      totalQuestions = 4,
      taskQuotas =
        listOf(
          TaskQuota("A-1", "A", 2),
          TaskQuota("B-1", "B", 2),
        ),
    )

  @Test
  fun `same seed yields identical order and choices`() {
    val questions = generateQuestions("A-1", 4) + generateQuestions("B-1", 4)
    val repo = FakeQuestionRepository(questions)
    val statsRepo = FakeUserStatsRepository()
    val logs1 = mutableListOf<String>()
    val assembler1 =
      ExamAssembler(
        questionRepository = repo,
        statsRepository = statsRepo,
        clock = fixedClock(),
        logger = logs1::add,
      )
    val seed = AttemptSeed(42L)
    val result1 =
      runBlocking {
        assembler1.assemble(
          userId = "user",
          mode = ExamMode.PRACTICE,
          locale = "EN",
          seed = seed,
          blueprint = blueprint,
        )
      }
    val attempt1 = (result1 as Outcome.Ok).value.exam

    val logs2 = mutableListOf<String>()
    val assembler2 =
      ExamAssembler(
        questionRepository = repo,
        statsRepository = statsRepo,
        clock = fixedClock(),
        logger = logs2::add,
      )
    val result2 =
      runBlocking {
        assembler2.assemble(
          userId = "user",
          mode = ExamMode.PRACTICE,
          locale = "EN",
          seed = seed,
          blueprint = blueprint,
        )
      }
    val attempt2 = (result2 as Outcome.Ok).value.exam

    assertEquals(
      attempt1.questions.map { it.question.id },
      attempt2.questions.map { it.question.id },
    )
    assertEquals(
      attempt1.questions.map { it.correctIndex },
      attempt2.questions.map { it.correctIndex },
    )
    assertEquals(logs1, logs2)
    assertTrue(logs1.isNotEmpty())
  }
}
