package com.qweld.app.domain.exam

import com.qweld.app.domain.exam.util.Pcg32
import com.qweld.app.domain.exam.util.WeightedSampler
import com.qweld.app.domain.exam.util.computeWeight
import java.time.Instant
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class WeightedSamplerTest {
  @Test
  fun `items with lower mastery appear more often`() {
    val now = Instant.parse("2024-01-01T00:00:00Z")
    val config = ExamAssemblyConfig()
    val stats =
      mapOf(
        "novice" to
          ItemStats("novice", attempts = 1, correct = 0, lastAnsweredAt = now.minusSeconds(86400)),
        "master" to ItemStats("master", attempts = 15, correct = 12, lastAnsweredAt = now),
      )
    var noviceFirst = 0
    var masterFirst = 0
    repeat(500) { idx ->
      val sampler = WeightedSampler(Pcg32(idx.toLong() + 1))
      val order =
        sampler.order(listOf("novice", "master")) { id -> computeWeight(stats[id], config, now) }
      if (order.first().item == "novice") {
        noviceFirst++
      } else {
        masterFirst++
      }
    }
    assertTrue(noviceFirst > masterFirst)
  }
}
