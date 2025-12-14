package com.qweld.app.error

import com.qweld.app.common.error.AppError
import com.qweld.app.common.error.AppErrorEvent
import com.qweld.app.common.error.AppErrorReportResult
import com.qweld.app.common.error.ErrorContext
import com.qweld.app.common.error.UiErrorEvent
import com.qweld.app.data.logging.LogCollector
import com.qweld.core.common.AppEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppErrorHandlerTest {
  @Test
  fun handleUnexpected_appendsHistoryEmitsUiEventAndRecordsNonFatal() = runTest {
    val crashReporter = RecordingCrashReporter()
    val handler = createHandler(crashReporter, analyticsAllowedByBuild = true, clockValue = 1_000L)
    val error =
      AppError.Unexpected(
        cause = IllegalStateException("boom"),
        context = ErrorContext(screen = "home", action = "tap"),
        offerReportDialog = true,
      )

    handler.handle(error)

    val history = handler.history.value
    assertEquals(1, history.size)
    val event = history.first()
    assertEquals(error, event.error)
    assertEquals(1_000L, event.timestamp)
    assertEquals(listOf(event), crashReporter.recordedNonFatal)

    val uiEvent: UiErrorEvent = handler.uiEvents.first()
    assertEquals(event, uiEvent.event)
  }

  @Test
  fun handleNotice_skipsCrashReporterAndUiEventWhenDialogNotOffered() = runTest {
    val crashReporter = RecordingCrashReporter()
    val handler = createHandler(crashReporter, analyticsAllowedByBuild = true)
    val notice =
      AppError.Notice(
        message = "debug log only",
        context = ErrorContext(screen = "settings"),
        offerReportDialog = false,
      )

    val emittedEvents = mutableListOf<UiErrorEvent>()
    val collector = launch { handler.uiEvents.collect { emittedEvents += it } }

    handler.handle(notice)

    assertTrue(handler.history.value.isNotEmpty())
    assertTrue(crashReporter.recordedNonFatal.isEmpty())
    assertTrue(emittedEvents.isEmpty())

    collector.cancel()
  }

  @Test
  fun analyticsEnabled_reflectsBuildFlagAndUserOptIn() = runTest {
    val crashReporter = RecordingCrashReporter()
    val handler = createHandler(crashReporter, analyticsAllowedByBuild = true)

    assertTrue(handler.analyticsEnabled.value)
    assertEquals(listOf(true), crashReporter.collectionFlags)

    handler.updateAnalyticsEnabled(userOptIn = false)

    assertFalse(handler.analyticsEnabled.value)
    assertEquals(listOf(true, false), crashReporter.collectionFlags)

    handler.handle(
      AppError.Unexpected(
        cause = IllegalStateException("disabled"),
        context = ErrorContext(screen = "home"),
      ),
    )

    assertTrue(crashReporter.recordedNonFatal.isEmpty())
  }

  @Test
  fun submitReport_respectsAnalyticsToggle() = runTest {
    val crashReporter = RecordingCrashReporter()
    val handler = createHandler(crashReporter, analyticsAllowedByBuild = true)
    val errorEvent =
      AppErrorEvent(
        error = AppError.Reporting(
          cause = IllegalStateException("report"),
          context = ErrorContext(screen = "reports"),
        ),
        timestamp = 5_000L,
      )

    handler.updateAnalyticsEnabled(userOptIn = false)

    val disabledResult = handler.submitReport(errorEvent, comment = "context")
    assertIs<AppErrorReportResult.Disabled>(disabledResult)
    assertTrue(crashReporter.submitted.isEmpty())

    handler.updateAnalyticsEnabled(userOptIn = true)

    val submittedResult = handler.submitReport(errorEvent, comment = "context")
    assertIs<AppErrorReportResult.Submitted>(submittedResult)
    assertEquals(listOf(errorEvent to "context"), crashReporter.submitted)
  }

  private fun createHandler(
    crashReporter: RecordingCrashReporter?,
    analyticsAllowedByBuild: Boolean,
    clockValue: Long = 0L,
  ): AppErrorHandlerImpl {
    return AppErrorHandlerImpl(
      crashReporter = crashReporter,
      appEnv = FakeAppEnv,
      logCollector = null,
      analyticsAllowedByBuild = analyticsAllowedByBuild,
      debugBehaviorEnabled = true,
      clock = { clockValue },
    )
  }
}

private object FakeAppEnv : AppEnv {
  override val appVersionName: String? = "1.0"
  override val appVersionCode: Int? = 1
  override val buildType: String? = "debug"
}

private class RecordingCrashReporter : CrashReporter {
  val recordedNonFatal = mutableListOf<AppErrorEvent>()
  val submitted = mutableListOf<Pair<AppErrorEvent, String?>>()
  val collectionFlags = mutableListOf<Boolean>()

  override fun setCollectionEnabled(enabled: Boolean) {
    collectionFlags.add(enabled)
  }

  override fun recordNonFatal(event: AppErrorEvent, appEnv: AppEnv) {
    recordedNonFatal.add(event)
  }

  override suspend fun submit(
    event: AppErrorEvent,
    comment: String?,
    appEnv: AppEnv,
    logCollector: LogCollector?,
  ) {
    submitted.add(event to comment)
  }
}
