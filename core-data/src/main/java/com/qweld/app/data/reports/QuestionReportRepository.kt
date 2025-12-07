package com.qweld.app.data.reports

/**
 * Repository for submitting and managing question reports in Firestore.
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

  /**
   * Lists question reports from Firestore, optionally filtered by status.
   *
   * @param status Optional status filter (e.g., "OPEN", "RESOLVED"). If null, returns all reports.
   * @param limit Maximum number of reports to return (default 50)
   * @return List of reports with their Firestore document IDs, sorted by createdAt descending
   * @throws Exception if the query fails
   */
  suspend fun listReports(
    status: String? = null,
    limit: Int = 50
  ): List<QuestionReportWithId>

  /**
   * Gets a single question report by its Firestore document ID.
   *
   * @param reportId The Firestore document ID
   * @return The report with its ID, or null if not found
   * @throws Exception if the query fails
   */
  suspend fun getReportById(reportId: String): QuestionReportWithId?

  /**
   * Updates the status and review fields of a question report.
   *
   * @param reportId The Firestore document ID
   * @param status New status value (e.g., "OPEN", "IN_REVIEW", "RESOLVED")
   * @param resolutionCode Optional resolution code when closing a report
   * @param resolutionComment Optional resolution comment when closing a report
   * @throws Exception if the update fails
   */
  suspend fun updateReportStatus(
    reportId: String,
    status: String,
    resolutionCode: String? = null,
    resolutionComment: String? = null
  )
}

/**
 * A QuestionReport paired with its Firestore document ID.
 * Used for admin tooling to identify and update specific reports.
 */
data class QuestionReportWithId(
  val id: String,
  val report: QuestionReport
)
