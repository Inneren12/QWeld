package com.qweld.app.domain.exam

import kotlin.math.floor

object QuotaDistributor {
  /**
   * Scales blueprint quotas for the chosen tasks to [total] using the largest remainder method.
   */
  fun proportional(
    blueprintQuotas: Map<String, Int>,
    chosen: Set<String>,
    total: Int,
  ): Map<String, Int> {
    if (total <= 0 || chosen.isEmpty()) return emptyMap()
    val entries =
      chosen.mapIndexed { index, taskId ->
        val weight = blueprintQuotas[taskId]?.coerceAtLeast(0) ?: 0
        Entry(taskId = taskId, weight = weight.toDouble(), order = index)
      }
    val weightSum = entries.sumOf { it.weight }
    if (weightSum <= 0.0) {
      return even(chosen, total)
    }
    return distribute(entries, total, weightSum)
  }

  /**
   * Evenly splits [total] between the chosen tasks with largest remainder rounding.
   */
  fun even(
    chosen: Set<String>,
    total: Int,
  ): Map<String, Int> {
    if (total <= 0 || chosen.isEmpty()) return emptyMap()
    val entries = chosen.mapIndexed { index, taskId -> Entry(taskId, 1.0, index) }
    val weightSum = entries.sumOf { it.weight }
    if (weightSum <= 0.0) return emptyMap()
    return distribute(entries, total, weightSum)
  }

  private fun distribute(
    entries: List<Entry>,
    total: Int,
    weightSum: Double,
  ): Map<String, Int> {
    if (entries.isEmpty()) return emptyMap()
    val sanitizedTotal = total.coerceAtLeast(0)
    if (sanitizedTotal == 0) return emptyMap()
    val baseAllocations = LinkedHashMap<String, Int>(entries.size)
    val remainders = ArrayList<Remainder>(entries.size)
    var allocated = 0
    for (entry in entries) {
      if (entry.weight <= 0.0) {
        baseAllocations[entry.taskId] = 0
        remainders += Remainder(entry, 0.0)
        continue
      }
      val exact = sanitizedTotal * entry.weight / weightSum
      val base = floor(exact).toInt()
      baseAllocations[entry.taskId] = base
      allocated += base
      val remainder = (exact - base).coerceAtLeast(0.0)
      remainders += Remainder(entry, remainder)
    }
    var remaining = sanitizedTotal - allocated
    if (remaining > 0 && remainders.isNotEmpty()) {
      val ordered =
        remainders.sortedWith(
          compareByDescending<Remainder> { it.remainder }
            .thenByDescending { it.entry.weight }
            .thenBy { it.entry.order },
        )
      var index = 0
      while (remaining > 0 && ordered.isNotEmpty()) {
        val target = ordered[index % ordered.size]
        baseAllocations[target.entry.taskId] = baseAllocations.getValue(target.entry.taskId) + 1
        remaining -= 1
        index += 1
      }
    }
    val result = LinkedHashMap<String, Int>()
    for (entry in entries) {
      val value = baseAllocations[entry.taskId] ?: 0
      if (value > 0) {
        result[entry.taskId] = value
      }
    }
    return result
  }

  private data class Entry(val taskId: String, val weight: Double, val order: Int)

  private data class Remainder(val entry: Entry, val remainder: Double)
}
