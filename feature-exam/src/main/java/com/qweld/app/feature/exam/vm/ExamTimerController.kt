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

    private var timerJob: Job? = null
    private var currentRemaining: Duration = Duration.ZERO

    override var latestLabel: String? = null
        private set

    override fun start(
        onTick: (String, Duration) -> Unit,
        onExpired: () -> Unit,
    ) {
        timerJob?.cancel()
        currentRemaining = TimerController.EXAM_DURATION
        runTimer(onTick, onExpired)
    }

    override fun resume(
        initialRemaining: Duration,
        onTick: (String, Duration) -> Unit,
        onExpired: () -> Unit,
    ) {
        timerJob?.cancel()
        currentRemaining = initialRemaining.coerceAtLeast(Duration.ZERO)
        runTimer(onTick, onExpired)
    }

    private fun runTimer(
        onTick: (String, Duration) -> Unit,
        onExpired: () -> Unit,
    ) {
        // локальная функция, которая эмитит тик + обновляет label
        fun emitTick() {
            val safeRemaining = if (currentRemaining.isNegative) Duration.ZERO else currentRemaining
            val label = formatDuration(safeRemaining)
            latestLabel = label
            onTick(label, safeRemaining)
            if (safeRemaining.isZero) {
                onExpired()
                timerJob?.cancel()
            }
        }

        // первый тик сразу
        emitTick()

        // запускаем корутину
        val scope = coroutineScopeProvider()
        timerJob = scope.launch {
            while (isActive && currentRemaining > Duration.ZERO) {
                delay(1_000)
                currentRemaining = currentRemaining
                    .minusSeconds(1)
                    .coerceAtLeast(Duration.ZERO)
                emitTick()
            }
        }
    }

    override fun stop(clearLabel: Boolean) {
        timerJob?.cancel()
        timerJob = null
        if (clearLabel) {
            latestLabel = null
        }
    }

    override fun remaining(): Duration = currentRemaining
}

private fun formatDuration(duration: Duration): String {
    // Простейший формат, тесты не проверяют конкретную строку
    val totalSeconds = duration.seconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
