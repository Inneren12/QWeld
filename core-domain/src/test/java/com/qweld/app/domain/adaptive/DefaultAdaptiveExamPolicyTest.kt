package com.qweld.app.domain.adaptive

import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultAdaptiveExamPolicyTest {

  private val policy = DefaultAdaptiveExamPolicy()

  @Test
  fun `series of correct answers raises difficulty and holds near the end`() {
    var state = policy.initialState(totalQuestions = 5)

    val served = mutableListOf<DifficultyBand>()
    repeat(5) {
      val pick = policy.pickNextDifficulty(state)
      served += pick
      state = policy.nextState(state, servedBand = pick, wasCorrect = true)
    }

    assertEquals(
      listOf(
        DifficultyBand.MEDIUM,
        DifficultyBand.MEDIUM,
        DifficultyBand.HARD,
        DifficultyBand.MEDIUM,
        DifficultyBand.MEDIUM,
      ),
      served,
    )
    assertEquals(0, state.remainingQuestions)
    assertEquals(2, state.correctStreak)
    assertEquals(DifficultyBand.HARD, state.currentDifficulty)
  }

  @Test
  fun `moves up after two consecutive correct answers`() {
    var state = policy.initialState(totalQuestions = 3)
    val firstBand = policy.pickNextDifficulty(state)
    state = policy.nextState(state, servedBand = firstBand, wasCorrect = true)
    val secondBand = policy.pickNextDifficulty(state)
    state = policy.nextState(state, servedBand = secondBand, wasCorrect = true)
    assertEquals(DifficultyBand.HARD, state.currentDifficulty)
    assertEquals(DifficultyBand.MEDIUM, policy.pickNextDifficulty(state))
    assertEquals(2, state.askedPerBand.getValue(DifficultyBand.MEDIUM))
    assertEquals(1, state.remainingQuestions)
  }

  @Test
  fun `drops after a single incorrect answer`() {
    var state = policy.initialState(totalQuestions = 4)
    state = policy.nextState(state, servedBand = policy.pickNextDifficulty(state), wasCorrect = false)
    assertEquals(DifficultyBand.EASY, state.currentDifficulty)
    assertEquals(1, state.askedPerBand.getValue(DifficultyBand.MEDIUM))
    assertEquals(3, state.remainingQuestions)
  }

  @Test
  fun `prefers medium near the end of the exam`() {
    var state = policy.initialState(totalQuestions = 2)
    state = policy.nextState(state, servedBand = policy.pickNextDifficulty(state), wasCorrect = true)
    val pick = policy.pickNextDifficulty(state)
    assertEquals(DifficultyBand.MEDIUM, pick)
  }

  @Test
  fun `series of incorrect answers steps down but does not underflow`() {
    var state = policy.initialState(totalQuestions = 4)

    // Move up once to exercise the HARD → MEDIUM drop path.
    repeat(2) {
      val pick = policy.pickNextDifficulty(state)
      state = policy.nextState(state, servedBand = pick, wasCorrect = true)
    }
    assertEquals(DifficultyBand.HARD, state.currentDifficulty)

    repeat(3) {
      val pick = policy.pickNextDifficulty(state)
      state = policy.nextState(state, servedBand = pick, wasCorrect = false)
    }

    assertEquals(DifficultyBand.EASY, state.currentDifficulty)
    assertEquals(0, state.correctStreak)
    assertEquals(1, state.incorrectStreak)
    assertEquals(0, state.remainingQuestions)
  }

  @Test
  fun `does not underflow below easy`() {
    var state = policy.initialState(totalQuestions = 2)
    state = policy.nextState(state, servedBand = policy.pickNextDifficulty(state), wasCorrect = false)
    state = policy.nextState(state, servedBand = DifficultyBand.EASY, wasCorrect = false)
    assertEquals(DifficultyBand.EASY, state.currentDifficulty)
    assertTrue(state.askedPerBand.getValue(DifficultyBand.EASY) >= 1)
  }

  @Test
  fun `aligns streaks with the band actually served`() {
    var state = policy.initialState(totalQuestions = 3)
    state = policy.nextState(state, servedBand = DifficultyBand.EASY, wasCorrect = true)
    assertEquals(2, state.remainingQuestions)
    assertEquals(1, state.askedPerBand.getValue(DifficultyBand.EASY))
    assertEquals(DifficultyBand.EASY, state.currentDifficulty)
    // Switching back to MEDIUM should reset streaks before applying result.
    state = policy.nextState(state, servedBand = DifficultyBand.MEDIUM, wasCorrect = false)
    assertEquals(1, state.remainingQuestions)
    assertEquals(1, state.askedPerBand.getValue(DifficultyBand.MEDIUM))
    assertEquals(DifficultyBand.EASY, state.currentDifficulty)
  }

  @Test
  fun `zig zag answers respect hysteresis`() {
    var state = policy.initialState(totalQuestions = 6)
    val served = mutableListOf<DifficultyBand>()

    val answers = listOf(true, false, true, false, true, true)
    answers.forEach { wasCorrect ->
      val pick = policy.pickNextDifficulty(state)
      served += pick
      state = policy.nextState(state, servedBand = pick, wasCorrect = wasCorrect)
    }

    served.zipWithNext { prev, next ->
      assertTrue(
        "Difficulty changed too aggressively from $prev to $next",
        (prev.ordinal - next.ordinal).absoluteValue <= 1,
      )
    }
    assertEquals(DifficultyBand.MEDIUM, state.currentDifficulty)
    assertEquals(0, state.incorrectStreak)
  }
}
