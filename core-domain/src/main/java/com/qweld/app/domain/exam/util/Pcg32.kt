package com.qweld.app.domain.exam.util

class Pcg32(
  seed: Long,
  stream: Long = DEFAULT_INCREMENT,
) {
  companion object {
    private const val MULTIPLIER: Long = 6364136223846793005L
    private const val DEFAULT_INCREMENT: Long = 1442695040888963407L
  }

  private var state: Long = 0L
  private val increment: Long = (stream shl 1) or 1L

  init {
    advance()
    state += seed
    advance()
  }

  private fun advance() {
    state = state * MULTIPLIER + increment
  }

  fun nextInt(): Int {
    val oldState = state
    advance()
    val xorshifted = ((oldState ushr 18) xor oldState) ushr 27
    val rot = (oldState ushr 59).toInt()
    val result = (xorshifted ushr rot) or (xorshifted shl ((-rot) and 31))
    return result.toInt()
  }

  fun nextInt(bound: Int): Int {
    require(bound > 0) { "Bound must be positive" }
    val boundLong = bound.toLong()
    val threshold = ((-boundLong) % boundLong)
    while (true) {
      val r = nextUInt()
      if (r >= threshold) {
        return (r % boundLong).toInt()
      }
    }
  }

  fun nextDouble(): Double {
    val value = nextUInt()
    return (value + 0.5) / 4294967296.0
  }

  fun nextUInt(): Long {
    val oldState = state
    advance()
    val xorshifted = ((oldState ushr 18) xor oldState) ushr 27
    val rot = (oldState ushr 59).toInt()
    val result = (xorshifted ushr rot) or (xorshifted shl ((-rot) and 31))
    return result.toLong() and 0xffffffffL
  }
}
