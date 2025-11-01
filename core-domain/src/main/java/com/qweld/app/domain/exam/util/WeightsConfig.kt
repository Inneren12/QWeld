package com.qweld.app.domain.exam.util

/**
 * Configuration for weight adjustments applied during question sampling.
 */
data class WeightsConfig(
  val wrongBoost: Double = 1.5,
)
