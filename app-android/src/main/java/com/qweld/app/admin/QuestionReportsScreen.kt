package com.qweld.app.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweld.app.data.reports.QuestionReportWithId

/**
 * Admin screen for viewing and filtering question reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionReportsScreen(
  viewModel: QuestionReportsViewModel,
  onNavigateToDetail: (String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val selectedFilter by viewModel.selectedStatusFilter.collectAsState()

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text("Question Reports") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { viewModel.refresh() }) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
          }
        }
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Status filter chips
      StatusFilterRow(
        selectedFilter = selectedFilter,
        onFilterSelected = { viewModel.setStatusFilter(it) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )

      Divider()

      // Content
      when (val state = uiState) {
        is QuestionReportsViewModel.UiState.Loading -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator()
          }
        }

        is QuestionReportsViewModel.UiState.Empty -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (selectedFilter != null) {
                "No reports with status: $selectedFilter"
              } else {
                "No reports found"
              },
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        is QuestionReportsViewModel.UiState.Success -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
          ) {
            items(
              items = state.reports,
              key = { it.id }
            ) { reportWithId ->
              ReportListItem(
                reportWithId = reportWithId,
                onClick = { onNavigateToDetail(reportWithId.id) },
                modifier = Modifier.fillMaxWidth()
              )
              Divider()
            }
          }
        }

        is QuestionReportsViewModel.UiState.Error -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = "Error: ${state.message}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
              )
              Button(onClick = { viewModel.refresh() }) {
                Text("Retry")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatusFilterRow(
  selectedFilter: String?,
  onFilterSelected: (String?) -> Unit,
  modifier: Modifier = Modifier
) {
  val statuses = listOf(
    null to "All",
    "OPEN" to "Open",
    "IN_REVIEW" to "In Review",
    "RESOLVED" to "Resolved",
    "WONT_FIX" to "Won't Fix"
  )

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    statuses.forEach { (status, label) ->
      FilterChip(
        selected = selectedFilter == status,
        onClick = { onFilterSelected(status) },
        label = { Text(label) }
      )
    }
  }
}

@Composable
private fun ReportListItem(
  reportWithId: QuestionReportWithId,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val report = reportWithId.report

  Surface(
    modifier = modifier.clickable(onClick = onClick),
    color = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      // Header row: status badge + date
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        StatusBadge(status = report.status)
        Text(
          text = report.createdAt.formatDateTime(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Question ID + Reason
      Text(
        text = "Question: ${report.questionId}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
      )

      Text(
        text = "Reason: ${report.reasonCode}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      // Task/Block context
      Text(
        text = "Task: ${report.taskId} / Block: ${report.blockId}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      // Locale + Mode
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "Locale: ${report.locale}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "Mode: ${report.mode}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // User comment preview if present
      report.userComment?.let { comment ->
        if (comment.isNotBlank()) {
          Text(
            text = "Comment: ${comment.take(100)}${if (comment.length > 100) "..." else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun StatusBadge(status: String, modifier: Modifier = Modifier) {
  val (backgroundColor, textColor) = when (status) {
    "OPEN" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    "IN_REVIEW" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    "RESOLVED" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    "WONT_FIX" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    modifier = modifier,
    color = backgroundColor,
    shape = MaterialTheme.shapes.small
  ) {
    Text(
      text = status,
      style = MaterialTheme.typography.labelSmall,
      color = textColor,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
  }
}
