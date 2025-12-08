package com.qweld.app.domain.exam

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimerControllerTest {
  @Test
  fun startSetsFullExamDuration() {
    val clock = FakeClock()
    val timer = TimerController(clock = clock) { }

    timer.start()

    assertEquals(TimerController.EXAM_DURATION, timer.remaining())
  }

  @Test
  fun remainingReflectsElapsedTime() {
    val clock = FakeClock()
    val timer = TimerController(clock = clock) { }

    timer.start()
    clock.advance(Duration.ofMinutes(15))

    assertEquals(Duration.ofHours(3).plusMinutes(45), timer.remaining())
  }

  @Test
  fun stopFreezesRemainingTime() {
    val clock = FakeClock()
    val timer = TimerController(clock = clock) { }

    timer.start()
    clock.advance(Duration.ofMinutes(90))

    val beforeStop = timer.remaining()
    timer.stop()
    clock.advance(Duration.ofMinutes(30))

    assertEquals(beforeStop, timer.remaining())
  }

  @Test
  fun remainingDoesNotGoNegative() {
    val clock = FakeClock()
    val timer = TimerController(clock = clock) { }

    timer.start()
    clock.advance(Duration.ofHours(5))

    assertTrue(timer.remaining().isZero)
  }
}

private class FakeClock(
  private var now: Instant = Instant.parse("2024-01-01T00:00:00Z"),
  private val zoneId: ZoneId = ZoneId.systemDefault(),
) : java.time.Clock() {
  override fun getZone(): ZoneId = zoneId

  override fun withZone(zone: ZoneId): java.time.Clock = FakeClock(now, zone)

  override fun instant(): Instant = now

  fun advance(duration: Duration) {
    now = now.plus(duration)
  }
}
