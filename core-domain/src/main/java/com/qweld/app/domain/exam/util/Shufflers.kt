package com.qweld.app.domain.exam.util

import com.qweld.app.domain.exam.Choice
import com.qweld.app.domain.exam.Question

fun <T> fisherYatesShuffle(list: MutableList<T>, rng: Pcg32) {
  for (i in list.size - 1 downTo 1) {
    val j = rng.nextInt(i + 1)
    if (i != j) {
      list.swap(i, j)
    }
  }
}

fun enforceAntiCluster(
  questions: MutableList<Question>,
  maxTaskRun: Int,
  maxBlockRun: Int,
  rng: Pcg32,
  maxSwaps: Int,
): Int {
  var swaps = 0
  while (swaps < maxSwaps) {
    val taskRun = findRun(questions, maxTaskRun) { it.taskId }
    val blockRun = findRun(questions, maxBlockRun) { it.blockId }
    val run = taskRun ?: blockRun ?: break
    val isTaskRun = run == taskRun
    val key = if (isTaskRun) questions[run.first].taskId else questions[run.first].blockId
    val candidates =
      questions.indices.filter { idx ->
        idx !in run && (if (isTaskRun) questions[idx].taskId else questions[idx].blockId) != key
      }
    if (candidates.isEmpty()) {
      break
    }
    val swapIndex = candidates[rng.nextInt(candidates.size)]
    val inRunIndex = run.first + rng.nextInt(run.count())
    questions.swap(inRunIndex, swapIndex)
    swaps++
  }
  return swaps
}

private fun <T> findRun(
  items: List<T>,
  maxAllowed: Int,
  selector: (T) -> String,
): IntRange? {
  if (items.isEmpty()) return null
  var currentValue = selector(items.first())
  var count = 1
  for (index in 1 until items.size) {
    val value = selector(items[index])
    if (value == currentValue) {
      count++
      if (count > maxAllowed) {
        return (index - count + 1)..index
      }
    } else {
      currentValue = value
      count = 1
    }
  }
  return null
}

data class MutableQuestionState(
  val question: Question,
  val choices: MutableList<Choice>,
  var correctIndex: Int,
)

data class ChoiceBalanceResult(
  val adjusted: Boolean,
  val histogram: Map<String, Int>,
)

fun balanceCorrectPositions(
  states: MutableList<MutableQuestionState>,
  rng: Pcg32,
): ChoiceBalanceResult {
  if (states.isEmpty()) {
    return ChoiceBalanceResult(adjusted = false, histogram = emptyMap())
  }
  val targetCounts = IntArray(4)
  val total = states.size
  val base = total / 4
  val remainder = total % 4
  for (index in 0 until 4) {
    targetCounts[index] = base + if (index < remainder) 1 else 0
  }
  val pattern = mutableListOf<Int>()
  for (index in 0 until 4) {
    repeat(targetCounts[index]) { pattern.add(index) }
  }
  fisherYatesShuffle(pattern, rng)

  var adjusted = false
  val histogram = mutableMapOf("A" to 0, "B" to 0, "C" to 0, "D" to 0)
  states.forEachIndexed { idx, state ->
    val target = pattern[idx]
    if (state.correctIndex != target) {
      val currentIndex = state.correctIndex
      state.choices.swap(currentIndex, target)
      state.correctIndex = target
      adjusted = true
    }
    val bucket =
      when (state.correctIndex) {
        0 -> "A"
        1 -> "B"
        2 -> "C"
        else -> "D"
      }
    histogram[bucket] = histogram.getValue(bucket) + 1
  }
  return ChoiceBalanceResult(adjusted = adjusted, histogram = histogram)
}

private fun <T> MutableList<T>.swap(i: Int, j: Int) {
  val tmp = this[i]
  this[i] = this[j]
  this[j] = tmp
}
