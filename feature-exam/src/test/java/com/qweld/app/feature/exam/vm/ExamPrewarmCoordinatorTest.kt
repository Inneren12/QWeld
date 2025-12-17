package com.qweld.app.feature.exam.vm

import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.feature.exam.model.PrewarmUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract tests for ExamPrewarmCoordinator.
 *
 * These tests verify that the DefaultExamPrewarmCoordinator correctly implements
 * the ExamPrewarmCoordinator contract and properly orchestrates prewarming flows.
 * This is a regression test ensuring that the prewarm coordinator extracted from
 * ExamViewModel maintains correct behavior after DI introduction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExamPrewarmCoordinatorTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var testScope: TestScope
  private lateinit var fakePrewarmRunner: FakePrewarmController
  private lateinit var coordinator: ExamPrewarmCoordinator

  @Before
  fun setup() {
    testScope = TestScope(testDispatcher)
    fakePrewarmRunner = FakePrewarmController()

    coordinator = DefaultExamPrewarmCoordinator(
      scope = testScope,
      ioDispatcher = testDispatcher,
      blueprintProvider = { mode, size -> createFakeBlueprint(size) },
      prewarmRunner = fakePrewarmRunner
    )
  }

  @Test
  fun `prewarmState starts with default empty state`() = testScope.runTest {
    // When
    val state = coordinator.prewarmState.value

    // Then
    assertEquals("", state.locale, "Default locale should be empty")
    assertEquals(0, state.loaded, "Default loaded should be 0")
    assertEquals(0, state.total, "Default total should be 0")
    assertFalse(state.isRunning, "Default isRunning should be false")
    assertFalse(state.isReady, "Default isReady should be false")
  }

  @Test
  fun `startForIpMock sets isRunning true immediately`() = testScope.runTest {
    // When
    coordinator.startForIpMock("en")

    // Then
    val state = coordinator.prewarmState.value
    assertTrue(state.isRunning, "Should be running after start")
    assertFalse(state.isReady, "Should not be ready immediately")
    assertEquals("en", state.locale, "Locale should be set")
  }

  @Test
  fun `startForIpMock invokes PrewarmController with correct tasks`() = testScope.runTest {
    // Given
    val locale = "en"

    // When
    coordinator.startForIpMock(locale)
    advanceUntilIdle()

    // Then
    assertTrue(fakePrewarmRunner.prewarmCalled, "PrewarmController.prewarm should be called")
    assertEquals(locale, fakePrewarmRunner.lastLocale, "Should pass correct locale")
    assertTrue(fakePrewarmRunner.lastTasks.isNotEmpty(), "Should pass tasks from blueprint")
  }

  @Test
  fun `startForIpMock emits progress updates from PrewarmController`() = testScope.runTest {
    // Given
    fakePrewarmRunner.simulateProgressSteps = listOf(
      0 to 3,
      1 to 3,
      2 to 3,
      3 to 3
    )

    // When
    coordinator.startForIpMock("en")
    advanceUntilIdle()

    // Then - final state should reflect completion
    val finalState = coordinator.prewarmState.value
    assertEquals(3, finalState.loaded, "Should reach total loaded")
    assertEquals(3, finalState.total, "Should have correct total")
    assertTrue(finalState.isReady, "Should be ready when complete")
    assertFalse(finalState.isRunning, "Should not be running when complete")
  }

  @Test
  fun `startForIpMock normalizes locale to lowercase`() = testScope.runTest {
    // When
    coordinator.startForIpMock("EN")
    advanceUntilIdle()

    // Then
    val state = coordinator.prewarmState.value
    assertEquals("en", state.locale, "Locale should be normalized to lowercase")
    assertEquals("en", fakePrewarmRunner.lastLocale, "PrewarmController should receive normalized locale")
  }

  @Test
  fun `startForIpMock ignores duplicate calls for same locale when ready`() = testScope.runTest {
    // Given - complete first prewarm
    coordinator.startForIpMock("en")
    advanceUntilIdle()
    val firstCallCount = fakePrewarmRunner.callCount

    // When - call again with same locale
    coordinator.startForIpMock("en")
    advanceUntilIdle()

    // Then - should not prewarm again
    assertEquals(firstCallCount, fakePrewarmRunner.callCount, "Should not prewarm again for same locale when ready")
  }

  @Test
  fun `startForIpMock ignores call when already running`() = testScope.runTest {
    // Given - start prewarm
    fakePrewarmRunner.delayMillis = 1000L
    coordinator.startForIpMock("en")

    // When - call again while running
    coordinator.startForIpMock("en")
    advanceUntilIdle()

    // Then - should only call once
    assertEquals(1, fakePrewarmRunner.callCount, "Should only prewarm once when already running")
  }

  @Test
  fun `cancel stops ongoing prewarm job`() = testScope.runTest {
    // Given - start long-running prewarm
    fakePrewarmRunner.delayMillis = 5000L
    coordinator.startForIpMock("en")

    // When
    coordinator.cancel()
    advanceUntilIdle()

    // Then - state should not be ready since we cancelled
    val state = coordinator.prewarmState.value
    assertFalse(state.isReady, "Should not be ready after cancel")
  }

  @Test
  fun `handles errors gracefully and finalizes state`() = testScope.runTest {
    // Given - configure runner to throw error
    fakePrewarmRunner.shouldThrowError = true

    // When
    coordinator.startForIpMock("en")
    advanceUntilIdle()

    // Then - should still finalize to ready state
    val state = coordinator.prewarmState.value
    assertTrue(state.isReady, "Should finalize to ready even on error")
    assertFalse(state.isRunning, "Should not be running after error")
  }

  @Test
  fun `progress updates clamp loaded to total`() = testScope.runTest {
    // Given - simulate progress that exceeds total
    fakePrewarmRunner.simulateProgressSteps = listOf(
      0 to 3,
      5 to 3 // Loaded exceeds total
    )

    // When
    coordinator.startForIpMock("en")
    advanceUntilIdle()

    // Then - loaded should be clamped to total
    val state = coordinator.prewarmState.value
    assertTrue(state.loaded <= state.total, "Loaded should not exceed total")
  }

  // Helper to create a fake blueprint with tasks
  private fun createFakeBlueprint(size: Int): ExamBlueprint {
    return ExamBlueprint(
      totalQuestions = size,
      taskQuotas = listOf(
        TaskQuota(taskId = "A-1", blockId = "A", required = 10),
        TaskQuota(taskId = "A-2", blockId = "A", required = 10),
        TaskQuota(taskId = "B-1", blockId = "B", required = 10)
      )
    )
  }

  // Fake PrewarmController for testing
  private class FakePrewarmController : PrewarmController {
    var prewarmCalled = false
    var callCount = 0
    var lastLocale: String = ""
    var lastTasks: Set<String> = emptySet()
    var delayMillis = 100L
    var shouldThrowError = false
    var simulateProgressSteps: List<Pair<Int, Int>> = emptyList()

    override suspend fun prewarm(
      locale: String,
      tasks: Set<String>,
      onProgress: (loaded: Int, total: Int) -> Unit
    ): PrewarmUseCase.RunResult {
      prewarmCalled = true
      callCount++
      lastLocale = locale
      lastTasks = tasks

      if (shouldThrowError) {
        throw RuntimeException("Simulated prewarm error")
      }

      // Simulate progress
      if (simulateProgressSteps.isNotEmpty()) {
        for ((loaded, total) in simulateProgressSteps) {
          delay(delayMillis / simulateProgressSteps.size)
          onProgress(loaded, total)
        }
      } else {
        val total = tasks.size
        for (i in 0..total) {
          delay(delayMillis / (total + 1))
          onProgress(i, total)
        }
      }

      // Return a completed result for the test
      return PrewarmUseCase.RunResult.Completed(
        locale = locale,
        requestedTasks = tasks,
        tasksLoaded = tasks.size,
        fallbackToBank = false,
        hadError = false,
        elapsedMs = delayMillis,
        usedQuestionBank = false
      )
    }
  }
}
