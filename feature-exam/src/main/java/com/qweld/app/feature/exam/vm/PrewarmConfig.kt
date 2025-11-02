package com.qweld.app.feature.exam.vm

data class PrewarmConfig(
  val enabled: Boolean = true,
  val maxConcurrency: Int = 3,
  val taskTimeoutMs: Long = 2_000L,
) {
  val sanitizedMaxConcurrency: Int get() = maxConcurrency.coerceAtLeast(1)
  val sanitizedTimeoutMs: Long get() = taskTimeoutMs.coerceAtLeast(1L)
}
