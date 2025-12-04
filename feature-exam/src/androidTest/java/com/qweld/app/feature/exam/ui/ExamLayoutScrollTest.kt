package com.qweld.app.feature.exam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.model.ExamAttemptUiState
import com.qweld.app.feature.exam.model.ExamChoiceUiModel
import com.qweld.app.feature.exam.model.ExamQuestionUiModel
import com.qweld.app.feature.exam.model.ExamUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamLayoutScrollTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun examScreenScrollableFinishButtonVisible() {
    setContentFor(mode = ExamMode.IP_MOCK)

    assertScrollableAndFinishAccessible()
  }

  @Test
  fun practiceScreenScrollableFinishButtonVisible() {
    setContentFor(mode = ExamMode.PRACTICE)

    assertScrollableAndFinishAccessible()
  }

  private fun assertScrollableAndFinishAccessible() {
    composeTestRule.onNodeWithTag("exam-content").assert(hasScrollAction())
    composeTestRule.onNodeWithTag("btn-finish").assertIsDisplayed().performClick()
  }

  private fun setContentFor(mode: ExamMode) {
    val attempt = createAttempt(mode)
    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        ExamScreenContent(
          state = ExamUiState(attempt = attempt),
          onChoiceSelected = {},
          onNext = {},
          onPrevious = {},
          onDismissDeficit = {},
          onFinish = {},
          onShowRestart = {},
          onShowExit = {},
        )
      }
    }
  }

  private fun createAttempt(mode: ExamMode): ExamAttemptUiState {
    val longStem = (1..40).joinToString(separator = " ") { index ->
      "Long question text line $index."
    }
    val choices = listOf("A", "B", "C", "D").mapIndexed { index, label ->
      ExamChoiceUiModel(
        id = "choice-$index",
        label = label,
        text = "Option $label",
        isSelected = false,
      )
    }
    val question = ExamQuestionUiModel(
      id = "question-1",
      stem = longStem,
      choices = choices,
      selectedChoiceId = null,
    )
    return ExamAttemptUiState(
      attemptId = "attempt-${mode.name.lowercase()}",
      mode = mode,
      locale = "en",
      questions = listOf(question),
      currentIndex = 0,
    )
  }
}
