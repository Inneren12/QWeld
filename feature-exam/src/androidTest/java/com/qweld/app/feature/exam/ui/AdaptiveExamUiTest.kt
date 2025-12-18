package com.qweld.app.feature.exam.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
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
@SdkSuppress(maxSdkVersion = 34)
class AdaptiveExamUiTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun adaptiveExamRunShowsLabelAndFinishes() {
    val adaptiveLabel = composeTestRule.activity.getString(R.string.exam_adaptive_mode_label)

    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        var adaptiveEnabled by remember { mutableStateOf(false) }
        var state by remember { mutableStateOf(ExamUiState(attempt = null, timerLabel = "")) }
        var resultState by remember { mutableStateOf<ResultUiState?>(null) }
        var scoreLabel by remember { mutableStateOf("") }

        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = composeTestRule.activity.getString(R.string.mode_adaptive_beta_label), modifier = Modifier.weight(1f))
            Switch(
              modifier = Modifier.testTag("adaptive-toggle"),
              checked = adaptiveEnabled,
              onCheckedChange = { enabled ->
                adaptiveEnabled = enabled
                state = state.copy(attempt = if (enabled) adaptiveAttempt() else null)
              },
            )
          }

          if (resultState == null && adaptiveEnabled) {
            val attemptState = state.attempt ?: adaptiveAttempt()
            state = state.copy(attempt = attemptState)

            ExamScreenContent(
              state = state,
              onChoiceSelected = { choiceId -> state = state.copy(attempt = state.attempt?.select(choiceId)) },
              onNext = { state = state.copy(attempt = state.attempt?.moveNext()) },
              onPrevious = {},
              onDismissDeficit = {},
              onFinish = {
                val attempt = requireNotNull(state.attempt)
                val correct =
                  attempt.questions.count { question -> question.selectedChoiceId == question.choices.first().id }
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
                    taskSummaries =
                      listOf(
                        TaskSummaryUiModel(taskId = "A-1", blockId = "A", correct = correct, total = total, scorePercent = percent),
                      ),
                    timeLeftLabel = null,
                  )
                scoreLabel = String.format(Locale.US, "%.0f%%", percent)
              },
              onShowRestart = {},
              onShowExit = {},
              onReportQuestionClick = {},
            )
          }

          resultState?.let { result ->
            ResultScreenLayout(
              state = result,
              scoreLabel = scoreLabel,
              onExport = {},
              onReview = {},
              logExportActions = null,
              onExit = {},
            )
          }
        }
      }
    }

    composeTestRule.onNodeWithTag("adaptive-toggle").performClick()

    composeTestRule.onNodeWithText(adaptiveLabel).assertExists()

    composeTestRule.onNodeWithText("Adaptive Q1 Choice A").performClick()
    composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.exam_next)).performClick()
    composeTestRule.onNodeWithText("Adaptive Q2 Choice B").performClick()
    composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.exam_next)).performClick()
    composeTestRule.onNodeWithText("Adaptive Q3 Choice A").performClick()
    composeTestRule.onNodeWithTag("btn-finish").performClick()

    val expectedScore = composeTestRule.activity.getString(R.string.result_score, "67%", 2, 3)
    composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.result_title)).assertExists()
    composeTestRule.onNodeWithText(expectedScore).assertExists()
  }

  private fun adaptiveAttempt(): ExamAttemptUiState {
    val question1Choices =
      listOf(
        ExamChoiceUiModel(id = "aq1-a", label = "A", text = "Adaptive Q1 Choice A", isSelected = false),
        ExamChoiceUiModel(id = "aq1-b", label = "B", text = "Adaptive Q1 Choice B", isSelected = false),
      )
    val question2Choices =
      listOf(
        ExamChoiceUiModel(id = "aq2-a", label = "A", text = "Adaptive Q2 Choice A", isSelected = false),
        ExamChoiceUiModel(id = "aq2-b", label = "B", text = "Adaptive Q2 Choice B", isSelected = false),
      )
    val question3Choices =
      listOf(
        ExamChoiceUiModel(id = "aq3-a", label = "A", text = "Adaptive Q3 Choice A", isSelected = false),
        ExamChoiceUiModel(id = "aq3-b", label = "B", text = "Adaptive Q3 Choice B", isSelected = false),
      )
    val questions =
      listOf(
        ExamQuestionUiModel(id = "aq1", stem = "Adaptive question 1", choices = question1Choices, selectedChoiceId = null),
        ExamQuestionUiModel(id = "aq2", stem = "Adaptive question 2", choices = question2Choices, selectedChoiceId = null),
        ExamQuestionUiModel(id = "aq3", stem = "Adaptive question 3", choices = question3Choices, selectedChoiceId = null),
      )
    return ExamAttemptUiState(
      attemptId = "adaptive-attempt",
      mode = ExamMode.ADAPTIVE,
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
