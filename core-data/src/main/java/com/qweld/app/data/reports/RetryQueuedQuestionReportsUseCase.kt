package com.qweld.app.data.reports

/**
 * Use case wrapper for retrying queued question reports.
 */
class RetryQueuedQuestionReportsUseCase(
  private val repository: QuestionReportRepository,
) {
  suspend operator fun invoke(
    maxAttempts: Int = DEFAULT_MAX_RETRY_ATTEMPTS,
    batchSize: Int = DEFAULT_RETRY_BATCH_SIZE,
  ): QuestionReportRetryResult {
    return repository.retryQueuedReports(maxAttempts = maxAttempts, batchSize = batchSize)
  }
}
