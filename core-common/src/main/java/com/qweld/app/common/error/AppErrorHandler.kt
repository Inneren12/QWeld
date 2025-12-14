package com.qweld.app.common.error

import java.util.UUID
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Central abstraction for non-fatal app errors.
 *
 * Implementations should route errors to logging/Crashlytics (when enabled)
 * and emit UI-friendly events when a user-facing surface needs to react.
 */
interface AppErrorHandler {
  val history: StateFlow<List<AppErrorEvent>>
  val uiEvents: SharedFlow<UiErrorEvent>
  val analyticsEnabled: StateFlow<Boolean>

  fun handle(error: AppError)

  fun updateAnalyticsEnabled(userOptIn: Boolean)

  suspend fun submitReport(event: AppErrorEvent, comment: String?): AppErrorReportResult
}

data class ErrorContext(
  val screen: String? = null,
  val action: String? = null,
  val extras: Map<String, String> = emptyMap(),
)

sealed class AppError(open val context: ErrorContext, open val offerReportDialog: Boolean = false) {
  abstract val cause: Throwable?
  abstract val forwardToCrashReporting: Boolean

  data class Unexpected(
    override val cause: Throwable,
    override val context: ErrorContext,
    override val offerReportDialog: Boolean = false,
    override val forwardToCrashReporting: Boolean = true,
  ) : AppError(context, offerReportDialog)

  data class Network(
    override val cause: Throwable,
    override val context: ErrorContext,
    override val offerReportDialog: Boolean = false,
    override val forwardToCrashReporting: Boolean = true,
  ) : AppError(context, offerReportDialog)

  data class Reporting(
    override val cause: Throwable,
    override val context: ErrorContext,
    override val offerReportDialog: Boolean = false,
    override val forwardToCrashReporting: Boolean = true,
  ) : AppError(context, offerReportDialog)

  data class Notice(
    val message: String,
    override val context: ErrorContext,
    override val offerReportDialog: Boolean = false,
    override val forwardToCrashReporting: Boolean = false,
    override val cause: Throwable? = null,
  ) : AppError(context, offerReportDialog)
}

data class AppErrorEvent(
  val id: String = UUID.randomUUID().toString(),
  val error: AppError,
  val timestamp: Long,
)

data class UiErrorEvent(
  val event: AppErrorEvent,
)

sealed class AppErrorReportResult {
  data object Submitted : AppErrorReportResult()
  data object Disabled : AppErrorReportResult()
  data class Failed(val reason: String) : AppErrorReportResult()
}
