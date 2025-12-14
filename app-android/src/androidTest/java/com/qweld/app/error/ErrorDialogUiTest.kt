package com.qweld.app.error

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.qweld.app.R
import com.qweld.app.common.error.AppError
import com.qweld.app.common.error.AppErrorEvent
import com.qweld.app.common.error.ErrorContext
import com.qweld.core.common.AppEnv
import com.qweld.app.data.logging.LogCollector
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 34)
class ErrorDialogUiTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun dialog_showsAndSubmitsReport() {
    val crashReporter = RecordingCrashReporter()
    val handler =
      AppErrorHandlerImpl(
        crashReporter = crashReporter,
        appEnv = FakeAppEnv,
        logCollector = null,
        analyticsAllowedByBuild = true,
        debugBehaviorEnabled = false,
      )

    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) { ErrorDialogHost(appErrorHandler = handler) }
    }

    val context = ErrorContext(screen = "exam", action = "submit")
    composeTestRule.runOnUiThread {
      handler.handle(AppError.Unexpected(Exception("ui"), context, offerReportDialog = true))
    }

    val titleText = composeTestRule.activity.getString(R.string.app_error_report_title)
    val commentLabel = composeTestRule.activity.getString(R.string.app_error_report_comment_label)
    val submitText = composeTestRule.activity.getString(R.string.app_error_report_submit)

    composeTestRule.onNodeWithText(titleText).assertExists()
    composeTestRule.onNodeWithText(commentLabel, substring = true).performTextInput("ui comment")
    composeTestRule.onNodeWithText(submitText).performClick()

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText(titleText).assertDoesNotExist()
    composeTestRule.onNodeWithTag("error_report_result").assertExists()

    assertTrue(crashReporter.recordedNonFatal.isNotEmpty())
    assertEquals("ui comment", crashReporter.submitted.single().second)
  }
}

@Composable
private fun ErrorDialogHost(appErrorHandler: AppErrorHandlerImpl) {
  var pending by remember { mutableStateOf<com.qweld.app.common.error.AppErrorEvent?>(null) }
  var isSubmitting by remember { mutableStateOf(false) }
  var submittedLabel by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  androidx.compose.runtime.LaunchedEffect(appErrorHandler) {
    appErrorHandler.uiEvents.collect { event -> pending = event.event }
  }

  pending?.let { event ->
    com.qweld.app.ui.AppErrorReportDialog(
      onDismiss = { if (!isSubmitting) pending = null },
      onSubmit = { comment ->
        scope.launch {
          isSubmitting = true
          val result = appErrorHandler.submitReport(event, comment)
          isSubmitting = false
          pending = null
          submittedLabel = result::class.simpleName
        }
      },
      isSubmitting = isSubmitting,
    )
  }

  submittedLabel?.let { Text(text = it, modifier = Modifier.testTag("error_report_result")) }
}

private object FakeAppEnv : AppEnv {
  override val appVersionName: String? = "1.0"
  override val appVersionCode: Int? = 1
  override val buildType: String? = "debug"
}

private class RecordingCrashReporter : CrashReporter {
  val recordedNonFatal = mutableListOf<AppErrorEvent>()
  val submitted = mutableListOf<Pair<AppErrorEvent, String?>>()

  override fun setCollectionEnabled(enabled: Boolean) {}

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
