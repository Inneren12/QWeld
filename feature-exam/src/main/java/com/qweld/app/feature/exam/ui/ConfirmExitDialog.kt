package com.qweld.app.feature.exam.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qweld.app.feature.exam.R

@Composable
fun ConfirmExitDialog(
  onContinue: () -> Unit,
  onExit: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onContinue,
    title = { Text(text = stringResource(id = R.string.confirm_exit_title)) },
    text = { Text(text = stringResource(id = R.string.confirm_exit_message)) },
    dismissButton = {
      TextButton(onClick = onContinue) {
        Text(text = stringResource(id = R.string.confirm_exit_continue))
      }
    },
    confirmButton = {
      TextButton(onClick = onExit) {
        Text(text = stringResource(id = R.string.confirm_exit_exit))
      }
    },
  )
}
