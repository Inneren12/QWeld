package com.qweld.app

import com.qweld.app.common.error.AppError
import com.qweld.app.common.error.AppErrorEvent
import com.qweld.app.common.error.ErrorContext
import com.qweld.app.data.analytics.AnalyticsBackend
import com.qweld.app.data.analytics.FirebaseAnalyticsImpl
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.error.AppErrorHandlerImpl
import com.qweld.app.error.CrashReporter
import com.qweld.core.common.AppEnv
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryGateTest {
  @Test
  fun `TelemetryGateTest_optOut_disablesTelemetry`() = runBlocking {
    val analyticsBackend = RecordingBackend()
    val analytics = FirebaseAnalyticsImpl(backend = analyticsBackend, isEnabled = true)

    analytics.setEnabled(false)
    analytics.log("practice_start")

    val crashReporter = RecordingCrashReporter()
    val handler = AppErrorHandlerImpl(
      crashReporter = crashReporter,
      appEnv = FakeAppEnv,
      logCollector = null,
      analyticsAllowedByBuild = true,
      debugBehaviorEnabled = false,
      clock = { 0L },
    )

    handler.updateAnalyticsEnabled(userOptIn = false)
    handler.handle(
      AppError.Unexpected(
        cause = IllegalStateException("failure"),
        context = ErrorContext(screen = "home"),
      ),
    )

    assertTrue("analytics events should be skipped", analyticsBackend.events.isEmpty())
    assertTrue("crash reporter should be idle", crashReporter.recordedNonFatal.isEmpty())
  }
}

private object FakeAppEnv : AppEnv {
  override val appVersionName: String? = "1.0"
  override val appVersionCode: Int? = 1
  override val buildType: String? = "release"
}

private class RecordingBackend : AnalyticsBackend {
  val events = mutableListOf<Pair<String, Map<String, Any?>>>()

  override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
    // no-op for tests
  }

  override fun logEvent(event: String, params: Map<String, Any?>) {
    events += event to params
  }
}

private class RecordingCrashReporter : CrashReporter {
  val recordedNonFatal = mutableListOf<AppErrorEvent>()

  override fun setCollectionEnabled(enabled: Boolean) {
    // no-op for tests
  }

  override fun recordNonFatal(event: AppErrorEvent, appEnv: AppEnv) {
    recordedNonFatal += event
  }

  override suspend fun submit(
    event: AppErrorEvent,
    comment: String?,
    appEnv: AppEnv,
    logCollector: LogCollector?,
  ) {
    // unused in this test
  }
}
