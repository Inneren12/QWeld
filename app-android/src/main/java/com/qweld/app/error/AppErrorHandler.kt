package com.qweld.app.error

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.qweld.app.data.logging.LogCollector
import com.qweld.core.common.AppEnv
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

const val APP_ERROR_COMMENT_MAX_LENGTH = 500

data class AppError(
  val message: String,
  val throwable: Throwable? = null,
  val kind: Kind = Kind.Unexpected,
) {
  enum class Kind { Unexpected, Reporting, Notice }
}

data class AppErrorContext(
  val screen: String? = null,
  val action: String? = null,
  val extras: Map<String, String?> = emptyMap(),
)

data class UiErrorEvent(
  val error: AppError,
  val context: AppErrorContext,
  val offerReportDialog: Boolean,
)

sealed class AppErrorReportResult {
  data object Submitted : AppErrorReportResult()
  data object Disabled : AppErrorReportResult()
  data class Failed(val reason: String) : AppErrorReportResult()
}

class AppErrorHandler(
  private val crashReporter: CrashReporter,
  private val appEnv: AppEnv,
  private val logCollector: LogCollector?,
  private val analyticsAllowedByBuild: Boolean,
) {
  private val _analyticsEnabled = MutableStateFlow(analyticsAllowedByBuild)
  val analyticsEnabled: StateFlow<Boolean> = _analyticsEnabled.asStateFlow()

  private val _events = MutableSharedFlow<UiErrorEvent>(extraBufferCapacity = 4)
  val events: SharedFlow<UiErrorEvent> = _events.asSharedFlow()

  init {
    crashReporter.setCollectionEnabled(analyticsAllowedByBuild)
  }

  fun updateAnalyticsEnabled(userOptIn: Boolean) {
    val effective = analyticsAllowedByBuild && userOptIn
    _analyticsEnabled.value = effective
    crashReporter.setCollectionEnabled(effective)
  }

  fun emitUiError(
    error: AppError,
    context: AppErrorContext = AppErrorContext(),
    offerReportDialog: Boolean = false,
  ) {
    val event = UiErrorEvent(error = error, context = context, offerReportDialog = offerReportDialog)
    Timber.e(
      error.throwable,
      "[app_error] kind=%s screen=%s action=%s offerDialog=%s",
      error.kind,
      context.screen ?: "unknown",
      context.action ?: "unknown",
      offerReportDialog,
    )
    if (!_events.tryEmit(event)) {
      Timber.w("[app_error] dropped=true reason=buffer_full")
    }
  }

  suspend fun submitReport(event: UiErrorEvent, comment: String?): AppErrorReportResult {
    val sanitizedComment = comment?.takeIf { it.isNotBlank() }?.take(APP_ERROR_COMMENT_MAX_LENGTH)
    if (!_analyticsEnabled.value) {
      Timber.i("[app_error_report] skipped=true reason=analytics_disabled")
      logCollector?.record(
        Log.INFO,
        "AppError",
        "[app_error_report] skipped=true reason=analytics_disabled | attrs={}",
        null,
      )
      return AppErrorReportResult.Disabled
    }

    return runCatching {
      crashReporter.submit(event, sanitizedComment, appEnv, logCollector)
      AppErrorReportResult.Submitted
    }
      .getOrElse { throwable ->
        val reason = throwable.message ?: throwable::class.java.simpleName
        Timber.e(throwable, "[app_error_report] failed reason=%s", reason)
        AppErrorReportResult.Failed(reason)
      }
  }
}

interface CrashReporter {
  fun setCollectionEnabled(enabled: Boolean)

  suspend fun submit(
    event: UiErrorEvent,
    comment: String?,
    appEnv: AppEnv,
    logCollector: LogCollector?,
  )
}

class CrashlyticsCrashReporter(
  private val crashlytics: FirebaseCrashlytics?,
) : CrashReporter {
  override fun setCollectionEnabled(enabled: Boolean) {
    crashlytics?.setCrashlyticsCollectionEnabled(enabled)
  }

  override suspend fun submit(
    event: UiErrorEvent,
    comment: String?,
    appEnv: AppEnv,
    logCollector: LogCollector?,
  ) {
    val context = event.context
    val extrasSummary =
      context.extras
        .filterValues { !it.isNullOrBlank() }
        .entries
        .joinToString(prefix = "{", postfix = "}") { (key, value) -> "$key=${value.orEmpty()}" }
        .ifBlank { "{}" }
    val commentSummary = comment ?: "none"
    val logAttrs =
      "{\"kind\":\"${event.error.kind}\",\"screen\":\"${context.screen ?: "unknown"}\"," +
        "\"action\":\"${context.action ?: "unknown"}\",\"comment_provided\":${comment != null}," +
        "\"extras\":${extrasSummary}}"

    Timber.i(
      "[app_error_report] submitting=true kind=%s screen=%s action=%s | attrs=%s",
      event.error.kind,
      context.screen ?: "unknown",
      context.action ?: "unknown",
      logAttrs,
    )
    logCollector?.record(
      Log.ERROR,
      "AppError",
      "[app_error_report_submit] attrs=$logAttrs",
      event.error.throwable,
    )

    crashlytics?.apply {
      setCustomKey("app_version", appEnv.appVersionName ?: "unknown")
      appEnv.buildType?.let { setCustomKey("build_type", it) }
      setCustomKey("error_kind", event.error.kind.name.lowercase())
      setCustomKey("error_screen", context.screen ?: "unknown")
      setCustomKey("error_action", context.action ?: "unknown")
      setCustomKey("error_has_comment", comment != null)
      log("[app_error_report] $logAttrs comment=$commentSummary")
      val throwable = event.error.throwable ?: AppErrorReportException(event.error.message, commentSummary)
      recordException(throwable)
    } ?: Timber.w("[app_error_report] crashlytics_unavailable=true")
  }
}

class AppErrorReportException(message: String, comment: String? = null) :
  RuntimeException(
    buildString {
      append(message)
      comment?.let { append(" | comment=$it") }
    },
  )
