package com.qweld.app.feature.exam.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

  LaunchedEffect(Unit) { Timber.i("[ui_nav] screen=Mode") }

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
      Button(modifier = Modifier.fillMaxWidth(), onClick = { onIpMockClick(resolvedLanguage) }) {
        Text(text = stringResource(id = R.string.mode_ip_mock))
      }
      Button(modifier = Modifier.fillMaxWidth(), onClick = { onPracticeClick(resolvedLanguage) }) {
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
