package com.qweld.app.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
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
    onBack = onBack,
  )
}

@Composable
fun AdminDashboardScreen(
  uiState: AdminDashboardUiState,
  onRefresh: () -> Unit,
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
