package com.qweld.app.error

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.qweld.app.common.error.AppError
import com.qweld.app.common.error.AppErrorEvent
import com.qweld.app.common.error.AppErrorHandler
import com.qweld.app.common.error.AppErrorReportResult
import com.qweld.app.common.error.UiErrorEvent
import com.qweld.app.data.logging.LogCollector
import com.qweld.core.common.AppEnv
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

const val APP_ERROR_COMMENT_MAX_LENGTH = 500

class AppErrorHandlerImpl(
  private val crashReporter: CrashReporter?,
  private val appEnv: AppEnv,
  private val logCollector: LogCollector?,
  private val analyticsAllowedByBuild: Boolean,
  private val debugBehaviorEnabled: Boolean,
  private val maxHistory: Int = DEFAULT_MAX_HISTORY,
  private val clock: () -> Long = { System.currentTimeMillis() },
) : AppErrorHandler {

  private val _history = MutableStateFlow<List<AppErrorEvent>>(emptyList())
  override val history: StateFlow<List<AppErrorEvent>> = _history.asStateFlow()

  private val _uiEvents = MutableSharedFlow<UiErrorEvent>(extraBufferCapacity = 4)
  override val uiEvents: SharedFlow<UiErrorEvent> = _uiEvents.asSharedFlow()

  private val _analyticsEnabled = MutableStateFlow(analyticsAllowedByBuild)
  override val analyticsEnabled: StateFlow<Boolean> = _analyticsEnabled.asStateFlow()

  init {
    crashReporter?.setCollectionEnabled(analyticsAllowedByBuild)
  }

  override fun handle(error: AppError) {
    val event = AppErrorEvent(error = error, timestamp = clock())
    val tag = buildTag(error)
    logError(tag, event)
    recordHistory(event)
    forwardToCrashlytics(event)
    if (error.offerReportDialog) {
      if (!_uiEvents.tryEmit(UiErrorEvent(event))) {
        Timber.w("[app_error] dropped=true reason=ui_event_buffer_full")
      }
    }
  }

  override fun updateAnalyticsEnabled(userOptIn: Boolean) {
    val effective = analyticsAllowedByBuild && userOptIn
    _analyticsEnabled.value = effective
    crashReporter?.setCollectionEnabled(effective)
  }

  override suspend fun submitReport(event: AppErrorEvent, comment: String?): AppErrorReportResult {
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
      crashReporter?.submit(event, sanitizedComment, appEnv, logCollector)
      AppErrorReportResult.Submitted
    }
      .getOrElse { throwable ->
        val reason = throwable.message ?: throwable::class.java.simpleName
        Timber.e(throwable, "[app_error_report] failed reason=%s", reason)
        AppErrorReportResult.Failed(reason)
      }
  }

  private fun recordHistory(event: AppErrorEvent) {
    _history.update { previous ->
      (previous + event).takeLast(maxHistory)
    }
  }

  private fun logError(tag: String, event: AppErrorEvent) {
    val error = event.error
    val context = error.context
    val attrs =
      context.extras.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "$key=${value}"
      }.ifBlank { "{}" }
    Timber.e(
      error.cause,
      "[app_error] kind=%s screen=%s action=%s reportDialog=%s attrs=%s",
      tag,
      context.screen ?: "unknown",
      context.action ?: "unknown",
      error.offerReportDialog,
      attrs,
    )
    if (debugBehaviorEnabled && error is AppError.Notice) {
      Timber.d("[app_error_notice] message=%s", error.message)
    }
  }

  private fun forwardToCrashlytics(event: AppErrorEvent) {
    if (!event.error.forwardToCrashReporting || !_analyticsEnabled.value) return
    crashReporter?.recordNonFatal(event, appEnv)
  }

  private fun buildTag(error: AppError): String =
    when (error) {
      is AppError.Network -> "network"
      is AppError.Reporting -> "reporting"
      is AppError.Unexpected -> "unexpected"
      is AppError.Notice -> "notice"
    }

  companion object {
    private const val DEFAULT_MAX_HISTORY = 30
  }
}

interface CrashReporter {
  fun setCollectionEnabled(enabled: Boolean)

  fun recordNonFatal(event: AppErrorEvent, appEnv: AppEnv)

  suspend fun submit(
    event: AppErrorEvent,
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

  override fun recordNonFatal(event: AppErrorEvent, appEnv: AppEnv) {
    crashlytics?.apply {
      setCustomKey("app_version", appEnv.appVersionName ?: "unknown")
      appEnv.buildType?.let { setCustomKey("build_type", it) }
      setCustomKey("error_kind", event.error.javaClass.simpleName.lowercase())
      setCustomKey("error_screen", event.error.context.screen ?: "unknown")
      setCustomKey("error_action", event.error.context.action ?: "unknown")
      log("[app_error] ${event.id}")
      val throwable = event.error.cause ?: AppErrorException(event)
      recordException(throwable)
    } ?: Timber.w("[app_error] crashlytics_unavailable=true")
  }

  override suspend fun submit(
    event: AppErrorEvent,
    comment: String?,
    appEnv: AppEnv,
    logCollector: LogCollector?,
  ) {
    val context = event.error.context
    val extrasSummary =
      context.extras
        .entries
        .joinToString(prefix = "{", postfix = "}") { (key, value) -> "$key=${value}" }
        .ifBlank { "{}" }
    val commentSummary = comment ?: "none"
    val logAttrs =
      "{\"kind\":\"${event.error.javaClass.simpleName}\",\"screen\":\"${context.screen ?: "unknown"}\"," +
        "\"action\":\"${context.action ?: "unknown"}\",\"comment_provided\":${comment != null}," +
        "\"extras\":${extrasSummary}}"

    Timber.i(
      "[app_error_report] submitting=true kind=%s screen=%s action=%s | attrs=%s",
      event.error.javaClass.simpleName,
      context.screen ?: "unknown",
      context.action ?: "unknown",
      logAttrs,
    )
    logCollector?.record(
      Log.ERROR,
      "AppError",
      "[app_error_report_submit] attrs=$logAttrs",
      event.error.cause,
    )

    crashlytics?.apply {
      setCustomKey("app_version", appEnv.appVersionName ?: "unknown")
      appEnv.buildType?.let { setCustomKey("build_type", it) }
      setCustomKey("error_kind", event.error.javaClass.simpleName.lowercase())
      setCustomKey("error_screen", context.screen ?: "unknown")
      setCustomKey("error_action", context.action ?: "unknown")
      setCustomKey("error_has_comment", comment != null)
      log("[app_error_report] $logAttrs comment=$commentSummary")
      val throwable = event.error.cause ?: AppErrorReportException(logAttrs, commentSummary)
      recordException(throwable)
    } ?: Timber.w("[app_error_report] crashlytics_unavailable=true")
  }
}

class AppErrorException(event: AppErrorEvent) :
  RuntimeException(
    buildString {
      append("AppError: ${event.error.javaClass.simpleName}")
      event.error.context.screen?.let { append(" | screen=$it") }
      event.error.context.action?.let { append(" | action=$it") }
    },
  )

class AppErrorReportException(message: String, comment: String? = null) :
  RuntimeException(
    buildString {
      append(message)
      comment?.let { append(" | comment=$it") }
    },
  )
