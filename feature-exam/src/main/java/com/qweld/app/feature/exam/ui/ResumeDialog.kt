package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.ResumeDialogUiModel
import com.qweld.app.feature.exam.model.ResumeLocaleOption
import java.util.Locale

@Composable
fun ResumeDialog(
  state: ResumeDialogUiModel,
  onContinue: (ResumeLocaleOption) -> Unit,
  onDiscard: () -> Unit,
) {
  var selectedOption by rememberSaveable(state.attemptId, state.showLocaleMismatch) {
    mutableStateOf(ResumeLocaleOption.KEEP_ORIGINAL)
  }

  val attemptLocaleDisplay = state.attemptLocale.uppercase(Locale.US)
  val deviceLocaleDisplay = state.deviceLocale.uppercase(Locale.US)
  val modeLabel = when (state.mode) {
    ExamMode.IP_MOCK -> stringResource(id = R.string.mode_ip_mock)
    ExamMode.PRACTICE -> stringResource(id = R.string.mode_practice)
    ExamMode.ADAPTIVE -> state.mode.name
  }
  val message = stringResource(
    id = R.string.resume_dialog_message,
    modeLabel,
    state.questionCount,
  )

  AlertDialog(
    onDismissRequest = {},
    title = { Text(text = stringResource(id = R.string.resume_dialog_title)) },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = message)
        if (state.remaining != null) {
          Spacer(modifier = Modifier.height(12.dp))
          val remainingLabel = TimerController.formatDuration(state.remaining)
          Text(
            text = stringResource(id = R.string.resume_dialog_remaining, remainingLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        if (state.showLocaleMismatch) {
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = stringResource(id = R.string.resume_dialog_locale_changed, deviceLocaleDisplay),
            style = MaterialTheme.typography.bodyMedium,
          )
          Spacer(modifier = Modifier.height(8.dp))
          ResumeLocaleRow(
            text = stringResource(id = R.string.resume_dialog_keep, attemptLocaleDisplay),
            selected = selectedOption == ResumeLocaleOption.KEEP_ORIGINAL,
            onSelect = { selectedOption = ResumeLocaleOption.KEEP_ORIGINAL },
          )
          ResumeLocaleRow(
            text = stringResource(id = R.string.resume_dialog_switch, deviceLocaleDisplay),
            selected = selectedOption == ResumeLocaleOption.SWITCH_TO_DEVICE,
            onSelect = { selectedOption = ResumeLocaleOption.SWITCH_TO_DEVICE },
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onContinue(selectedOption) }) {
        Text(text = stringResource(id = R.string.resume_dialog_continue))
      }
    },
    dismissButton = {
      TextButton(onClick = onDiscard) {
        Text(text = stringResource(id = R.string.resume_dialog_discard))
      }
    },
  )
}

@Composable
private fun ResumeLocaleRow(
  text: String,
  selected: Boolean,
  onSelect: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
  ) {
    RadioButton(selected = selected, onClick = onSelect)
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier
        .align(Alignment.CenterVertically)
        .padding(start = 4.dp),
    )
  }
}
