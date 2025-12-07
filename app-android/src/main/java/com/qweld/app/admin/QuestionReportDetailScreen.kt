package com.qweld.app.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweld.app.data.reports.QuestionReport
import com.qweld.app.data.reports.QuestionReportWithId

/**
 * Admin screen for viewing and updating a single question report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionReportDetailScreen(
  viewModel: QuestionReportDetailViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text("Report Details") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { paddingValues ->
    when (val state = uiState) {
      is QuestionReportDetailViewModel.UiState.Loading -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }
      }

      is QuestionReportDetailViewModel.UiState.Success -> {
        ReportDetailContent(
          reportWithId = state.report,
          onUpdateStatus = { status, resolutionCode, comment ->
            viewModel.updateStatus(status, resolutionCode, comment)
          },
          modifier = Modifier.padding(paddingValues)
        )
      }

      is QuestionReportDetailViewModel.UiState.Error -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
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
            Button(onClick = { viewModel.loadReport() }) {
              Text("Retry")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ReportDetailContent(
  reportWithId: QuestionReportWithId,
  onUpdateStatus: (String, String?, String?) -> Unit,
  modifier: Modifier = Modifier
) {
  val report = reportWithId.report
  var showStatusDialog by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Status section with update button
    Card {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "Status",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = report.status,
            style = MaterialTheme.typography.bodyLarge,
            color = when (report.status) {
              "OPEN" -> MaterialTheme.colorScheme.error
              "IN_REVIEW" -> MaterialTheme.colorScheme.tertiary
              "RESOLVED" -> MaterialTheme.colorScheme.primary
              "WONT_FIX" -> MaterialTheme.colorScheme.secondary
              else -> MaterialTheme.colorScheme.onSurface
            }
          )
          Button(onClick = { showStatusDialog = true }) {
            Text("Update Status")
          }
        }
      }
    }

    // Core identifiers
    SectionCard(title = "Core Information") {
      DetailRow("Report ID", reportWithId.id)
      DetailRow("Question ID", report.questionId)
      DetailRow("Task ID", report.taskId)
      DetailRow("Block ID", report.blockId)
      DetailRow("Blueprint ID", report.blueprintId)
      DetailRow("Created At", report.createdAt.formatDateTime())
    }

    // Reason & comment
    SectionCard(title = "Report Reason") {
      DetailRow("Reason Code", report.reasonCode)
      report.reasonDetail?.let {
        DetailRow("Reason Detail", it)
      }
      report.userComment?.let {
        if (it.isNotBlank()) {
          DetailRow("User Comment", it)
        }
      }
    }

    // Question context
    SectionCard(title = "Question Context") {
      DetailRow("Locale", report.locale)
      DetailRow("Mode", report.mode)
      report.questionIndex?.let {
        DetailRow("Question Index", "${it + 1} of ${report.totalQuestions ?: "?"}")
      }
      report.selectedChoiceIds?.let {
        DetailRow("Selected Choices", it.joinToString(", "))
      }
      report.correctChoiceIds?.let {
        DetailRow("Correct Choices", it.joinToString(", "))
      }
      report.blueprintTaskQuota?.let {
        DetailRow("Blueprint Task Quota", it.toString())
      }
    }

    // Environment info
    SectionCard(title = "Environment") {
      report.appVersionName?.let {
        DetailRow("App Version", "$it (${report.appVersionCode ?: "?"})")
      }
      report.buildType?.let {
        DetailRow("Build Type", it)
      }
      report.platform?.let {
        DetailRow("Platform", it)
      }
      report.osVersion?.let {
        DetailRow("OS Version", it)
      }
      report.deviceModel?.let {
        DetailRow("Device Model", it)
      }
      report.contentIndexSha?.let {
        DetailRow("Content Index SHA", it.take(8))
      }
      report.blueprintVersion?.let {
        DetailRow("Blueprint Version", it)
      }
    }

    // Session context
    SectionCard(title = "Session Context") {
      report.sessionId?.let {
        DetailRow("Session ID", it.take(12) + "...")
      }
      report.attemptId?.let {
        DetailRow("Attempt ID", it.take(12) + "...")
      }
      report.seed?.let {
        DetailRow("Seed", it.toString())
      }
      report.attemptKind?.let {
        DetailRow("Attempt Kind", it)
      }
    }

    // Review info
    report.review?.let { reviewMap ->
      SectionCard(title = "Review Information") {
        reviewMap["assignee"]?.let {
          DetailRow("Assignee", it.toString())
        }
        reviewMap["resolutionCode"]?.let {
          DetailRow("Resolution Code", it.toString())
        }
        reviewMap["resolutionComment"]?.let {
          DetailRow("Resolution Comment", it.toString())
        }
        reviewMap["resolvedAt"]?.let {
          DetailRow("Resolved At", it.toString())
        }
      }
    }
  }

  // Status update dialog
  if (showStatusDialog) {
    StatusUpdateDialog(
      currentStatus = report.status,
      onDismiss = { showStatusDialog = false },
      onConfirm = { newStatus, resolutionCode, comment ->
        onUpdateStatus(newStatus, resolutionCode, comment)
        showStatusDialog = false
      }
    )
  }
}

@Composable
private fun SectionCard(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit
) {
  Card(modifier = modifier) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
      content()
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium
    )
  }
}

@Composable
private fun StatusUpdateDialog(
  currentStatus: String,
  onDismiss: () -> Unit,
  onConfirm: (String, String?, String?) -> Unit
) {
  var selectedStatus by remember { mutableStateOf(currentStatus) }
  var resolutionCode by remember { mutableStateOf("") }
  var comment by remember { mutableStateOf("") }

  val statuses = listOf("OPEN", "IN_REVIEW", "RESOLVED", "WONT_FIX")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Update Status") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Select new status:")

        // Status radio buttons
        statuses.forEach { status ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            RadioButton(
              selected = selectedStatus == status,
              onClick = { selectedStatus = status }
            )
            Text(
              text = status.replace("_", " "),
              modifier = Modifier.padding(start = 8.dp)
            )
          }
        }

        // Resolution code
        OutlinedTextField(
          value = resolutionCode,
          onValueChange = { resolutionCode = it },
          label = { Text("Resolution Code (optional)") },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text("e.g., FIXED, DUPLICATE, NO_ACTION") }
        )

        // Resolution comment
        OutlinedTextField(
          value = comment,
          onValueChange = { comment = it },
          label = { Text("Resolution Comment (optional)") },
          modifier = Modifier.fillMaxWidth(),
          maxLines = 3
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(
            selectedStatus,
            resolutionCode.takeIf { it.isNotBlank() },
            comment.takeIf { it.isNotBlank() }
          )
        }
      ) {
        Text("Update")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
