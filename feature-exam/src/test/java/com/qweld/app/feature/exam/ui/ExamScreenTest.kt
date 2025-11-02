package com.qweld.app.feature.exam.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.qweld.app.domain.exam.ExamMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamScreenTest {
  @Test
  fun confirmExitRequiredForIpMock() {
    assertTrue(shouldConfirmExit(ExamMode.IP_MOCK))
  }

  @Test
  fun confirmExitRequiredForPractice() {
    assertTrue(shouldConfirmExit(ExamMode.PRACTICE))
  }

  @Test
  fun confirmExitNotRequiredWhenModeUnknown() {
    assertFalse(shouldConfirmExit(null))
  }

  @Test
  fun performSubmitFeedbackRespectsFlags() {
    val haptics = FakeHapticFeedback()
    var soundCount = 0

    performSubmitFeedback(
      hapticsEnabled = true,
      soundsEnabled = true,
      hapticFeedback = haptics,
      onPlaySound = { soundCount++ },
    )

    assertEquals(HapticFeedbackType.LongPress, haptics.lastType)
    assertEquals(1, soundCount)

    haptics.reset()

    performSubmitFeedback(
      hapticsEnabled = false,
      soundsEnabled = false,
      hapticFeedback = haptics,
      onPlaySound = { soundCount++ },
    )

    assertNull(haptics.lastType)
    assertEquals(1, soundCount)
  }

  private class FakeHapticFeedback : HapticFeedback {
    var lastType: HapticFeedbackType? = null

    override fun performHapticFeedback(feedbackType: HapticFeedbackType) {
      lastType = feedbackType
    }

    fun reset() {
      lastType = null
    }
  }
}
