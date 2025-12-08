package com.qweld.app.feature.exam.fakes

import com.qweld.app.data.reports.QuestionReport
import com.qweld.app.data.reports.QuestionReportRepository
import com.qweld.app.data.reports.QuestionReportWithId

/**
 * Shared fake implementation of [QuestionReportRepository] for tests.
 * Provides no-op behaviors that satisfy interface requirements.
 */
class FakeQuestionReportRepository : QuestionReportRepository {
  override suspend fun submitReport(report: QuestionReport) {
    // No-op for tests
  }

  override suspend fun listReports(status: String?, limit: Int): List<QuestionReportWithId> = emptyList()

  override suspend fun getReportById(reportId: String): QuestionReportWithId? = null

  override suspend fun updateReportStatus(
    reportId: String,
    status: String,
    resolutionCode: String?,
    resolutionComment: String?,
  ) {
    // No-op for tests
  }
}
