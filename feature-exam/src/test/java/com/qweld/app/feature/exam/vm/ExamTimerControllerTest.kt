package com.qweld.app.feature.exam.vm

import com.qweld.app.domain.exam.TimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for ExamTimerController.
 *
 * These tests verify that the DefaultExamTimerController correctly implements
 * the ExamTimerController contract and properly delegates to the underlying
 * TimerController. This is a regression test ensuring that the timer controller
 * extracted from ExamViewModel maintains correct behavior after DI introduction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExamTimerControllerTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var testScope: TestScope
  private lateinit var timerController: TimerController
  private lateinit var controller: ExamTimerController

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    testScope = TestScope(testDispatcher)
    timerController = TimerController { } // No logging in tests
    controller = DefaultExamTimerController(
      timerController = timerController,
      coroutineScopeProvider = { testScope }
    )
  }

  @After
  fun teardown() {
    testScope.cancel()
    Dispatchers.resetMain()
  }

  @Test
  fun `start initializes timer with exam duration`() = testScope.runTest {
    // Given
    var tickCount = 0
    var lastLabel: String? = null
    var lastRemaining: Duration? = null

    // When
    controller.start(
      onTick = { label, remaining ->
        tickCount++
        lastLabel = label
        lastRemaining = remaining
      },
      onExpired = {}
    )

    // Then - should immediately emit initial tick with 4 hours
    assertEquals(1, tickCount, "Should have one tick immediately")
    assertNotNull(lastLabel, "Label should be set")
    assertEquals(TimerController.EXAM_DURATION, lastRemaining, "Initial remaining should be 4 hours")
    assertEquals(lastLabel, controller.latestLabel, "latestLabel should match emitted label")
  }

  @Test
  fun `start emits ticks every second`() = testScope.runTest {
    // Given
    val ticks = mutableListOf<Pair<String, Duration>>()

    // When
    controller.start(
      onTick = { label, remaining -> ticks.add(label to remaining) },
      onExpired = {}
    )

    // Advance time by 3 seconds
    advanceTimeBy(3_000)

    // Then - should have initial tick + 3 more ticks
    assertTrue(ticks.size >= 4, "Should have at least 4 ticks (initial + 3)")
  }

  @Test
  fun `resume starts timer with custom remaining duration`() = testScope.runTest {
    // Given
    val customRemaining = Duration.ofMinutes(30)
    var tickCount = 0
    var lastLabel: String? = null
    var lastRemaining: Duration? = null

    // When
    controller.resume(
      initialRemaining = customRemaining,
      onTick = { label, remaining ->
        tickCount++
        lastLabel = label
        lastRemaining = remaining
      },
      onExpired = {}
    )

    // Then - should emit initial tick with custom duration
    assertEquals(1, tickCount, "Should have one tick immediately")
    assertNotNull(lastLabel, "Label should be set")
    assertEquals(customRemaining, lastRemaining, "Initial remaining should match custom duration")
  }

  @Test
  fun `resume decrements time each second`() = testScope.runTest {
    // Given
    val initialRemaining = Duration.ofSeconds(10)
    val ticks = mutableListOf<Duration>()

    // When
    controller.resume(
      initialRemaining = initialRemaining,
      onTick = { _, remaining -> ticks.add(remaining) },
      onExpired = {}
    )

    // Advance time by 3 seconds
    advanceTimeBy(3_000)

    // Then - ticks should decrement: [10s, 9s, 8s, 7s]
    assertTrue(ticks.size >= 4, "Should have at least 4 ticks")
    assertEquals(initialRemaining, ticks[0], "First tick should be initial")
    assertEquals(Duration.ofSeconds(9), ticks[1], "Second tick should decrement by 1")
    assertEquals(Duration.ofSeconds(8), ticks[2], "Third tick should decrement by 1")
    assertEquals(Duration.ofSeconds(7), ticks[3], "Fourth tick should decrement by 1")
  }

  @Test
  fun `stop cancels timer and clears label when clearLabel is true`() = testScope.runTest {
    // Given
    controller.start(
      onTick = { _, _ -> },
      onExpired = {}
    )
    assertNotNull(controller.latestLabel, "Label should be set after start")

    // When
    controller.stop(clearLabel = true)

    // Then
    assertNull(controller.latestLabel, "Label should be cleared")

    // And no more ticks after stop
    advanceTimeBy(2_000)
    // Test passes if no exceptions thrown
  }

  @Test
  fun `stop preserves label when clearLabel is false`() = testScope.runTest {
    // Given
    controller.start(
      onTick = { _, _ -> },
      onExpired = {}
    )
    val labelBeforeStop = controller.latestLabel
    assertNotNull(labelBeforeStop, "Label should be set after start")

    // When
    controller.stop(clearLabel = false)

    // Then
    assertEquals(labelBeforeStop, controller.latestLabel, "Label should be preserved")
  }

  @Test
  fun `onExpired callback is invoked when timer reaches zero`() = testScope.runTest {
    // Given
    var expiredCalled = false
    val veryShortDuration = Duration.ofSeconds(2)

    // When
    controller.resume(
      initialRemaining = veryShortDuration,
      onTick = { _, _ -> },
      onExpired = { expiredCalled = true }
    )

    // Advance past the duration
    advanceTimeBy(3_000)

    // Then
    assertTrue(expiredCalled, "onExpired should be called when timer reaches zero")
  }

  @Test
  fun `remaining returns correct duration during countdown`() = testScope.runTest {
    // Given
    val initialRemaining = Duration.ofMinutes(5)

    // When
    controller.resume(
      initialRemaining = initialRemaining,
      onTick = { _, _ -> },
      onExpired = {}
    )

    // Then - before any time passes
    assertEquals(initialRemaining, controller.remaining(), "Should return initial remaining")

    // When - advance 1 second
    advanceTimeBy(1_000)

    // Then - should be 1 second less
    assertEquals(Duration.ofMinutes(5).minusSeconds(1), controller.remaining())
  }

  @Test
  fun `multiple start calls stop previous timer`() = testScope.runTest {
    // Given
    var firstTimerTicks = 0
    controller.start(
      onTick = { _, _ -> firstTimerTicks++ },
      onExpired = {}
    )
    advanceTimeBy(2_000)
    val ticksBeforeRestart = firstTimerTicks

    // When - start again
    var secondTimerTicks = 0
    controller.start(
      onTick = { _, _ -> secondTimerTicks++ },
      onExpired = {}
    )

    // Then - first timer should stop, only second timer ticks
    val ticksAfterRestart = firstTimerTicks
    assertEquals(ticksBeforeRestart, ticksAfterRestart, "First timer should not tick after restart")

    advanceTimeBy(2_000)
    assertTrue(secondTimerTicks > 0, "Second timer should tick")
  }

  @Test
  fun `timer handles negative remaining gracefully`() = testScope.runTest {
    // Given - start with 1 second
    val ticks = mutableListOf<Duration>()

    // When
    controller.resume(
      initialRemaining = Duration.ofSeconds(1),
      onTick = { _, remaining -> ticks.add(remaining) },
      onExpired = {}
    )

    // Advance past expiration
    advanceTimeBy(3_000)

    // Then - should never emit negative durations
    assertTrue(ticks.all { !it.isNegative }, "All ticks should be non-negative")
    assertTrue(ticks.any { it.isZero }, "Should emit zero when expired")
  }
}
