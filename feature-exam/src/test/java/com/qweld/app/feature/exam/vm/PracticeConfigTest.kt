package com.qweld.app.feature.exam.vm

import kotlin.test.Test
import kotlin.test.assertEquals

class PracticeConfigTest {

  @Test
  fun sanitizeSize_clampsToMinimum() {
    val sanitized = PracticeConfig.sanitizeSize(PracticeConfig.MIN_SIZE - 10)

    assertEquals(PracticeConfig.MIN_SIZE, sanitized)
  }

  @Test
  fun sanitizeSize_clampsToMaximum() {
    val sanitized = PracticeConfig.sanitizeSize(PracticeConfig.MAX_SIZE + 25)

    assertEquals(PracticeConfig.MAX_SIZE, sanitized)
  }

  @Test
  fun sanitizeSize_keepsValidValue() {
    val target = (PracticeConfig.MIN_SIZE + PracticeConfig.MAX_SIZE) / 2

    val sanitized = PracticeConfig.sanitizeSize(target)

    assertEquals(target, sanitized)
  }
}
