package com.qweld.app.domain.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultAdaptiveExamPolicyTest {

  private val policy = DefaultAdaptiveExamPolicy()

  @Test
  fun `moves up after two consecutive correct answers`() {
    var state = policy.initialState(totalQuestions = 3)
    state = policy.nextState(state, wasCorrect = true)
    state = policy.nextState(state, wasCorrect = true)
    assertEquals(DifficultyBand.HARD, policy.pickNextDifficulty(state))
    assertEquals(2, state.askedPerBand.getValue(DifficultyBand.MEDIUM))
    assertEquals(1, state.remainingQuestions)
  }

  @Test
  fun `drops after a single incorrect answer`() {
    var state = policy.initialState(totalQuestions = 4)
    state = policy.nextState(state, wasCorrect = false)
    assertEquals(DifficultyBand.EASY, state.currentDifficulty)
    assertEquals(1, state.askedPerBand.getValue(DifficultyBand.MEDIUM))
    assertEquals(3, state.remainingQuestions)
  }

  @Test
  fun `prefers medium near the end of the exam`() {
    var state = policy.initialState(totalQuestions = 2)
    state = policy.nextState(state, wasCorrect = true)
    val pick = policy.pickNextDifficulty(state)
    assertEquals(DifficultyBand.MEDIUM, pick)
  }

  @Test
  fun `does not underflow below easy`() {
    var state = policy.initialState(totalQuestions = 2)
    state = policy.nextState(state, wasCorrect = false)
    state = policy.nextState(state, wasCorrect = false)
    assertEquals(DifficultyBand.EASY, state.currentDifficulty)
    assertTrue(state.askedPerBand.getValue(DifficultyBand.EASY) >= 1)
  }
}
