package com.qweld.app.feature.exam.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.PassStatus
import com.qweld.app.feature.exam.model.ResultUiState
import com.qweld.app.feature.exam.vm.ResultViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun ResultScreen(
  viewModel: ResultViewModel,
  onReview: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState
  val onExport = rememberAttemptExportLauncher(viewModel)
  ResultScreenContent(
    state = uiState,
    scoreLabel = viewModel.scoreLabel(),
    onExport = onExport,
    onReview = onReview,
    modifier = modifier,
  )
}

@Composable
private fun ResultScreenContent(
  state: ResultUiState,
  scoreLabel: String,
  onExport: () -> Unit,
  onReview: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Text(
        text = stringResource(id = R.string.result_title),
        style = MaterialTheme.typography.headlineSmall,
      )
    }
    item {
      Text(
        text = stringResource(id = R.string.result_score, scoreLabel, state.totalCorrect, state.totalQuestions),
        style = MaterialTheme.typography.titleMedium,
      )
    }
    if (state.passStatus != null) {
      item {
        val statusText = when (state.passStatus) {
          PassStatus.Passed -> stringResource(id = R.string.result_passed)
          PassStatus.Failed -> stringResource(id = R.string.result_failed)
        }
        val statusColor = when (state.passStatus) {
          PassStatus.Passed -> MaterialTheme.colorScheme.primary
          PassStatus.Failed -> MaterialTheme.colorScheme.error
        }
        Text(
          text = stringResource(id = R.string.result_status, statusText),
          style = MaterialTheme.typography.titleMedium,
          color = statusColor,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
    state.timeLeftLabel?.let { timeLeft ->
      item {
        Text(
          text = stringResource(id = R.string.result_time_left, timeLeft),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    if (state.blockSummaries.isNotEmpty()) {
      item {
        Text(
          text = stringResource(id = R.string.result_blocks_title),
          style = MaterialTheme.typography.titleMedium,
        )
      }
      items(state.blockSummaries) { summary ->
        SummaryCard(
          title = stringResource(
            id = R.string.result_block_item,
            summary.blockId,
            summary.correct,
            summary.total,
            summary.scorePercent,
          ),
        )
      }
    }
    if (state.taskSummaries.isNotEmpty()) {
      item {
        Text(
          text = stringResource(id = R.string.result_tasks_title),
          style = MaterialTheme.typography.titleMedium,
        )
      }
      items(state.taskSummaries) { summary ->
        SummaryCard(
          title = stringResource(
            id = R.string.result_task_item,
            summary.taskId,
            summary.blockId,
            summary.correct,
            summary.total,
            summary.scorePercent,
          ),
        )
      }
    }
    item {
      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onExport,
      ) {
        Text(text = stringResource(id = R.string.result_export_json))
      }
    }
    item {
      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onReview,
      ) {
        Text(text = stringResource(id = R.string.result_review))
      }
    }
  }
}

@Composable
internal fun rememberAttemptExportLauncher(viewModel: ResultViewModel): () -> Unit {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val fileName = remember(viewModel) { viewModel.exportFileName() }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        try {
          val json = viewModel.exportAttemptJson()
          withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri)
              ?: error("Unable to open output stream for $uri")
            stream.use { output ->
              output.writer(Charsets.UTF_8).use { writer -> writer.write(json) }
            }
          }
          val pctLabel = String.format(Locale.US, "%.2f", viewModel.attemptScorePercent())
          Timber.i(
            "[export_attempt] id=%s items=%d pct=%s fileName=%s uri=%s",
            viewModel.attemptId(),
            viewModel.attemptQuestionCount(),
            pctLabel,
            fileName,
            uri,
          )
        } catch (t: Throwable) {
          Timber.e(
            t,
            "[export_attempt_error] id=%s reason=%s",
            viewModel.attemptId(),
            t.message ?: t::class.java.simpleName,
          )
        }
      }
    }
  return remember(fileName, launcher) { { launcher.launch(fileName) } }
}

@Composable
private fun SummaryCard(
  title: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
      )
    }
  }
}
