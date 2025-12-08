package com.qweld.app.feature.exam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.BlockSummaryUiModel
import com.qweld.app.feature.exam.model.ExamAttemptUiState
import com.qweld.app.feature.exam.model.ExamChoiceUiModel
import com.qweld.app.feature.exam.model.ExamQuestionUiModel
import com.qweld.app.feature.exam.model.ExamUiState
import com.qweld.app.feature.exam.model.PassStatus
import com.qweld.app.feature.exam.model.ResultUiState
import com.qweld.app.feature.exam.model.TaskSummaryUiModel
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamSubmitResumeTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun happyPathExamRunShowsResults() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    var state = ExamUiState(attempt = sampleAttempt(), timerLabel = "04:00:00")
    var resultState: ResultUiState? = null
    var scoreLabel = ""

    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        if (resultState == null) {
          ExamScreenContent(
            state = state,
            onChoiceSelected = { choiceId -> state = state.copy(attempt = state.attempt?.select(choiceId)) },
            onNext = { state = state.copy(attempt = state.attempt?.moveNext()) },
            onPrevious = {},
            onDismissDeficit = {},
            onFinish = {
              val attempt = requireNotNull(state.attempt)
              val correct = attempt.questions.count { question ->
                question.selectedChoiceId == question.choices.first().id
              }
              val total = attempt.questions.size
              val percent = if (total == 0) 0.0 else (correct.toDouble() / total.toDouble()) * 100.0
              val passStatus = if (percent >= 70) PassStatus.Passed else PassStatus.Failed
              resultState =
                ResultUiState(
                  mode = attempt.mode,
                  totalCorrect = correct,
                  totalQuestions = total,
                  scorePercent = percent,
                  passStatus = passStatus,
                  blockSummaries = listOf(BlockSummaryUiModel(blockId = "A", correct = correct, total = total, scorePercent = percent)),
                  taskSummaries = listOf(
                    TaskSummaryUiModel(taskId = "A-1", blockId = "A", correct = correct, total = total, scorePercent = percent)
                  ),
                  timeLeftLabel = state.timerLabel,
                )
              scoreLabel = String.format(Locale.US, "%.0f%%", percent)
            },
            onShowRestart = {},
            onShowExit = {},
            onReportQuestionClick = {},
          )
        } else {
          ResultScreenLayout(
            state = requireNotNull(resultState),
            scoreLabel = scoreLabel,
            onExport = {},
            onReview = {},
            logExportActions = null,
            onExit = {},
          )
        }
      }
    }

    val timerLabel = context.getString(R.string.exam_timer_label, "04:00:00")
    composeTestRule.onNodeWithText(timerLabel).assertIsDisplayed()

    composeTestRule.onNodeWithText("Choice 1A").performClick()
    composeTestRule.onNodeWithText(context.getString(R.string.exam_next)).performClick()
    composeTestRule.onNodeWithText("Choice 2B").performClick()
    composeTestRule.onNodeWithTag("btn-finish").performClick()

    val expectedScore = context.getString(R.string.result_score, "50%", 1, 2)
    composeTestRule.onNodeWithText(context.getString(R.string.result_title)).assertIsDisplayed()
    composeTestRule.onNodeWithText(expectedScore).assertIsDisplayed()
  }

  @Test
  fun resumedExamShowsAnsweredStateAndTimer() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    var state = ExamUiState(attempt = answeredAttempt(), timerLabel = "03:30:00")

    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        ExamScreenContent(
          state = state,
          onChoiceSelected = {},
          onNext = {},
          onPrevious = {},
          onDismissDeficit = {},
          onFinish = {},
          onShowRestart = {},
          onShowExit = {},
          onReportQuestionClick = {},
        )
      }
    }

    val selectedDescription = context.getString(R.string.exam_choice_state_selected)
    composeTestRule
      .onNode(hasStateDescription(selectedDescription), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNodeWithText(context.getString(R.string.exam_timer_label, "03:30:00"))
      .assertIsDisplayed()

    composeTestRule.runOnIdle { state = state.copy(timerLabel = "03:29:50") }

    composeTestRule
      .onNodeWithText(context.getString(R.string.exam_timer_label, "03:29:50"))
      .assertIsDisplayed()
  }

  private fun sampleAttempt(): ExamAttemptUiState {
    val firstChoices =
      listOf(
        ExamChoiceUiModel(id = "q1-a", label = "A", text = "Choice 1A", isSelected = false),
        ExamChoiceUiModel(id = "q1-b", label = "B", text = "Choice 1B", isSelected = false),
      )
    val secondChoices =
      listOf(
        ExamChoiceUiModel(id = "q2-a", label = "A", text = "Choice 2A", isSelected = false),
        ExamChoiceUiModel(id = "q2-b", label = "B", text = "Choice 2B", isSelected = false),
      )
    val questions =
      listOf(
        ExamQuestionUiModel(id = "q1", stem = "Question 1", choices = firstChoices, selectedChoiceId = null),
        ExamQuestionUiModel(id = "q2", stem = "Question 2", choices = secondChoices, selectedChoiceId = null),
      )
    return ExamAttemptUiState(
      attemptId = "attempt-1",
      mode = ExamMode.IP_MOCK,
      locale = "en",
      questions = questions,
      currentIndex = 0,
    )
  }

  private fun answeredAttempt(): ExamAttemptUiState {
    val choices =
      listOf(
        ExamChoiceUiModel(id = "q1-a", label = "A", text = "Choice 1A", isSelected = true),
        ExamChoiceUiModel(id = "q1-b", label = "B", text = "Choice 1B", isSelected = false),
      )
    val question = ExamQuestionUiModel(id = "q1", stem = "Question 1", choices = choices, selectedChoiceId = "q1-a")
    return ExamAttemptUiState(
      attemptId = "attempt-resume",
      mode = ExamMode.IP_MOCK,
      locale = "en",
      questions = listOf(question),
      currentIndex = 0,
    )
  }

  private fun ExamAttemptUiState.select(choiceId: String): ExamAttemptUiState {
    val updated =
      questions.mapIndexed { index, question ->
        if (index != currentIndex) return@mapIndexed question
        val updatedChoices = question.choices.map { choice -> choice.copy(isSelected = choice.id == choiceId) }
        question.copy(selectedChoiceId = choiceId, choices = updatedChoices)
      }
    return copy(questions = updated)
  }

  private fun ExamAttemptUiState.moveNext(): ExamAttemptUiState {
    val nextIndex = (currentIndex + 1).coerceAtMost(questions.lastIndex)
    return copy(currentIndex = nextIndex)
  }
}
