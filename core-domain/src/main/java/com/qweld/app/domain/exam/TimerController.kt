package com.qweld.app.domain.exam

import java.time.Clock
import java.time.Duration
import java.time.Instant

class TimerController(
    private val clock: Clock = Clock.systemUTC(),
    private val logger: (String) -> Unit = ::println,
) {
    companion object {
        val EXAM_DURATION: Duration = Duration.ofHours(4)
    }

    private var startedAt: Instant? = null
    private var stoppedAt: Instant? = null

    fun start() {
        startedAt = clock.instant()
        stoppedAt = null
        logger("[timer_start] startedAt=${startedAt} duration=$EXAM_DURATION")
    }

    fun remaining(): Duration {
        val now = clock.instant()
        val remaining = computeRemaining(now)
        logger("[timer_tick] remaining=$remaining")
        return remaining
    }

    fun stop() {
        val stopInstant = clock.instant()
        stoppedAt = stopInstant
        val remaining = computeRemaining(stopInstant)
        logger("[timer_stop] stoppedAt=$stopInstant remaining=$remaining")
    }

    private fun computeRemaining(now: Instant): Duration {
        val start = startedAt ?: return EXAM_DURATION
        val effectiveStop = stoppedAt ?: now
        val elapsed = Duration.between(start, effectiveStop)
        val remaining = EXAM_DURATION.minus(elapsed)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }
}
