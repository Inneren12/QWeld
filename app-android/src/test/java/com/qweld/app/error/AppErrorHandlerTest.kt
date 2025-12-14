package com.qweld.app.error

import com.qweld.app.common.error.AppError
import com.qweld.app.common.error.ErrorContext
import com.qweld.app.common.error.UiErrorEvent
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.error.CrashReporter
import com.qweld.app.error.AppErrorEvent
import com.qweld.core.common.AppEnv
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class AppErrorHandlerTest {
  private lateinit var logTree: RecordingTree

  @BeforeTest
  fun setUp() {
    Timber.uprootAll()
    logTree = RecordingTree()
    Timber.plant(logTree)
  }

  @AfterTest
  fun tearDown() {
    Timber.uprootAll()
  }

  @Test
  fun handleUnexpected_logsEvent_andForwardsToCrashReporter() {
    val crashReporter = RecordingCrashReporter()
    val handler = createHandler(crashReporter = crashReporter, analyticsAllowedByBuild = true)
    val error =
      AppError.Unexpected(
        cause = IllegalStateException("boom"),
        context = ErrorContext(screen = "home", action = "tap"),
        offerReportDialog = true,
      )

    handler.handle(error)

    val history = handler.history.value
    assertEquals(1, history.size)
    assertEquals(error, history.first().error)
    assertTrue(crashReporter.recordedNonFatal.isNotEmpty())
    assertTrue(crashReporter.collectionFlags.first())
    assertTrue(logTree.messages.any { it.contains("[app_error]") && it.contains("screen=home") })
    val uiEvent = runBlocking { handler.uiEvents.first() }
    assertEquals(error, uiEvent.event.error)
  }

  @Test
  fun handleUnexpected_skipsCrashReporterWhenAnalyticsDisabled() {
    val crashReporter = RecordingCrashReporter()
    val handler = createHandler(crashReporter = crashReporter, analyticsAllowedByBuild = true)
    handler.updateAnalyticsEnabled(userOptIn = false)

    handler.handle(
      AppError.Unexpected(
        cause = IllegalStateException("disabled"),
        context = ErrorContext(screen = "home"),
      ),
    )

    assertTrue(crashReporter.recordedNonFatal.isEmpty())
    assertTrue(handler.history.value.isNotEmpty())
  }

  @Test
  fun emitsUiEventsOnlyWhenDialogOffered() {
    val handler = createHandler(crashReporter = null, analyticsAllowedByBuild = false)

    handler.handle(
      AppError.Notice(
        message = "toast",
        context = ErrorContext(screen = "settings"),
        offerReportDialog = false,
      ),
    )

    val eventWhenOffered: UiErrorEvent? =
      runBlocking {
        handler.handle(
          AppError.Notice(
            message = "show", context = ErrorContext(screen = "settings"), offerReportDialog = true)
        )
        handler.uiEvents.first()
      }

    assertNull(logTree.messages.firstOrNull { it.contains("app_error_notice") && it.contains("toast") })
    assertNotNull(eventWhenOffered)
    assertEquals("settings", eventWhenOffered.event.error.context.screen)
  }

  private fun createHandler(
    crashReporter: RecordingCrashReporter?,
    analyticsAllowedByBuild: Boolean,
  ): AppErrorHandlerImpl {
    return AppErrorHandlerImpl(
      crashReporter = crashReporter,
      appEnv = FakeAppEnv,
      logCollector = null,
      analyticsAllowedByBuild = analyticsAllowedByBuild,
      debugBehaviorEnabled = true,
      clock = { 123L },
    )
  }
}

private object FakeAppEnv : AppEnv {
  override val appVersionName: String? = "1.0"
  override val appVersionCode: Int? = 1
  override val buildType: String? = "debug"
}

private class RecordingCrashReporter : CrashReporter {
  val recordedNonFatal = CopyOnWriteArrayList<AppErrorEvent>()
  val submitted = CopyOnWriteArrayList<Pair<AppErrorEvent, String?>>()
  val collectionFlags = CopyOnWriteArrayList<Boolean>()

  override fun setCollectionEnabled(enabled: Boolean) {
    collectionFlags.add(enabled)
  }

  override fun recordNonFatal(event: AppErrorEvent, appEnv: AppEnv) {
    recordedNonFatal.add(event)
  }

  override suspend fun submit(event: AppErrorEvent, comment: String?, appEnv: AppEnv, logCollector: LogCollector?) {
    submitted.add(event to comment)
  }
}

private class RecordingTree : Timber.Tree() {
  val messages = CopyOnWriteArrayList<String>()

  override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    messages.add(message)
  }
}
