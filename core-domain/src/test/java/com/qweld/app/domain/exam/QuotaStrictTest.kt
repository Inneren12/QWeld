package com.qweld.app.domain.exam

import com.qweld.app.domain.exam.errors.ExamAssemblyException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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

    val error =
      assertFailsWith<ExamAssemblyException.Deficit> {
        assembler.assemble(
          userId = "user",
          mode = ExamMode.IP_MOCK,
          locale = "RU",
          seed = AttemptSeed(1L),
          blueprint = blueprint,
        )
      }

    val detail = error.details.single { it.taskId == "B-1" }
    assertEquals(2, detail.need)
    assertEquals(1, detail.have)
    assertEquals(1, detail.missing)
    assertEquals("RU", detail.locale)
    assertTrue(logs.any { it.startsWith("[deficit] taskId=B-1") })
  }
}
