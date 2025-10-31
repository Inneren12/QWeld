package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.data.AssetExplanationRepository

@Composable
fun ExplainSheet(
  questionStem: String,
  explanation: AssetExplanationRepository.Explanation?,
  rationale: String?,
  isLoading: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = stringResource(id = R.string.review_explain),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = questionStem,
      style = MaterialTheme.typography.titleMedium,
    )
    if (isLoading) {
      Spacer(modifier = Modifier.height(12.dp))
      CircularProgressIndicator()
      return@Column
    }
    if (explanation != null) {
      explanation.summary?.let { summary ->
        ExplainSection(title = stringResource(id = R.string.explain_summary)) {
          Text(text = summary, style = MaterialTheme.typography.bodyMedium)
        }
      }
      if (explanation.steps.isNotEmpty()) {
        ExplainSection(title = stringResource(id = R.string.explain_steps)) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            explanation.steps.forEach { step ->
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                step.title?.let { title ->
                  Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                step.text?.let { text ->
                  Text(text = text, style = MaterialTheme.typography.bodyMedium)
                }
              }
            }
          }
        }
      }
      if (explanation.whyNot.isNotEmpty()) {
        ExplainSection(title = stringResource(id = R.string.explain_why_not)) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            explanation.whyNot.forEach { reason ->
              val label = reason.choiceId?.let { choiceId ->
                stringResource(id = R.string.explain_why_not_item, choiceId)
              }
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                label?.let { Text(text = it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                reason.text?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
              }
            }
          }
        }
      }
      if (explanation.tips.isNotEmpty()) {
        ExplainSection(title = stringResource(id = R.string.explain_tips)) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            explanation.tips.forEach { tip ->
              Text(text = "• $tip", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
      rationale?.let { quick ->
        ExplainSection(title = stringResource(id = R.string.explain_rationale_label)) {
          Text(text = quick, style = MaterialTheme.typography.bodyMedium)
        }
      }
    } else if (!rationale.isNullOrBlank()) {
      ExplainSection(title = stringResource(id = R.string.explain_rationale_label)) {
        Text(text = rationale, style = MaterialTheme.typography.bodyMedium)
      }
    } else {
      Text(text = stringResource(id = R.string.explain_not_available), style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun ExplainSection(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    content()
  }
}
