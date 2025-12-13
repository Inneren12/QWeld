package com.qweld.app.feature.exam.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.ExamAttemptUiState
import com.qweld.app.feature.exam.model.ExamChoiceUiModel
import com.qweld.app.feature.exam.model.ExamQuestionUiModel
import com.qweld.app.feature.exam.model.ExamUiState
import com.qweld.app.feature.exam.model.QuestionReportReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 34)
class QuestionReportUiTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun reportQuestionDialog_submitsWithUserComment() {
    val attempt = examAttempt()
    var submittedReason: QuestionReportReason? = null
    var submittedComment: String? = null

    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        var state by remember { mutableStateOf(ExamUiState(attempt = attempt, timerLabel = null)) }
        var showReport by remember { mutableStateOf(false) }
        var acknowledgment by remember { mutableStateOf<String?>(null) }

        ExamScreenContent(
          state = state,
          onChoiceSelected = { choiceId ->
            state = state.copy(attempt = state.attempt?.select(choiceId))
          },
          onNext = { state = state.copy(attempt = state.attempt?.moveNext()) },
          onPrevious = {},
          onDismissDeficit = {},
          onFinish = {},
          onShowRestart = {},
          onShowExit = {},
          onReportQuestionClick = { showReport = true },
        )

        if (showReport) {
          ReportQuestionDialog(
            onDismiss = { showReport = false },
            onSubmit = { reason, comment ->
              submittedReason = reason
              submittedComment = comment
              acknowledgment = "Report submitted"
              showReport = false
            },
          )
        }

        acknowledgment?.let {
          androidx.compose.material3.Text(text = it)
        }
      }
    }

    composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.report_question_button))
      .performClick()

    composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.report_reason_wrong_answer))
      .performClick()
    composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.report_question_comment_hint))
      .performTextInput("Test report from UI")
    composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.report_question_submit))
      .performClick()

    assertNotNull("Expected report submission to be captured", submittedReason)
    assertEquals(QuestionReportReason.WRONG_ANSWER, submittedReason)
    assertEquals("Test report from UI", submittedComment)
    composeTestRule.onNodeWithText("Report submitted").assertExists()
  }

  private fun examAttempt(): ExamAttemptUiState {
    val choices =
      listOf(
        ExamChoiceUiModel(id = "q1-a", label = "A", text = "Choice A", isSelected = false),
        ExamChoiceUiModel(id = "q1-b", label = "B", text = "Choice B", isSelected = false),
      )
    val questions = listOf(ExamQuestionUiModel(id = "q1", stem = "Question 1", choices = choices, selectedChoiceId = null))
    return ExamAttemptUiState(
      attemptId = "report-attempt",
      mode = ExamMode.IP_MOCK,
      locale = "en",
      questions = questions,
      currentIndex = 0,
    )
  }

  private fun ExamAttemptUiState.select(choiceId: String): ExamAttemptUiState {
    val updatedQuestions =
      questions.mapIndexed { index, question ->
        if (index != currentIndex) return@mapIndexed question
        val updatedChoices = question.choices.map { choice -> choice.copy(isSelected = choice.id == choiceId) }
        question.copy(selectedChoiceId = choiceId, choices = updatedChoices)
      }
    return copy(questions = updatedQuestions)
  }

  private fun ExamAttemptUiState.moveNext(): ExamAttemptUiState {
    val nextIndex = (currentIndex + 1).coerceAtMost(questions.lastIndex)
    return copy(currentIndex = nextIndex)
  }
}
