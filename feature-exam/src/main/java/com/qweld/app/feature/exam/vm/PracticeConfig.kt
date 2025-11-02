package com.qweld.app.feature.exam.vm

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

    fun sanitizeSize(value: Int): Int {
      return PRESETS.firstOrNull { it == value } ?: DEFAULT_SIZE
    }
  }
}
