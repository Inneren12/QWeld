package com.qweld.app.data.reports

import com.google.firebase.Timestamp

/**
 * Repository for submitting and managing question reports in Firestore.
 * Reports are written to the "question_reports" collection.
 */
interface QuestionReportRepository {
  /**
   * Submits a question report to Firestore.
   *
   * @param report The question report to submit
   * @return [QuestionReportSubmitResult] describing whether the report was sent immediately or queued
   */
  suspend fun submitReport(report: QuestionReport): QuestionReportSubmitResult

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
   * Lists reports grouped by question for quick triage in admin tooling.
   *
   * @param limit Maximum number of raw report documents to scan (newest first)
   */
  suspend fun listReportSummaries(limit: Int = 200): List<QuestionReportSummary>

  /**
   * Fetches the most recent reports for a specific question.
   */
  suspend fun listReportsForQuestion(
    questionId: String,
    limit: Int = 20,
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

  /**
   * Retries any queued question reports that failed to send previously.
   *
   * @param maxAttempts Maximum attempts before a queued report is dropped
   * @param batchSize Number of queued reports to process per invocation
   * @return Counts for successfully sent and dropped reports
   */
  suspend fun retryQueuedReports(
    maxAttempts: Int = DEFAULT_MAX_RETRY_ATTEMPTS,
    batchSize: Int = DEFAULT_RETRY_BATCH_SIZE,
  ): QuestionReportRetryResult
}

/**
 * A QuestionReport paired with its Firestore document ID.
 * Used for admin tooling to identify and update specific reports.
 */
data class QuestionReportWithId(
  val id: String,
  val report: QuestionReport
)

data class QuestionReportSummary(
  val questionId: String,
  val taskId: String?,
  val blockId: String?,
  val reportsCount: Int,
  val lastReportAt: Timestamp?,
  val lastStatus: String?,
  val lastReasonCode: String?,
  val lastLocale: String?,
  val latestUserComment: String?,
)

data class QuestionReportRetryResult(
  val sent: Int,
  val dropped: Int,
)

const val DEFAULT_MAX_RETRY_ATTEMPTS = 5
const val DEFAULT_RETRY_BATCH_SIZE = 25

sealed class QuestionReportSubmitResult {
  data object Sent : QuestionReportSubmitResult()
  data class Queued(val queueId: Long? = null, val error: Throwable? = null) : QuestionReportSubmitResult()
}
