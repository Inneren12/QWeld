package com.qweld.app.feature.exam.vm

/**
 * User-configurable practice parameters.
 */
data class PracticeConfig(
  val requestedSize: Int = DEFAULT_SIZE,
  val wrongBiased: Boolean = false,
) {
  val size: Int
    get() = PRESETS.firstOrNull { it == requestedSize } ?: DEFAULT_SIZE

  companion object {
    val PRESETS: List<Int> = listOf(10, 20, 30)
    const val DEFAULT_SIZE: Int = 20
  }
}
