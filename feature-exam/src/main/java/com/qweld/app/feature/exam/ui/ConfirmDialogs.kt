package com.qweld.app.feature.exam.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
  onCancel: () -> Unit,
  onExit: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(text = stringResource(id = R.string.dialog_exit_title)) },
    text = { Text(text = stringResource(id = R.string.dialog_exit_msg)) },
    dismissButton = {
      TextButton(onClick = onCancel) { Text(text = stringResource(id = R.string.action_cancel)) }
    },
    confirmButton = {
      TextButton(onClick = onExit) { Text(text = stringResource(id = R.string.exit)) }
    },
  )
}
