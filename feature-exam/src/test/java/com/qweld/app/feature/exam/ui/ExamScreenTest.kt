package com.qweld.app.feature.exam.ui

import com.qweld.app.domain.exam.ExamMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExamScreenTest {
  @Test
  fun confirmExitRequiredForIpMock() {
    assertTrue(shouldConfirmExit(ExamMode.IP_MOCK))
  }

  @Test
  fun confirmExitNotRequiredForPractice() {
    assertFalse(shouldConfirmExit(ExamMode.PRACTICE))
  }

  @Test
  fun confirmExitNotRequiredWhenModeUnknown() {
    assertFalse(shouldConfirmExit(null))
  }
}
