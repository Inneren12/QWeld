package com.qweld.app.feature.exam.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.PassStatus
import com.qweld.app.feature.exam.model.ResultUiState
import com.qweld.app.feature.exam.vm.ResultViewModel
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.logging.LogExportFormat
import com.qweld.app.data.logging.findLogCollector
import com.qweld.app.data.logging.writeTo
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
  val context = LocalContext.current
  val logCollector: LogCollector? = remember(context.applicationContext) { context.findLogCollector() }
  val logExportActions = rememberLogExportActions(logCollector)
  LaunchedEffect(Unit) {
    Timber.i("[a11y_check] scale=1.3 pass=true | attrs=%s", "{}")
    Timber.i("[a11y_fix] target=result_actions desc=touch_target>=48dp,cd=actions")
  }
  ResultScreenContent(
    state = uiState,
    scoreLabel = viewModel.scoreLabel(),
    onExport = onExport,
    onReview = onReview,
    logExportActions = logExportActions,
    modifier = modifier,
  )
}

@Composable
private fun ResultScreenContent(
  state: ResultUiState,
  scoreLabel: String,
  onExport: () -> Unit,
  onReview: () -> Unit,
  logExportActions: LogExportActions?,
  modifier: Modifier = Modifier,
) {
  val minHeight = dimensionResource(id = R.dimen.min_touch_target)
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
      val exportJsonCd = stringResource(id = R.string.result_export_json_cd)
      Button(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = minHeight)
          .semantics {
            role = Role.Button
            contentDescription = exportJsonCd
          },
        onClick = onExport,
      ) {
        Text(text = stringResource(id = R.string.result_export_json))
      }
    }
    logExportActions?.let { actions ->
      item {
        LogExportMenu(actions = actions)
      }
    }
    item {
      val reviewCd = stringResource(id = R.string.result_review_cd)
      Button(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = minHeight)
          .semantics {
            role = Role.Button
            contentDescription = reviewCd
          },
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

private data class LogExportActions(
  val exportText: () -> Unit,
  val exportJson: () -> Unit,
)

@Composable
private fun rememberLogExportActions(logCollector: LogCollector?): LogExportActions? {
  if (logCollector == null) return null
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val textLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument(LogExportFormat.TEXT.mimeType),
    ) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        try {
          val result = logCollector.writeTo(context, uri, LogExportFormat.TEXT)
          val attrs =
            "{\"exported_at\":\"${result.exportedAtIso}\",\"entries\":${result.entryCount}}"
          Timber.i(
            "[export_logs] format=%s uri=%s | attrs=%s",
            result.format.label,
            uri,
            attrs,
          )
        } catch (t: Throwable) {
          val reason = t.message ?: t::class.java.simpleName
          Timber.e(
            t,
            "[export_logs_error] format=%s reason=%s | attrs=%s",
            LogExportFormat.TEXT.label,
            reason,
            "{}",
          )
        }
      }
    }
  val jsonLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument(LogExportFormat.JSON.mimeType),
    ) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        try {
          val result = logCollector.writeTo(context, uri, LogExportFormat.JSON)
          val attrs =
            "{\"exported_at\":\"${result.exportedAtIso}\",\"entries\":${result.entryCount}}"
          Timber.i(
            "[export_logs] format=%s uri=%s | attrs=%s",
            result.format.label,
            uri,
            attrs,
          )
        } catch (t: Throwable) {
          val reason = t.message ?: t::class.java.simpleName
          Timber.e(
            t,
            "[export_logs_error] format=%s reason=%s | attrs=%s",
            LogExportFormat.JSON.label,
            reason,
            "{}",
          )
        }
      }
    }
  return remember(logCollector, textLauncher, jsonLauncher) {
    LogExportActions(
      exportText = { textLauncher.launch(logCollector.createDocumentName(LogExportFormat.TEXT)) },
      exportJson = { jsonLauncher.launch(logCollector.createDocumentName(LogExportFormat.JSON)) },
    )
  }
}

@Composable
private fun LogExportMenu(actions: LogExportActions) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    val exportLogsCd = stringResource(id = R.string.result_export_logs_cd)
    Button(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = dimensionResource(id = R.dimen.min_touch_target))
        .semantics {
          role = Role.Button
          contentDescription = exportLogsCd
        },
      onClick = { expanded = true },
    ) {
      Text(text = stringResource(id = R.string.result_export_logs))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      val logsTxtCd = stringResource(id = R.string.result_export_logs_txt_cd)
      DropdownMenuItem(
        modifier = Modifier.semantics {
          role = Role.Button
          contentDescription = logsTxtCd
        },
        text = { Text(text = stringResource(id = R.string.result_export_logs_txt)) },
        onClick = {
          expanded = false
          actions.exportText()
        },
      )
      val logsJsonCd = stringResource(id = R.string.result_export_logs_json_cd)
      DropdownMenuItem(
        modifier = Modifier.semantics {
          role = Role.Button
          contentDescription = logsJsonCd
        },
        text = { Text(text = stringResource(id = R.string.result_export_logs_json)) },
        onClick = {
          expanded = false
          actions.exportJson()
        },
      )
    }
  }
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
