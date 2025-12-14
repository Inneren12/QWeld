package com.qweld.app.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweld.app.R
import com.qweld.app.data.reports.QuestionReportSummary
import com.qweld.app.data.reports.QuestionReportWithId
import java.text.DateFormat
import java.util.Date

@Composable
fun AdminDashboardRoute(
  viewModel: AdminDashboardViewModel,
  onBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  AdminDashboardScreen(
    uiState = uiState,
    onRefresh = { viewModel.refresh() },
    onReportsRefresh = { viewModel.loadReportSummaries() },
    onSelectQuestion = { questionId -> viewModel.loadReportsForQuestion(questionId) },
    onClearSelection = { viewModel.clearSelectedQuestion() },
    onBack = onBack,
  )
}

@Composable
fun AdminDashboardScreen(
  uiState: AdminDashboardUiState,
  onRefresh: () -> Unit,
  onReportsRefresh: () -> Unit,
  onSelectQuestion: (String) -> Unit,
  onClearSelection: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  Scaffold { innerPadding ->
    Column(
      modifier =
        Modifier
          .padding(innerPadding)
          .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Filled.BugReport, contentDescription = null)
        Text(
          text = stringResource(id = R.string.admin_dashboard_title),
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.padding(start = 8.dp),
        )
      }

      Text(
        text = stringResource(id = R.string.admin_dashboard_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      when {
        uiState.isLoading -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
          ) {
            CircularProgressIndicator()
          }
        }

        uiState.errorMessage != null -> {
          AdminDashboardError(
            message = uiState.errorMessage,
            onRetry = onRefresh,
          )
        }

        uiState.data != null -> {
          DashboardContent(
            data = uiState.data,
            onRefresh = onRefresh,
            reportsState = uiState.reports,
            onReportsRefresh = onReportsRefresh,
            onSelectQuestion = onSelectQuestion,
            onClearSelection = onClearSelection,
          )
        }
      }

      Spacer(modifier = Modifier.weight(1f, fill = true))
      TextButton(onClick = onBack) {
        Text(text = stringResource(id = android.R.string.cancel))
      }
    }
  }
}

@Composable
private fun DashboardContent(
  data: AdminDashboardData,
  onRefresh: () -> Unit,
  reportsState: QuestionReportsDashboardState,
  onReportsRefresh: () -> Unit,
  onSelectQuestion: (String) -> Unit,
  onClearSelection: () -> Unit,
) {
  val attempts = data.attemptStats
  val lastFinished = data.attemptStats.lastFinishedAt?.asDateTime()
    ?: stringResource(id = R.string.admin_dashboard_unknown)

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(id = R.string.admin_dashboard_attempts_header),
          style = MaterialTheme.typography.titleMedium,
        )
        StatsList(
          items = listOf(
            stringResource(id = R.string.admin_dashboard_total_attempts) to attempts.totalCount,
            stringResource(id = R.string.admin_dashboard_finished_attempts) to attempts.finishedCount,
            stringResource(id = R.string.admin_dashboard_in_progress_attempts) to attempts.inProgressCount,
            stringResource(id = R.string.admin_dashboard_failed_attempts) to attempts.failedCount,
            stringResource(id = R.string.admin_dashboard_answers_count) to data.answerCount,
          ),
        )
        Divider()
        Text(
          text = stringResource(id = R.string.admin_dashboard_last_finished, lastFinished),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Card {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(imageVector = Icons.Filled.Storage, contentDescription = null)
          Text(text = stringResource(id = R.string.admin_dashboard_db_header), style = MaterialTheme.typography.titleMedium)
        }
        DbHealthBlock(dbHealth = data.dbHealth)
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
          Text(text = stringResource(id = R.string.admin_dashboard_refresh))
        }
      }
    }

    Card {
      SystemHealthCard(systemHealth = data.systemHealth, onRefresh = onRefresh)
    }

    Card {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(imageVector = Icons.Filled.BugReport, contentDescription = null)
          Text(text = stringResource(id = R.string.admin_dashboard_reports_header), style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.weight(1f))
          TextButton(onClick = onReportsRefresh) {
            Text(text = stringResource(id = R.string.admin_dashboard_refresh))
          }
        }
        QuestionReportsSummaryList(
          state = reportsState,
          onRetry = onReportsRefresh,
          onSelectQuestion = onSelectQuestion,
          onClearSelection = onClearSelection,
        )
      }
    }
  }
}

