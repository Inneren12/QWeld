package com.qweld.app.domain.exam

import com.qweld.app.domain.Outcome
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class PracticeWeightedPickTest {
  private val blueprint =
    ExamBlueprint(
      totalQuestions = 1,
      taskQuotas = listOf(TaskQuota("A-1", "A", 1)),
    )

  @Test
  fun `practice prefers novel items while ip mock is uniform`() {
    val questions =
      listOf(
        buildQuestion("A-1", 0, familyId = "F1"),
        buildQuestion("A-1", 1, familyId = "F2"),
      )
    val novelId = questions[0].id
    val knownId = questions[1].id
    val statsRepoPractice =
      FakeUserStatsRepository(
        mapOf(
          knownId to ItemStats(knownId, attempts = 20, correct = 18),
        )
      )
    val repo = FakeQuestionRepository(questions)

    val practiceAssembler =
      ExamAssembler(
        questionRepository = repo,
        statsRepository = statsRepoPractice,
        clock = fixedClock(),
      )
    val practiceCounts = mutableMapOf(novelId to 0, knownId to 0)
    repeat(40) { idx ->
      val result =
        runBlocking {
          practiceAssembler.assemble(
            userId = "user",
            mode = ExamMode.PRACTICE,
            locale = "EN",
            seed = AttemptSeed(idx.toLong()),
            blueprint = blueprint,
          )
        }
      val attempt = (result as Outcome.Ok).value.exam
      val picked = attempt.questions.single().question.id
      practiceCounts[picked] = practiceCounts.getValue(picked) + 1
    }
    assertTrue(practiceCounts.getValue(novelId) > practiceCounts.getValue(knownId))

    val ipAssembler =
      ExamAssembler(
        questionRepository = repo,
        statsRepository = FakeUserStatsRepository(),
        clock = fixedClock(),
      )
    val ipCounts = mutableMapOf(novelId to 0, knownId to 0)
    repeat(40) { idx ->
      val result =
        runBlocking {
          ipAssembler.assemble(
            userId = "user",
            mode = ExamMode.IP_MOCK,
            locale = "EN",
            seed = AttemptSeed(idx.toLong()),
            blueprint = blueprint,
          )
        }
      val attempt = (result as Outcome.Ok).value.exam
      val picked = attempt.questions.single().question.id
      ipCounts[picked] = ipCounts.getValue(picked) + 1
    }
    assertTrue(ipCounts.values.all { it > 0 })
  }
}
