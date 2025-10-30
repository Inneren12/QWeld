package com.qweld.app.domain.exam

import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AntiClusterTest {
  private val blueprint =
    ExamBlueprint(
      totalQuestions = 24,
      taskQuotas =
        listOf(
          TaskQuota("A-1", "A", 6),
          TaskQuota("A-2", "A", 6),
          TaskQuota("B-1", "B", 6),
          TaskQuota("C-1", "C", 6),
        ),
    )

  @Test
  fun `anti cluster keeps task and block streaks within limits`() {
    val questions =
      generateQuestions("A-1", 8) +
        generateQuestions("A-2", 8) +
        generateQuestions("B-1", 8) +
        generateQuestions("C-1", 8)
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
        mode = ExamMode.IP_MOCK,
        locale = "EN",
        seed = AttemptSeed(99L),
        blueprint = blueprint,
      )

    val taskIds = attempt.questions.map { it.question.taskId }
    val blocks = attempt.questions.map { it.question.blockId }
    val maxTaskRun = maxRunLength(taskIds)
    val maxBlockRun = maxRunLength(blocks)
    assertTrue(maxTaskRun <= 3, "Expected task run <=3 but was $maxTaskRun")
    assertTrue(maxBlockRun <= 5, "Expected block run <=5 but was $maxBlockRun")
  }

  private fun maxRunLength(sequence: List<String>): Int {
    if (sequence.isEmpty()) return 0
    var maxRun = 1
    var current = sequence.first()
    var count = 1
    for (index in 1 until sequence.size) {
      val value = sequence[index]
      if (value == current) {
        count++
        if (count > maxRun) {
          maxRun = count
        }
      } else {
        current = value
        count = 1
      }
    }
    return maxRun
  }
}
