package com.qweld.app.feature.exam.fakes

import com.qweld.app.data.reports.QuestionReport
import com.qweld.app.data.reports.QuestionReportRepository
import com.qweld.app.data.reports.QuestionReportSubmitResult
import com.qweld.app.data.reports.QuestionReportRetryResult
import com.qweld.app.data.reports.QuestionReportWithId
import com.qweld.app.data.reports.QuestionReportSummary

/**
 * Shared fake implementation of [QuestionReportRepository] for tests.
 * Provides no-op behaviors that satisfy interface requirements.
 */
class FakeQuestionReportRepository : QuestionReportRepository {
  override suspend fun submitReport(report: QuestionReport): QuestionReportSubmitResult =
    QuestionReportSubmitResult.Sent

  override suspend fun listReports(status: String?, limit: Int): List<QuestionReportWithId> = emptyList()

  override suspend fun listReportSummaries(limit: Int): List<QuestionReportSummary> = emptyList()

  override suspend fun listReportsForQuestion(questionId: String, limit: Int): List<QuestionReportWithId> = emptyList()

  override suspend fun getReportById(reportId: String): QuestionReportWithId? = null

  override suspend fun updateReportStatus(
    reportId: String,
    status: String,
    resolutionCode: String?,
    resolutionComment: String?,
  ) {
    // No-op for tests
  }

  override suspend fun retryQueuedReports(
    maxAttempts: Int,
    batchSize: Int,
  ) = QuestionReportRetryResult(sent = 0, dropped = 0)
}
