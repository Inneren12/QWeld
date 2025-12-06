package com.qweld.app.data.reports

/**
 * Repository for submitting question reports to Firestore.
 * Reports are written to the "question_reports" collection.
 */
interface QuestionReportRepository {
  /**
   * Submits a question report to Firestore.
   *
   * @param report The question report to submit
   * @throws Exception if the submission fails
   */
  suspend fun submitReport(report: QuestionReport)
}
