package com.qweld.app.admin

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.firebase.Timestamp
import com.qweld.app.data.reports.QuestionReport
import com.qweld.app.data.reports.QuestionReportRepository
import com.qweld.app.data.reports.QuestionReportRetryResult
import com.qweld.app.data.reports.QuestionReportSubmitResult
import com.qweld.app.data.reports.QuestionReportWithId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 34)
class AdminReportsUiTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun questionReportsList_navigatesToDetailAndShowsComment() {
    val repository = FakeQuestionReportRepository()
    val listViewModel = QuestionReportsViewModel(repository)

    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        AdminReportsTestScaffold(repository = repository, listViewModel = listViewModel)
      }
    }

    composeTestRule.onNodeWithText("Question: TEST-Q1").assertExists()
    composeTestRule.onNodeWithText("Question: TEST-Q1").performClick()

    composeTestRule.onNodeWithText("Report Details").assertExists()
    composeTestRule.onNodeWithText("User Comment").assertExists()
    composeTestRule.onNodeWithText("Admin test comment").assertExists()
  }
}

@Composable
private fun AdminReportsTestScaffold(
  repository: FakeQuestionReportRepository,
  listViewModel: QuestionReportsViewModel,
) {
  var selectedId by remember { mutableStateOf<String?>(null) }

  if (selectedId == null) {
    QuestionReportsScreen(
      viewModel = listViewModel,
      onNavigateToDetail = { selectedId = it },
      onBack = {},
    )
  } else {
    val detailViewModel = remember(selectedId) { QuestionReportDetailViewModel(repository, selectedId!!) }
    QuestionReportDetailScreen(viewModel = detailViewModel, onBack = { selectedId = null })
  }
}

private class FakeQuestionReportRepository : QuestionReportRepository {
  private val reports =
    mutableListOf(
      QuestionReportWithId(
        id = "report-1",
        report =
          QuestionReport(
            questionId = "TEST-Q1",
            taskId = "A-1",
            blockId = "A",
            blueprintId = "welder_ip_sk_202404",
            locale = "en",
            mode = "IP_MOCK",
            reasonCode = "WRONG_ANSWER",
            userComment = "Admin test comment",
            questionIndex = 0,
            totalQuestions = 1,
            status = "OPEN",
            createdAt = Timestamp.now(),
          ),
      ),
    )

  override suspend fun submitReport(report: QuestionReport): QuestionReportSubmitResult =
    QuestionReportSubmitResult.Sent

  override suspend fun listReports(status: String?, limit: Int): List<QuestionReportWithId> {
    return reports.filter { status == null || it.report.status == status }.take(limit)
  }

  override suspend fun getReportById(reportId: String): QuestionReportWithId? {
    return reports.firstOrNull { it.id == reportId }
  }

  override suspend fun updateReportStatus(
    reportId: String,
    status: String,
    resolutionCode: String?,
    resolutionComment: String?,
  ) {
    val existingIndex = reports.indexOfFirst { it.id == reportId }
    if (existingIndex >= 0) {
      val current = reports[existingIndex]
      reports[existingIndex] = current.copy(report = current.report.copy(status = status))
    }
  }

  override suspend fun retryQueuedReports(
    maxAttempts: Int,
    batchSize: Int,
  ): QuestionReportRetryResult = QuestionReportRetryResult(sent = 0, dropped = 0)
}
