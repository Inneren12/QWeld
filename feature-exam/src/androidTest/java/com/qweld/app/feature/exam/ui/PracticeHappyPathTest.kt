package com.qweld.app.feature.exam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule

@RunWith(AndroidJUnit4::class)
class PracticeHappyPathTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun practiceRunCompletesAndShowsSummary() {
    val context = composeTestRule.activity
    val attempt = practiceAttempt()

    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        var state by remember { mutableStateOf(ExamUiState(attempt = attempt, timerLabel = null)) }
        var resultState by remember { mutableStateOf<ResultUiState?>(null) }
        var scoreLabel by remember { mutableStateOf("") }

        if (resultState == null) {
          ExamScreenContent(
            state = state,
            onChoiceSelected = { choiceId -> state = state.copy(attempt = state.attempt?.select(choiceId)) },
            onNext = { state = state.copy(attempt = state.attempt?.moveNext()) },
            onPrevious = {},
            onDismissDeficit = {},
            onFinish = {
              val attemptState = requireNotNull(state.attempt)
              val correct = attemptState.questions.count { question ->
                question.selectedChoiceId == question.choices.first().id
              }
              val total = attemptState.questions.size
              val percent = if (total == 0) 0.0 else (correct.toDouble() / total.toDouble()) * 100.0
              val passStatus = if (percent >= 70) PassStatus.Passed else PassStatus.Failed
              resultState =
                ResultUiState(
                  mode = attemptState.mode,
                  totalCorrect = correct,
                  totalQuestions = total,
                  scorePercent = percent,
                  passStatus = passStatus,
                  blockSummaries = listOf(
                    BlockSummaryUiModel(blockId = "A", correct = correct, total = total, scorePercent = percent),
                  ),
                  taskSummaries = listOf(
                    TaskSummaryUiModel(taskId = "A-1", blockId = "A", correct = correct, total = total, scorePercent = percent)
                  ),
                  timeLeftLabel = null,
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

    composeTestRule.onNodeWithText("Practice choice 1A").performClick()
    composeTestRule.onNodeWithText(context.getString(R.string.exam_next)).performClick()
    composeTestRule.onNodeWithText("Practice choice 2A").performClick()
    composeTestRule.onNodeWithTag("btn-finish").performClick()

    val expectedScore = context.getString(R.string.result_score, "100%", 2, 2)

    composeTestRule.onNodeWithText(context.getString(R.string.result_title)).assertExists()
    composeTestRule.onNodeWithText(expectedScore).assertExists()
  }

  private fun practiceAttempt(): ExamAttemptUiState {
    val firstChoices =
      listOf(
        ExamChoiceUiModel(id = "p1-a", label = "A", text = "Practice choice 1A", isSelected = false),
        ExamChoiceUiModel(id = "p1-b", label = "B", text = "Practice choice 1B", isSelected = false),
      )
    val secondChoices =
      listOf(
        ExamChoiceUiModel(id = "p2-a", label = "A", text = "Practice choice 2A", isSelected = false),
        ExamChoiceUiModel(id = "p2-b", label = "B", text = "Practice choice 2B", isSelected = false),
      )
    val questions =
      listOf(
        ExamQuestionUiModel(id = "p1", stem = "Practice question 1", choices = firstChoices, selectedChoiceId = null),
        ExamQuestionUiModel(id = "p2", stem = "Practice question 2", choices = secondChoices, selectedChoiceId = null),
      )
    return ExamAttemptUiState(
      attemptId = "practice-attempt",
      mode = ExamMode.PRACTICE,
      locale = "en",
      questions = questions,
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