@Composable
private fun DbHealthBlock(dbHealth: DbHealth) {
  val statusText =
    if (dbHealth.isAtExpectedVersion) {
      stringResource(id = R.string.admin_dashboard_db_status_ok)
    } else {
      stringResource(id = R.string.admin_dashboard_db_status_mismatch)
    }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(text = statusText, fontWeight = FontWeight.Medium)
    StatsList(
      items = listOf(
        stringResource(id = R.string.admin_dashboard_db_version) to dbHealth.userVersion,
        stringResource(id = R.string.admin_dashboard_db_expected_version) to dbHealth.expectedVersion,
      ),
    )
  }
}

@Composable
private fun SystemHealthCard(systemHealth: SystemHealthStatus, onRefresh: () -> Unit) {
  val queueStatus = systemHealth.queueStatus
  val oldestQueued = queueStatus.oldestQueuedAt?.asDateTime()
    ?: stringResource(id = R.string.admin_dashboard_unknown)
  val lastQueueAttempt = queueStatus.lastAttemptAt?.asDateTime()
    ?: stringResource(id = R.string.admin_dashboard_unknown)
  val lastError = systemHealth.lastErrorAt?.asDateTime()
    ?: stringResource(id = R.string.admin_dashboard_health_no_errors)

  Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(imageVector = Icons.Filled.Error, contentDescription = null)
      Text(text = stringResource(id = R.string.admin_dashboard_health_header), style = MaterialTheme.typography.titleMedium)
    }

    StatsList(
      items = listOf(
        stringResource(id = R.string.admin_dashboard_health_queue_count) to queueStatus.queuedCount,
        stringResource(id = R.string.admin_dashboard_health_errors_count) to systemHealth.recentErrorCount,
      ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = stringResource(id = R.string.admin_dashboard_health_oldest_queue, oldestQueued),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = stringResource(id = R.string.admin_dashboard_health_last_attempt, lastQueueAttempt),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Divider()

    if (systemHealth.hasErrors) {
      Text(
        text = stringResource(id = R.string.admin_dashboard_health_last_error, lastError),
        style = MaterialTheme.typography.bodyMedium,
      )
    } else {
      Text(
        text = stringResource(id = R.string.admin_dashboard_health_no_errors),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
      Text(text = stringResource(id = R.string.admin_dashboard_refresh))
    }
  }
}

@Composable
private fun StatsList(items: List<Pair<String, Int>>) {
  LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    items(items) { (label, value) ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
      }
    }
  }
}

@Composable
private fun QuestionReportsSummaryList(
  state: QuestionReportsDashboardState,
  onRetry: () -> Unit,
  onSelectQuestion: (String) -> Unit,
  onClearSelection: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    when {
      state.isLoading -> {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
          CircularProgressIndicator()
        }
      }

      state.errorMessage != null -> {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = stringResource(id = R.string.admin_dashboard_reports_error, state.errorMessage),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
          TextButton(onClick = onRetry) { Text(text = stringResource(id = R.string.admin_dashboard_refresh)) }
        }
      }

      state.summaries.isEmpty() -> {
        Text(
          text = stringResource(id = R.string.admin_dashboard_reports_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      else -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.summaries, key = { it.questionId }) { summary ->
            QuestionReportSummaryRow(summary = summary, onSelectQuestion = onSelectQuestion)
          }
        }
      }
    }

    state.selectedQuestionId?.let { questionId ->
      QuestionReportDetailBlock(
        questionId = questionId,
        state = state,
        onClearSelection = onClearSelection,
        onRetry = { onSelectQuestion(questionId) },
      )
    }
  }
}

