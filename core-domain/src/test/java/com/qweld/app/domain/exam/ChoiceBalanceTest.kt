package com.qweld.app.domain.exam

import kotlin.math.abs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ChoiceBalanceTest {
  private val blueprint = ExamBlueprint.default()
  private val repo =
    FakeQuestionRepository(
      blueprint.taskQuotas.flatMap { quota -> generateQuestions(quota.taskId, quota.required * 2) }
    )
  private val assembler =
    ExamAssembler(
      questionRepository = repo,
      statsRepository = FakeUserStatsRepository(),
      clock = fixedClock(),
    )

  @Test
  fun `single attempt stays within 10 percent tolerance`() {
    val attempt =
      runBlocking {
        assembler.assemble(
          userId = "user",
          mode = ExamMode.PRACTICE,
          locale = "EN",
          seed = AttemptSeed(1L),
          blueprint = blueprint,
        )
      }
    val histogram = attempt.correctPositionHistogram()
    val total = attempt.questions.size
    histogram.values.forEach { count ->
      val ratio = count.toDouble() / total
      assertTrue(ratio in 0.15..0.35, "Ratio $ratio out of tolerance for count $count")
    }
  }

  @Test
  fun `average distribution across many attempts gravitates to quarter`() {
    val attempts = 100
    val aggregate = mutableMapOf("A" to 0, "B" to 0, "C" to 0, "D" to 0)
    repeat(attempts) { idx ->
      val attempt =
        runBlocking {
          assembler.assemble(
            userId = "user",
            mode = ExamMode.PRACTICE,
            locale = "EN",
            seed = AttemptSeed(idx.toLong()),
            blueprint = blueprint,
          )
        }
      attempt.correctPositionHistogram().forEach { (key, value) ->
        aggregate[key] = aggregate.getValue(key) + value
      }
    }
    val total = blueprint.totalQuestions * attempts
    aggregate.forEach { (_, value) ->
      val ratio = value.toDouble() / total
      assertTrue(abs(ratio - 0.25) <= 0.05, "Ratio $ratio too far from 0.25")
    }
  }
}
