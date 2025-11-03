package com.qweld.app.domain.exam

import com.qweld.app.domain.Outcome
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class LocaleDeficitTest {
  private val blueprint =
    ExamBlueprint(
      totalQuestions = 2,
      taskQuotas =
        listOf(
          TaskQuota("A-1", "A", 1),
          TaskQuota("B-1", "B", 1),
        ),
    )

  @Test
  fun `ip mock blocks when locale pool missing`() {
    val questions =
      generateQuestions("A-1", 2, locale = "RU") + generateQuestions("B-1", 2, locale = "EN")
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
          mode = ExamMode.IP_MOCK,
          locale = "RU",
          seed = AttemptSeed(5L),
          blueprint = blueprint,
        )
      }

    assertTrue(result is Outcome.Err.QuotaExceeded)
    result as Outcome.Err.QuotaExceeded
    assertEquals("B-1", result.taskId)
    assertEquals(1, result.required)
    assertEquals(0, result.have)
    assertEquals(1, result.required - result.have)
  }
}
