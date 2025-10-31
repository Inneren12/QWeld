package com.qweld.app.feature.exam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.DeficitDialogUiModel
import com.qweld.app.feature.exam.model.ExamUiState
import com.qweld.app.feature.exam.vm.ExamViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ExamScreen(
  viewModel: ExamViewModel,
  onNavigateToResult: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState
  LaunchedEffect(viewModel) {
    viewModel.events.collectLatest { event ->
      when (event) {
        ExamViewModel.ExamEvent.NavigateToResult -> onNavigateToResult()
      }
    }
  }
  ExamScreenContent(
    state = uiState,
    modifier = modifier,
    onChoiceSelected = viewModel::submitAnswer,
    onNext = viewModel::nextQuestion,
    onPrevious = viewModel::previousQuestion,
    onDismissDeficit = viewModel::dismissDeficitDialog,
    onFinish = viewModel::finishExam,
  )
}

@Composable
private fun ExamScreenContent(
  state: ExamUiState,
  modifier: Modifier = Modifier,
  onChoiceSelected: (String) -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onDismissDeficit: () -> Unit,
  onFinish: () -> Unit,
) {
  Scaffold { paddingValues ->
    val attempt = state.attempt
    if (attempt == null) {
      Box(
        modifier = modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(id = R.string.exam_no_attempt),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
        )
      }
    } else {
      BackHandler(enabled = attempt.mode == ExamMode.IP_MOCK) { }
      val question = attempt.currentQuestion()
      Column(
        modifier = modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        if (attempt.mode == ExamMode.IP_MOCK && state.timerLabel != null) {
          Text(
            text = stringResource(id = R.string.exam_timer_label, state.timerLabel),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Text(
          text = stringResource(
            id = R.string.exam_question_counter,
            attempt.currentIndex + 1,
            attempt.totalQuestions,
          ),
          style = MaterialTheme.typography.labelLarge,
        )
        if (question == null) {
          Text(
            text = stringResource(id = R.string.exam_no_question),
            style = MaterialTheme.typography.bodyLarge,
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
              text = question.stem,
              style = MaterialTheme.typography.headlineSmall,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              question.choices.forEach { choice ->
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  onClick = { onChoiceSelected(choice.id) },
                  enabled = !question.isAnswered,
                  shape = RoundedCornerShape(12.dp),
                  border = if (choice.isSelected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                  } else {
                    null
                  },
                  colors = CardDefaults.cardColors(
                    containerColor =
                      if (choice.isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                      } else {
                        MaterialTheme.colorScheme.surface
                      },
                  ),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                  ) {
                    Text(
                      text = choice.label,
                      style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                      text = choice.text,
                      style = MaterialTheme.typography.bodyLarge,
                    )
                  }
                }
              }
            }
          }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Button(
            modifier = Modifier.weight(1f),
            onClick = onPrevious,
            enabled = attempt.canGoPrevious(),
          ) {
            Text(text = stringResource(id = R.string.exam_previous))
          }
          Button(
            modifier = Modifier.weight(1f),
            onClick = onNext,
            enabled = attempt.canGoNext(),
          ) {
            Text(text = stringResource(id = R.string.exam_next))
          }
        }
        Button(
          modifier = Modifier.fillMaxWidth(),
          onClick = onFinish,
        ) {
          Text(text = stringResource(id = R.string.exam_finish))
        }
      }
    }
  }

  state.deficitDialog?.let { dialog ->
    DeficitDialog(dialog = dialog, onDismiss = onDismissDeficit)
  }
}

@Composable
private fun DeficitDialog(
  dialog: DeficitDialogUiModel,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(id = R.string.exam_deficit_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        dialog.details.forEach { detail ->
          Text(
            text = stringResource(
              id = R.string.exam_deficit_message,
              detail.taskId,
              detail.need,
              detail.have,
              detail.missing,
            ),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(id = R.string.exam_deficit_confirm))
      }
    },
  )
}
