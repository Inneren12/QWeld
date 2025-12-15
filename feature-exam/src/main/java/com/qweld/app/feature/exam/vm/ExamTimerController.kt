package com.qweld.app.feature.exam.vm

import com.qweld.app.domain.exam.TimerController
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface ExamTimerController {
  val latestLabel: String?

  fun start(
    onTick: (label: String, remaining: Duration) -> Unit,
    onExpired: () -> Unit,
  )

  fun resume(
    initialRemaining: Duration,
    onTick: (label: String, remaining: Duration) -> Unit,
    onExpired: () -> Unit,
  )

  fun stop(clearLabel: Boolean)

  fun remaining(): Duration
}

class DefaultExamTimerController(
  private val timerController: TimerController,
  private val coroutineScopeProvider: () -> CoroutineScope,
) : ExamTimerController {

  override var latestLabel: String? = null
    private set

  private var manualRemaining: Duration? = null
  private var timerJob: Job? = null
  private var timerRunning: Boolean = false

  override fun start(
    onTick: (label: String, remaining: Duration) -> Unit,
    onExpired: () -> Unit,
  ) {
    stop(clearLabel = true)
    manualRemaining = null
    timerController.start()
    timerRunning = true
    val initialRemaining = TimerController.EXAM_DURATION
    latestLabel = TimerController.formatDuration(initialRemaining)
    onTick(checkNotNull(latestLabel), initialRemaining)
    timerJob = coroutineScopeProvider().launchTimerJob(
      tick = {
        val remaining = timerController.remaining()
        latestLabel = TimerController.formatDuration(remaining)
        onTick(checkNotNull(latestLabel), remaining)
        if (remaining.isZero) {
          onExpired()
        }
      },
    )
  }

  override fun resume(
    initialRemaining: Duration,
    onTick: (label: String, remaining: Duration) -> Unit,
    onExpired: () -> Unit,
  ) {
    stop(clearLabel = true)
    manualRemaining = initialRemaining
    timerRunning = true
    latestLabel = TimerController.formatDuration(initialRemaining)
    onTick(checkNotNull(latestLabel), initialRemaining)
    timerJob = coroutineScopeProvider().launchTimerJob(
      tick = {
        val updatedRemaining = (manualRemaining ?: initialRemaining).minusSeconds(1)
        val remaining = if (updatedRemaining.isNegative) Duration.ZERO else updatedRemaining
        manualRemaining = remaining
        latestLabel = TimerController.formatDuration(remaining)
        onTick(checkNotNull(latestLabel), remaining)
        if (remaining.isZero) {
          onExpired()
        }
      },
    )
  }

  override fun stop(clearLabel: Boolean) {
    timerJob?.cancel()
    timerJob = null
    if (timerRunning) {
      timerController.stop()
      timerRunning = false
    }
    manualRemaining = null
    if (clearLabel) {
      latestLabel = null
    }
  }

  override fun remaining(): Duration {
    val manual = manualRemaining
    if (manual != null) return manual
    return timerController.remaining()
  }
}

private fun CoroutineScope.launchTimerJob(
  tick: suspend () -> Unit,
): Job {
  return launch {
    while (isActive) {
      delay(1_000)
      tick()
    }
  }
}
