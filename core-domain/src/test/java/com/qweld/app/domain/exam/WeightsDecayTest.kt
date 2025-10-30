package com.qweld.app.domain.exam

import com.qweld.app.domain.exam.util.computeWeight
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class WeightsDecayTest {
    private val config = ExamAssemblyConfig()

    @Test
    fun `novel questions receive boost`() {
        val now = Instant.parse("2024-02-01T00:00:00Z")
        val stats = ItemStats(questionId = "q1", attempts = 0, correct = 0, lastAnsweredAt = null)
        val weight = computeWeight(stats, config, now)
        assertEquals(2.0, weight, 1e-6)
    }

    @Test
    fun `weight decays with correct answers`() {
        val now = Instant.parse("2024-02-01T00:00:00Z")
        val stats = ItemStats(questionId = "q2", attempts = 5, correct = 4, lastAnsweredAt = now)
        val weight = computeWeight(stats, config, now)
        assertEquals(0.25, weight, 1e-6)
    }

    @Test
    fun `weight clamped to min when heavily mastered`() {
        val now = Instant.parse("2024-02-01T00:00:00Z")
        val stats = ItemStats(questionId = "q3", attempts = 200, correct = 120, lastAnsweredAt = now)
        val weight = computeWeight(stats, config, now)
        assertEquals(0.05, weight, 1e-6)
    }

    @Test
    fun `long inactivity triggers novelty boost`() {
        val now = Instant.parse("2024-02-15T00:00:00Z")
        val old = now.minusSeconds(21 * 24 * 3600)
        val stats = ItemStats(questionId = "q4", attempts = 3, correct = 1, lastAnsweredAt = old)
        val weight = computeWeight(stats, config, now)
        // base weight 2^(-1/2)=~0.7071, novelty boost doubles
        assertEquals(1.4142, weight, 1e-3)
    }
}
