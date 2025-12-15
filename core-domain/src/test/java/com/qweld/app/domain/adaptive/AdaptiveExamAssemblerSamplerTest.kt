package com.qweld.app.domain.adaptive

import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.AttemptSeed
import com.qweld.app.domain.exam.ExamAssemblyConfig
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.buildQuestion
import com.qweld.app.domain.exam.generateQuestions
import com.qweld.app.domain.exam.fixedClock
import com.qweld.app.domain.exam.FakeQuestionRepository
import com.qweld.app.domain.exam.FakeUserStatsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AdaptiveExamAssemblerSamplerTest {

  @Test
  fun `respects quotas while falling back between difficulty bands`() {
    val blueprint =
      ExamBlueprint(
        totalQuestions = 3,
        taskQuotas = listOf(TaskQuota("A-1", "A", 2), TaskQuota("B-1", "B", 1)),
      )
    val questions =
      listOf(
        buildQuestion("A-1", 0, difficulty = DifficultyBand.EASY),
        buildQuestion("A-1", 1, difficulty = DifficultyBand.MEDIUM),
        buildQuestion("B-1", 0, difficulty = DifficultyBand.MEDIUM),
      )
    val assembler =
      AdaptiveExamAssembler(
        questionRepository = FakeQuestionRepository(questions),
        statsRepository = FakeUserStatsRepository(),
        policy = DefaultAdaptiveExamPolicy(),
        assemblyConfig = ExamAssemblyConfig(),
        clock = fixedClock(),
      )

    val result =
      runBlocking {
        assembler.assemble(
          userId = "user",
          locale = "EN",
          seed = AttemptSeed(9L),
          blueprint = blueprint,
        )
      }

    val attempt = (result as Outcome.Ok).value.exam
    assertEquals(3, attempt.questions.size)
    val byTask = attempt.questions.groupingBy { it.question.taskId }.eachCount()
    assertEquals(2, byTask.getValue("A-1"))
    assertEquals(1, byTask.getValue("B-1"))
    assertTrue(attempt.questions.any { it.question.difficulty == DifficultyBand.EASY })
  }

  @Test
  fun `drifts toward harder items when stats show strong accuracy`() {
    val blueprint = ExamBlueprint(totalQuestions = 4, taskQuotas = listOf(TaskQuota("A-1", "A", 4)))
    val questions =
      generateQuestions(
        taskId = "A-1",
        count = 6,
        difficultyProvider = { index -> if (index >= 4) DifficultyBand.HARD else DifficultyBand.MEDIUM },
      )
    val strongStats =
      mapOf(
        questions[0].id to com.qweld.app.domain.exam.ItemStats(questionId = questions[0].id, attempts = 10, correct = 9),
        questions[1].id to com.qweld.app.domain.exam.ItemStats(questionId = questions[1].id, attempts = 10, correct = 9),
        questions[2].id to com.qweld.app.domain.exam.ItemStats(questionId = questions[2].id, attempts = 10, correct = 9),
      )
    val assembler =
      AdaptiveExamAssembler(
        questionRepository = FakeQuestionRepository(questions),
        statsRepository = FakeUserStatsRepository(strongStats),
        policy = DefaultAdaptiveExamPolicy(),
        assemblyConfig = ExamAssemblyConfig(),
        clock = fixedClock(),
      )

    val attempt =
      runBlocking {
        (assembler.assemble("user", "EN", AttemptSeed(12L), blueprint) as Outcome.Ok).value.exam
      }
    assertEquals(4, attempt.questions.size)
    assertTrue(attempt.questions.count { it.question.difficulty == DifficultyBand.HARD } >= 1)
  }
}
