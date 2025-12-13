package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.QuestionReportReason

/**
 * Dialog for reporting a question issue.
 * Allows user to select a reason and optionally add a comment.
 *
 * @param onDismiss Callback invoked when dialog is dismissed/cancelled
 * @param onSubmit Callback invoked when user submits the report with selected reason and comment
 */
@Composable
fun ReportQuestionDialog(
  onDismiss: () -> Unit,
  onSubmit: (QuestionReportReason, String?) -> Unit,
) {
  var selectedReason by rememberSaveable { mutableStateOf(QuestionReportReason.OTHER) }
  var comment by rememberSaveable { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(id = R.string.report_question_title)) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = stringResource(id = R.string.report_question_description),
          style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = stringResource(id = R.string.report_question_reason_label),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Reason options
        ReasonRow(
          text = stringResource(id = R.string.report_reason_wrong_answer),
          selected = selectedReason == QuestionReportReason.WRONG_ANSWER,
          onSelect = { selectedReason = QuestionReportReason.WRONG_ANSWER },
        )
        ReasonRow(
          text = stringResource(id = R.string.report_reason_outdated_content),
          selected = selectedReason == QuestionReportReason.OUTDATED_CONTENT,
          onSelect = { selectedReason = QuestionReportReason.OUTDATED_CONTENT },
        )
        ReasonRow(
          text = stringResource(id = R.string.report_reason_bad_wording),
          selected = selectedReason == QuestionReportReason.BAD_WORDING,
          onSelect = { selectedReason = QuestionReportReason.BAD_WORDING },
        )
        ReasonRow(
          text = stringResource(id = R.string.report_reason_typo),
          selected = selectedReason == QuestionReportReason.TYPO,
          onSelect = { selectedReason = QuestionReportReason.TYPO },
        )
        ReasonRow(
          text = stringResource(id = R.string.report_reason_translation_issue),
          selected = selectedReason == QuestionReportReason.TRANSLATION_ISSUE,
          onSelect = { selectedReason = QuestionReportReason.TRANSLATION_ISSUE },
        )
        ReasonRow(
          text = stringResource(id = R.string.report_reason_ui_issue),
          selected = selectedReason == QuestionReportReason.UI_ISSUE,
          onSelect = { selectedReason = QuestionReportReason.UI_ISSUE },
        )
        ReasonRow(
          text = stringResource(id = R.string.report_reason_other),
          selected = selectedReason == QuestionReportReason.OTHER,
          onSelect = { selectedReason = QuestionReportReason.OTHER },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Comment field
        Text(
          text = stringResource(id = R.string.report_question_comment_label),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
          value = comment,
          onValueChange = { comment = it },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text(stringResource(id = R.string.report_question_comment_hint)) },
          minLines = 3,
          maxLines = 5,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onSubmit(selectedReason, comment.ifBlank { null })
      }) {
        Text(text = stringResource(id = R.string.report_question_submit))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(id = R.string.action_cancel))
      }
    },
  )
}

/**
 * Helper composable for a single reason option row with radio button.
 */
@Composable
private fun ReasonRow(
  text: String,
  selected: Boolean,
  onSelect: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
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
