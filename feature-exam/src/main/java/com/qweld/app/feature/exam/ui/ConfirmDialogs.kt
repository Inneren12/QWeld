package com.qweld.app.feature.exam.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.R

@Composable
fun ConfirmRestartDialog(
  onCancel: () -> Unit,
  onRestart: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(text = stringResource(id = R.string.dialog_restart_title)) },
    text = { Text(text = stringResource(id = R.string.dialog_restart_msg)) },
    dismissButton = {
      TextButton(onClick = onCancel) { Text(text = stringResource(id = R.string.action_cancel)) }
    },
    confirmButton = {
      TextButton(onClick = onRestart) { Text(text = stringResource(id = R.string.action_restart)) }
    },
  )
}

@Composable
fun ConfirmExitDialog(
  mode: ExamMode?,
  onCancel: () -> Unit,
  onExit: () -> Unit,
) {
  val (titleRes, messageRes) =
    when (mode) {
      ExamMode.PRACTICE -> R.string.dialog_exit_practice_title to R.string.dialog_exit_practice_msg
      else -> R.string.dialog_exit_exam_title to R.string.dialog_exit_exam_msg
    }
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(text = stringResource(id = titleRes)) },
    text = { Text(text = stringResource(id = messageRes)) },
    dismissButton = {
      TextButton(onClick = onCancel) { Text(text = stringResource(id = R.string.action_cancel)) }
    },
    confirmButton = {
      TextButton(onClick = onExit) { Text(text = stringResource(id = R.string.exit)) }
    },
  )
}
