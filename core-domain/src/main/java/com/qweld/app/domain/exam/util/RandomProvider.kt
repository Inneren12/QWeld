package com.qweld.app.domain.exam.util

fun interface RandomProvider {
  fun pcg32(seed: Long): Pcg32
}

object DefaultRandomProvider : RandomProvider {
  override fun pcg32(seed: Long): Pcg32 = Pcg32(seed)
}
