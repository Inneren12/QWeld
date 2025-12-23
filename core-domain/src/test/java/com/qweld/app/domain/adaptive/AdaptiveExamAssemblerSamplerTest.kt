package com.qweld.app.domain.adaptive

import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.AttemptSeed
import com.qweld.app.domain.exam.ExamAssemblyConfig
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ItemStats
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
    // Make this deterministic: ensure any MEDIUM pick simulates "always correct"
    // so the policy will reliably drift to HARD.
    val strongStats =
      questions
        .filter { it.difficulty == DifficultyBand.MEDIUM }
        .associate { q -> q.id to ItemStats(questionId = q.id, attempts = 10, correct = 10) }
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

  @Test
  fun `difficulty trend follows policy picks during initial run`() {
    val blueprint = ExamBlueprint(totalQuestions = 5, taskQuotas = listOf(TaskQuota("A-1", "A", 5)))
    val questions =
      generateQuestions(
        taskId = "A-1",
        count = 6,
        difficultyProvider = { index -> if (index < 4) DifficultyBand.MEDIUM else DifficultyBand.HARD },
      )
    val stats = questions.associate { question -> question.id to ItemStats(questionId = question.id, attempts = 1, correct = 1) }
    val policy = RecordingPolicy(DefaultAdaptiveExamPolicy())
    val assembler =
      AdaptiveExamAssembler(
        questionRepository = FakeQuestionRepository(questions),
        statsRepository = FakeUserStatsRepository(stats),
        policy = policy,
        assemblyConfig = ExamAssemblyConfig(),
        clock = fixedClock(),
      )

    runBlocking {
      (assembler.assemble("user", "EN", AttemptSeed(7L), blueprint) as Outcome.Ok).value.exam
    }

    assertEquals(
      listOf(
        DifficultyBand.MEDIUM,
        DifficultyBand.MEDIUM,
        DifficultyBand.HARD,
        DifficultyBand.MEDIUM,
        DifficultyBand.MEDIUM,
      ),
      policy.servedBands.take(5),
    )
  }

  @Test
  fun `falls back when desired difficulty is exhausted`() {
    val blueprint = ExamBlueprint(totalQuestions = 3, taskQuotas = listOf(TaskQuota("A-1", "A", 3)))
    val questions =
      listOf(
        buildQuestion("A-1", 0, difficulty = DifficultyBand.HARD),
        buildQuestion("A-1", 1, difficulty = DifficultyBand.MEDIUM),
        buildQuestion("A-1", 2, difficulty = DifficultyBand.MEDIUM),
      )
    val policy = GreedyHardPolicy()
    val assembler =
      AdaptiveExamAssembler(
        questionRepository = FakeQuestionRepository(questions),
        statsRepository = FakeUserStatsRepository(),
        policy = policy,
        assemblyConfig = ExamAssemblyConfig(),
        clock = fixedClock(),
      )

    val attempt =
      runBlocking {
        (assembler.assemble("user", "EN", AttemptSeed(3L), blueprint) as Outcome.Ok).value.exam
      }

    assertEquals(3, attempt.questions.size)
    assertEquals(1, attempt.questions.count { it.question.difficulty == DifficultyBand.HARD })
    assertEquals(listOf(DifficultyBand.HARD, DifficultyBand.MEDIUM, DifficultyBand.MEDIUM), policy.servedBands)
  }
}

private class RecordingPolicy(
  private val delegate: AdaptiveExamPolicy,
) : AdaptiveExamPolicy {
  val servedBands = mutableListOf<DifficultyBand>()

  override fun initialState(totalQuestions: Int): AdaptiveState = delegate.initialState(totalQuestions)

  override fun nextState(previous: AdaptiveState, servedBand: DifficultyBand, wasCorrect: Boolean): AdaptiveState {
    servedBands += servedBand
    return delegate.nextState(previous, servedBand, wasCorrect)
  }

  override fun pickNextDifficulty(state: AdaptiveState): DifficultyBand = delegate.pickNextDifficulty(state)
}

private class GreedyHardPolicy : AdaptiveExamPolicy {
  val servedBands = mutableListOf<DifficultyBand>()

  override fun initialState(totalQuestions: Int): AdaptiveState =
    AdaptiveState(
      currentDifficulty = DifficultyBand.HARD,
      correctStreak = 0,
      incorrectStreak = 0,
      askedPerBand = DifficultyBand.values().associateWith { 0 },
      remainingQuestions = totalQuestions,
    )

  override fun nextState(previous: AdaptiveState, servedBand: DifficultyBand, wasCorrect: Boolean): AdaptiveState {
    servedBands += servedBand
    val asked = previous.askedPerBand.toMutableMap()
    asked[servedBand] = asked.getOrElse(servedBand) { 0 } + 1
    return previous.copy(
      correctStreak = if (servedBand == DifficultyBand.HARD && wasCorrect) previous.correctStreak + 1 else 0,
      incorrectStreak = if (servedBand == DifficultyBand.HARD && !wasCorrect) previous.incorrectStreak + 1 else 0,
      askedPerBand = asked,
      remainingQuestions = (previous.remainingQuestions - 1).coerceAtLeast(0),
    )
  }

  override fun pickNextDifficulty(state: AdaptiveState): DifficultyBand = DifficultyBand.HARD
}
