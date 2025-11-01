package com.qweld.app.domain.exam.util

import com.qweld.app.domain.exam.ExamAssemblyConfig
import com.qweld.app.domain.exam.ItemStats
import java.time.Instant
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

fun computeWeight(
  stats: ItemStats?,
  config: ExamAssemblyConfig,
  now: Instant,
  bias: Double = 1.0,
): Double {
  val attempts = stats?.attempts ?: 0
  val correct = stats?.correct ?: 0
  val novelty =
    if (attempts == 0) {
      config.noveltyBoost
    } else {
      val last = stats?.lastAnsweredAt
      if (last != null) {
        val days = java.time.Duration.between(last, now).toDays()
        if (days >= config.freshDays) config.noveltyBoost else 1.0
      } else {
        1.0
      }
    }
  val weight = novelty * 2.0.pow(-correct / config.halfLifeCorrect) * bias
  return weight.coerceIn(config.minWeight, config.maxWeight)
}

data class WeightedEntry<T>(
  val item: T,
  val weight: Double,
  val key: Double,
)

class WeightedSampler(private val rng: Pcg32) {
  fun <T> order(items: List<T>, weightProvider: (T) -> Double): List<WeightedEntry<T>> {
    return items
      .map { item ->
        val weight = max(weightProvider(item), 1e-9)
        val key = -ln(nextUnitInterval()) / weight
        WeightedEntry(item = item, weight = weight, key = key)
      }
      .sortedBy { it.key }
  }

  fun <T> sample(items: List<T>, weightProvider: (T) -> Double, count: Int): List<T> {
    require(count <= items.size) { "Cannot sample more items than available" }
    return order(items, weightProvider).take(count).map { it.item }
  }

  private fun nextUnitInterval(): Double {
    val value = rng.nextUInt()
    // ensure (0,1]
    return (value + 1.0) / (4294967296.0 + 1.0)
  }
}

fun formatWeightsStats(weights: List<Double>): String {
  if (weights.isEmpty()) return "n=0 mean=0.0000 p50=0.0000 p90=0.0000 max=0.0000"
  val sorted = weights.sorted()
  val n = sorted.size
  val mean = sorted.sum() / n
  val p50 = sorted[(0.5 * (n - 1)).toInt()]
  val p90Index = ((n - 1) * 0.9).toInt()
  val p90 = sorted[p90Index]
  val maxValue = sorted.last()
  return String.format(
    Locale.US,
    "n=%d mean=%.4f p50=%.4f p90=%.4f max=%.4f",
    n,
    mean,
    p50,
    p90,
    maxValue
  )
}
