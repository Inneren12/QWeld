package com.qweld.app.domain.exam

import kotlin.math.abs

fun repeatSeeds(range: LongRange = 0L..499L, action: (Long) -> Unit) {
  for (seed in range) {
    try {
      action(seed)
    } catch (failure: Throwable) {
      val reason = failure.message ?: failure::class.qualifiedName ?: "unknown"
      val message = "[tests] seed=$seed reason=$reason"
      throw AssertionError(message, failure)
    }
  }
}

fun <T> List<T>.maxRunLengthBy(selector: (T) -> String): Int {
  if (isEmpty()) return 0
  var maxRun = 1
  var currentKey = selector(first())
  var currentLength = 1
  for (index in 1 until size) {
    val key = selector(get(index))
    if (key == currentKey) {
      currentLength += 1
      if (currentLength > maxRun) {
        maxRun = currentLength
      }
    } else {
      currentKey = key
      currentLength = 1
    }
  }
  return maxRun
}

private val choiceLabels = listOf("A", "B", "C", "D")

fun List<AssembledQuestion>.histogramOfCorrectPositions(): Map<String, Int> {
  if (isEmpty()) return emptyMap()
  val counts = mutableMapOf<String, Int>()
  choiceLabels.forEach { counts[it] = 0 }
  for (question in this) {
    val label = choiceLabels[question.correctIndex]
    counts[label] = counts.getValue(label) + 1
  }
  return counts
}

fun Double.isCloseTo(other: Double, tolerance: Double): Boolean {
  if (this == other) return true
  if (this.isNaN() || other.isNaN()) return false
  val diff = abs(this - other)
  val scale = maxOf(abs(other), 1e-9)
  return diff <= scale * tolerance
}
