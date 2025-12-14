package com.qweld.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.qweld.app.R
import com.qweld.app.error.APP_ERROR_COMMENT_MAX_LENGTH

@Composable
fun AppErrorReportDialog(
  onDismiss: () -> Unit,
  onSubmit: (String) -> Unit,
  isSubmitting: Boolean,
) {
  var comment by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
  val trimmedComment = remember(comment) { comment.text.take(APP_ERROR_COMMENT_MAX_LENGTH) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(id = R.string.app_error_report_title)) },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = stringResource(id = R.string.app_error_report_message),
          style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(id = R.string.app_error_report_privacy),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
          value = comment,
          onValueChange = { newValue ->
            comment =
              if (newValue.text.length <= APP_ERROR_COMMENT_MAX_LENGTH) newValue
              else newValue.copy(text = newValue.text.take(APP_ERROR_COMMENT_MAX_LENGTH))
          },
          label = { Text(text = stringResource(id = R.string.app_error_report_comment_label)) },
          supportingText = {
            Text(text = stringResource(id = R.string.app_error_report_comment_supporting))
          },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSubmit(trimmedComment) },
        enabled = !isSubmitting,
      ) {
        Text(text = stringResource(id = R.string.app_error_report_submit))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isSubmitting) {
        Text(text = stringResource(id = android.R.string.cancel))
      }
    },
  )
}
