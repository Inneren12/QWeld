package com.qweld.app.domain.exam

import com.qweld.app.domain.Outcome
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class WeightsBiasTest {
  @Test
  fun wrongBiasedPrefersRecentMistakes() {
    val questions = listOf(buildQuestion(taskId = "A-1", index = 0), buildQuestion(taskId = "A-1", index = 1))
    val wrongQuestion = questions.first()
    val questionRepository = FakeQuestionRepository(questions)
    val statsRepository =
      FakeUserStatsRepository(
        mapOf(
          wrongQuestion.id to
            ItemStats(
              questionId = wrongQuestion.id,
              attempts = 2,
              correct = 1,
              lastAnsweredAt = java.time.Instant.EPOCH,
              lastAnswerCorrect = false,
            ),
          questions[1].id to
            ItemStats(
              questionId = questions[1].id,
              attempts = 2,
              correct = 1,
              lastAnsweredAt = java.time.Instant.EPOCH,
              lastAnswerCorrect = true,
            ),
        ),
      )
    val blueprint = ExamBlueprint(totalQuestions = 1, taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = 1)))
    val neutralAssembler =
      ExamAssembler(
        questionRepository = questionRepository,
        statsRepository = statsRepository,
        clock = fixedClock(),
        config = ExamAssemblyConfig(practiceWrongBiased = false),
      )
    val biasedAssembler =
      ExamAssembler(
        questionRepository = questionRepository,
        statsRepository = statsRepository,
        clock = fixedClock(),
        config = ExamAssemblyConfig(practiceWrongBiased = true),
      )

    var neutralWrongSelections = 0
    var biasedWrongSelections = 0
    repeat(200) { index ->
      val seed = AttemptSeed(index.toLong() + 1)
      val neutralResult =
        runBlocking {
          neutralAssembler.assemble(
            userId = "user",
            mode = ExamMode.PRACTICE,
            locale = "en",
            seed = seed,
            blueprint = blueprint,
          )
        }
      val biasedResult =
        runBlocking {
          biasedAssembler.assemble(
            userId = "user",
            mode = ExamMode.PRACTICE,
            locale = "en",
            seed = seed,
            blueprint = blueprint,
          )
        }
      val neutralAttempt = (neutralResult as Outcome.Ok).value.exam
      val biasedAttempt = (biasedResult as Outcome.Ok).value.exam
      if (neutralAttempt.questions.first().question.id == wrongQuestion.id) {
        neutralWrongSelections++
      }
      if (biasedAttempt.questions.first().question.id == wrongQuestion.id) {
        biasedWrongSelections++
      }
    }

    assertTrue(biasedWrongSelections > neutralWrongSelections)
  }
}
