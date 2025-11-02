package com.qweld.app.feature.exam.vm

import com.qweld.app.data.prefs.UserPrefsDataStore

/**
 * User-configurable practice parameters.
 */
data class PracticeScope(
  val blocks: Set<String> = setOf("A", "B", "C", "D"),
  val taskIds: Set<String> = emptySet(),
  val distribution: Distribution = Distribution.Proportional,
)

enum class Distribution {
  Proportional,
  Even,
}

data class PracticeConfig(
  val size: Int = DEFAULT_SIZE,
  val scope: PracticeScope = PracticeScope(),
  val wrongBiased: Boolean = false,
) {
  companion object {
    val PRESETS: List<Int> = listOf(10, 20, 30)
    const val DEFAULT_SIZE: Int = 20
    const val MIN_SIZE: Int = UserPrefsDataStore.MIN_PRACTICE_SIZE
    const val MAX_SIZE: Int = UserPrefsDataStore.MAX_PRACTICE_SIZE

    fun sanitizeSize(value: Int): Int {
      return value.coerceIn(MIN_SIZE, MAX_SIZE)
    }
  }
}
