package com.qweld.app.domain.exam

import com.qweld.app.domain.Outcome
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class QuotaStrictTest {
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
  fun `ip mock blocks when pool below quota`() {
    val questions =
      generateQuestions("A-1", 2, locale = "RU") + generateQuestions("B-1", 1, locale = "RU")
    val repo = FakeQuestionRepository(questions)
    val statsRepo = FakeUserStatsRepository()
    val logs = mutableListOf<String>()
    val assembler =
      ExamAssembler(
        questionRepository = repo,
        statsRepository = statsRepo,
        clock = fixedClock(),
        logger = logs::add,
      )

    val result =
      runBlocking {
        assembler.assemble(
          userId = "user",
          mode = ExamMode.IP_MOCK,
          locale = "RU",
          seed = AttemptSeed(1L),
          blueprint = blueprint,
        )
      }

    require(result is Outcome.Err.QuotaExceeded)
    assertEquals("B-1", result.taskId)
    assertEquals(2, result.required)
    assertEquals(1, result.have)
    assertEquals(1, result.required - result.have)
    assertTrue(logs.any { it == "[deficit] taskId=B-1 required=2 have=1 seed=1" })
  }
}
