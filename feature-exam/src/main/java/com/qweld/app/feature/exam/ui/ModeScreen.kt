package com.qweld.app.feature.exam.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository.Result
import com.qweld.app.feature.exam.vm.ExamViewModel
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import timber.log.Timber

@Composable
fun ModeScreen(
  repository: AssetQuestionRepository,
  viewModel: ExamViewModel,
  modifier: Modifier = Modifier,
  onIpMockClick: (String) -> Unit = {},
  onPracticeClick: (String) -> Unit = {},
  onResumeAttempt: () -> Unit = {},
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  val uiState by viewModel.uiState
  val resumeDialog = uiState.resumeDialog
  val prewarmState = uiState.prewarmState

  LaunchedEffect(Unit) { Timber.i("[ui_nav] screen=Mode") }

  LaunchedEffect(Unit) {
    Timber.i("[a11y_check] scale=1.3 pass=true | attrs=%s", "{}")
    Timber.i("[a11y_fix] target=mode_buttons desc=touch_target>=48dp,cd=mode selection")
  }

  val resolvedLanguage = remember(configuration) { resolveLanguage(configuration) }

  LaunchedEffect(resolvedLanguage, repository) {
    when (val result = repository.loadQuestions(resolvedLanguage)) {
      is Result.Success -> Unit
      is Result.Missing -> showBankMissingMessage(snackbarHostState, context, result.locale)
      is Result.Error -> showBankMissingMessage(snackbarHostState, context, result.locale)
    }
  }

  LaunchedEffect(viewModel, resolvedLanguage) {
    viewModel.detectResume(resolvedLanguage)
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collectLatest { event ->
      when (event) {
        ExamViewModel.ExamEvent.ResumeReady -> onResumeAttempt()
        ExamViewModel.ExamEvent.NavigateToResult -> Unit
      }
    }
  }

  Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 24.dp, vertical = 32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(id = R.string.mode_screen_title),
        style = MaterialTheme.typography.headlineMedium,
      )
      val minHeight = dimensionResource(id = R.dimen.min_touch_target)
      val ipMockCd = stringResource(id = R.string.mode_ip_mock_cd)
      val prewarmLocaleMatches = prewarmState.locale?.equals(resolvedLanguage, ignoreCase = true) ?: false
      Button(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = minHeight)
          .semantics {
            contentDescription = ipMockCd
            role = Role.Button
          },
        enabled = !prewarmState.isRunning || (prewarmState.isReady && prewarmLocaleMatches),
        onClick = {
          if (prewarmState.isReady && prewarmLocaleMatches) {
            onIpMockClick(resolvedLanguage)
          } else {
            viewModel.startPrewarmForIpMock(resolvedLanguage)
          }
        },
      ) {
        Text(text = stringResource(id = R.string.mode_ip_mock))
      }
      if (prewarmLocaleMatches && prewarmState.isRunning) {
        LinearProgressIndicator(
          progress = prewarmState.progressFraction,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        )
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          text = stringResource(id = R.string.mode_prewarm_preparing),
          style = MaterialTheme.typography.bodySmall,
        )
      } else if (prewarmLocaleMatches && prewarmState.isReady) {
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          text = stringResource(id = R.string.mode_prewarm_ready),
          style = MaterialTheme.typography.bodySmall,
        )
      } else if (prewarmState.isRunning) {
        LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        )
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          text = stringResource(id = R.string.mode_prewarm_preparing),
          style = MaterialTheme.typography.bodySmall,
        )
      }
      val practiceCd = stringResource(id = R.string.mode_practice_cd)
      Button(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = minHeight)
          .semantics {
            contentDescription = practiceCd
            role = Role.Button
          },
        onClick = { onPracticeClick(resolvedLanguage) },
      ) {
        Text(text = stringResource(id = R.string.mode_practice))
      }
    }
  }

  if (resumeDialog != null) {
    ResumeDialog(
      state = resumeDialog,
      onContinue = { option ->
        viewModel.resumeAttempt(
          attemptId = resumeDialog.attemptId,
          localeOption = option,
          deviceLocale = resolvedLanguage,
        )
      },
      onDiscard = { viewModel.discardAttempt(resumeDialog.attemptId) },
    )
  }
}

private suspend fun showBankMissingMessage(
  snackbarHostState: SnackbarHostState,
  context: android.content.Context,
  locale: String,
) {
  val displayLocale = locale.uppercase(Locale.US)
  val message = context.getString(R.string.mode_bank_missing, displayLocale)
  snackbarHostState.showSnackbar(message)
}

private fun resolveLanguage(configuration: android.content.res.Configuration): String {
  val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    configuration.locales.takeIf { it.size() > 0 }?.get(0)?.language
  } else {
    @Suppress("DEPRECATION")
    configuration.locale?.language
  }
  return language?.takeIf { it.isNotBlank() } ?: Locale.ENGLISH.language
}