@Composable
private fun QuestionReportSummaryRow(
  summary: QuestionReportSummary,
  onSelectQuestion: (String) -> Unit,
) {
  Card(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onSelectQuestion(summary.questionId) },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = summary.questionId,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        summary.taskId?.takeIf { it.isNotBlank() }?.let { taskId ->
          Text(
            text = taskId,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        StatusChip(status = summary.lastStatus)
      }

      Text(
        text = stringResource(
          id = R.string.admin_dashboard_reports_count_line,
          summary.reportsCount,
          summary.lastReportAt.formatDateTime(),
        ),
        style = MaterialTheme.typography.bodyMedium,
      )

      val localeReason = listOfNotNull(summary.lastLocale, summary.lastReasonCode).joinToString(separator = " • ")
      if (localeReason.isNotBlank()) {
        Text(
          text = localeReason,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (summary.hasErrorContext) {
        Text(
          text = stringResource(id = R.string.admin_dashboard_reports_after_error),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      summary.latestUserComment?.takeIf { it.isNotBlank() }?.let { comment ->
        Text(
          text = stringResource(id = R.string.admin_dashboard_reports_comment_preview, comment.take(140)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun QuestionReportDetailBlock(
  questionId: String,
  state: QuestionReportsDashboardState,
  onClearSelection: () -> Unit,
  onRetry: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(id = R.string.admin_dashboard_reports_detail_header, questionId),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.weight(1f))
      TextButton(onClick = onClearSelection) {
        Text(text = stringResource(id = R.string.admin_dashboard_reports_clear_selection))
      }
    }

    when {
      state.isDetailLoading -> {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
          CircularProgressIndicator()
        }
      }

      state.detailError != null -> {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = state.detailError,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
          Button(onClick = onRetry) {
            Text(text = stringResource(id = R.string.admin_dashboard_refresh))
          }
        }
      }

      state.selectedReports.isEmpty() -> {
        Text(
          text = stringResource(id = R.string.admin_dashboard_reports_detail_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      else -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.selectedReports, key = { it.id }) { reportWithId ->
            QuestionReportDetailItem(reportWithId = reportWithId)
            Divider()
          }
        }
      }
    }
  }
}

@Composable
private fun QuestionReportDetailItem(reportWithId: QuestionReportWithId) {
  val report = reportWithId.report
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = report.locale,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.weight(1f))
      Text(
        text = report.createdAt.formatDateTime(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Text(
      text = stringResource(id = R.string.admin_dashboard_reports_reason_line, report.reasonCode, report.status),
      style = MaterialTheme.typography.bodyMedium,
    )

    if (report.recentError == true || !report.errorContextMessage.isNullOrBlank()) {
      val message = report.errorContextMessage?.takeIf { it.isNotBlank() }
        ?: stringResource(id = R.string.admin_dashboard_reports_after_error)
      Text(
        text = stringResource(id = R.string.admin_dashboard_reports_error_context, message),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    report.userComment?.takeIf { it.isNotBlank() }?.let { comment ->
      Text(
        text = comment,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun StatusChip(status: String?, modifier: Modifier = Modifier) {
  val (container, content) = when (status) {
    "OPEN" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    "IN_REVIEW" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    "RESOLVED" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    "WONT_FIX" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = container),
    shape = MaterialTheme.shapes.small,
    modifier = modifier,
  ) {
    Text(
      text = status ?: "—",
      style = MaterialTheme.typography.labelSmall,
      color = content,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun AdminDashboardError(
  message: String,
  onRetry: () -> Unit,
) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
      Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(id = R.string.admin_dashboard_retry))
      }
    }
  }
}

private fun Long.asDateTime(): String {
  return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))
}
