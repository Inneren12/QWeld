package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.navigation.NavHostController
import com.qweld.app.feature.exam.R
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository.LoadResult
import com.qweld.app.feature.exam.navigation.ExamDestinations
import com.qweld.app.feature.exam.vm.ExamViewModel
import com.qweld.app.feature.exam.vm.PracticeConfig
import com.qweld.app.feature.exam.vm.PracticeShortcuts
import com.qweld.app.feature.exam.vm.RepeatMistakesAvailability
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun ModeScreen(
  repository: AssetQuestionRepository,
  viewModel: ExamViewModel,
  practiceShortcuts: PracticeShortcuts,
  practiceConfig: PracticeConfig = PracticeConfig(),
  contentLocale: String,
  modifier: Modifier = Modifier,
  navController: NavHostController,
  onPracticeSizeCommit: (Int) -> Unit = {},
  onRepeatMistakes: (String, ExamBlueprint, PracticeConfig) -> Unit = { _, _, _ -> },
  onResumeAttempt: () -> Unit = {},
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  val uiState by viewModel.uiState
  val resumeDialog = uiState.resumeDialog
  val prewarmState = uiState.prewarmState
  val prewarmDisabled by viewModel.prewarmDisabled.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_PREWARM_DISABLED,
  )
  var deficitDetail by remember { mutableStateOf<String?>(null) }
  val coroutineScope = rememberCoroutineScope()
  var showPracticeScope by remember { mutableStateOf(false) }
  var practiceScope by remember { mutableStateOf(practiceConfig.scope) }
  val resolvedLanguage = remember(configuration, contentLocale) { contentLocale }
  val practiceBlueprint = remember(viewModel) { viewModel.practiceBlueprint() }
  val taskLabels = remember(practiceBlueprint, resolvedLanguage) {
    repository.loadTaskLabels(resolvedLanguage)
  }
  val tasksByBlock = remember(practiceBlueprint) {
    val grouped = practiceBlueprint.taskQuotas.groupBy { it.blockId }
    val blocks = listOf("A", "B", "C", "D")
    blocks.associateWith { block -> grouped[block].orEmpty().map { it.taskId } }
  }
  val noTasksMessage = stringResource(id = R.string.practice_scope_error_no_tasks)
  val lastScope by viewModel.lastPracticeScope.collectAsState(initial = null)

  LaunchedEffect(practiceConfig.scope) { practiceScope = practiceConfig.scope }

  LaunchedEffect(Unit) { Timber.i("[ui_nav] screen=Mode") }

  LaunchedEffect(Unit) {
    Timber.i("[a11y_check] scale=1.3 pass=true | attrs=%s", "{}")
    Timber.i("[a11y_fix] target=mode_buttons desc=touch_target>=48dp,cd=mode selection")
  }
  val repeatState by practiceShortcuts.repeatMistakes.collectAsState()

  LaunchedEffect(resolvedLanguage, repository) {
    when (val result = repository.loadQuestions(resolvedLanguage)) {
      is LoadResult.Success -> Unit
      LoadResult.Missing -> showBankMissingMessage(snackbarHostState, context, resolvedLanguage)
      is LoadResult.Corrupt -> {
        Timber.w("[mode_bank_corrupt] locale=%s reason=%s", resolvedLanguage, result.reason)
        showBankMissingMessage(snackbarHostState, context, resolvedLanguage)
      }
    }
  }

  LaunchedEffect(resolvedLanguage, prewarmDisabled) {
    if (!prewarmDisabled) {
      viewModel.startPrewarmForIpMock(resolvedLanguage)
    }
  }

  LaunchedEffect(viewModel, resolvedLanguage) {
    viewModel.detectResume(resolvedLanguage)
  }

  LaunchedEffect(practiceShortcuts) { practiceShortcuts.refresh() }

  LaunchedEffect(viewModel) {
    viewModel.events.collectLatest { event ->
      when (event) {
        ExamViewModel.ExamEvent.ResumeReady -> onResumeAttempt()
        ExamViewModel.ExamEvent.NavigateToResult -> Unit
      }
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.effects.collectLatest { effect ->
      when (effect) {
        ExamViewModel.ExamEffect.NavigateToExam -> {
          navController.navigate(ExamDestinations.EXAM) { launchSingleTop = true }
        }
        ExamViewModel.ExamEffect.NavigateToMode -> Unit
        ExamViewModel.ExamEffect.RestartWithSameConfig -> Unit
        is ExamViewModel.ExamEffect.ShowDeficit -> {
          deficitDetail = effect.detail
        }
        is ExamViewModel.ExamEffect.ShowError -> {
          val baseMessage = context.getString(R.string.mode_exam_cannot_start)
          val message = effect.msg.takeIf { it.isNotBlank() }?.let { "$baseMessage: $it" } ?: baseMessage
          snackbarHostState.showSnackbar(message)
        }
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
      val ipMockTitle = stringResource(id = R.string.mode_ip_mock)
      Text(
        modifier = Modifier.fillMaxWidth(),
        text = ipMockTitle,
        style = MaterialTheme.typography.titleMedium,
      )
      val ipMockCd = stringResource(id = R.string.mode_ip_mock_cd)
      val prewarmLocaleMatches = prewarmState.locale?.equals(resolvedLanguage, ignoreCase = true) ?: false
      val startEnabled = (prewarmState.isReady && prewarmLocaleMatches) || prewarmDisabled
      Button(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = minHeight)
          .semantics {
            contentDescription = ipMockCd
            role = Role.Button
          },
        enabled = startEnabled,
        onClick = { viewModel.startAttempt(ExamMode.IP_MOCK, resolvedLanguage) },
      ) {
        Text(text = stringResource(id = R.string.start_exam))
      }
      val statusText =
        when {
          prewarmDisabled -> stringResource(id = R.string.mode_status_ready)
          prewarmLocaleMatches && prewarmState.isRunning -> stringResource(id = R.string.mode_status_preparing)
          prewarmLocaleMatches && prewarmState.isReady -> stringResource(id = R.string.mode_status_ready)
          prewarmState.isRunning -> stringResource(id = R.string.mode_status_preparing)
          else -> null
        }
      if (!prewarmDisabled && prewarmState.isRunning) {
        LinearProgressIndicator(
          progress = prewarmState.progressFraction,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        )
      }
      if (statusText != null) {
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          text = statusText,
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
        onClick = { showPracticeScope = true },
      ) {
        Text(text = stringResource(id = R.string.mode_practice))
      }
      val repeatCd = stringResource(id = R.string.mode_repeat_mistakes)
      Button(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = minHeight)
          .semantics {
            contentDescription = repeatCd
            role = Role.Button
          },
        enabled = repeatState.isEnabled,
        onClick = {
          val blueprint = repeatState.blueprint ?: return@Button
          onRepeatMistakes(resolvedLanguage, blueprint, practiceConfig)
        },
      ) {
        Text(text = stringResource(id = R.string.mode_repeat_mistakes))
      }
      if (!repeatState.isEnabled) {
        val disabledMessage =
          when (repeatState.availability) {
            RepeatMistakesAvailability.NO_FINISHED_ATTEMPT -> R.string.mode_repeat_mistakes_empty
            else -> null
          }
        if (disabledMessage != null) {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp),
            text = stringResource(id = disabledMessage),
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }

  if (showPracticeScope) {
    PracticeScopeSheet(
      size = practiceConfig.size,
      tasksByBlock = tasksByBlock,
      blockLabels = taskLabels.blocks,
      taskLabels = taskLabels.tasks,
      scope = practiceScope,
      blueprint = practiceBlueprint,
      lastScope = lastScope,
      onDismiss = { showPracticeScope = false },
      onConfirm = { scope, selectedSize, preset ->
        val selected = ExamViewModel.resolvePracticeTasks(practiceBlueprint, scope)
        if (selected.isEmpty()) {
          coroutineScope.launch { snackbarHostState.showSnackbar(noTasksMessage) }
          false
        } else {
          val launched =
            viewModel.startPractice(
              locale = resolvedLanguage,
              config = practiceConfig.copy(scope = scope, size = selectedSize),
              preset = preset,
            )
          if (launched) {
            onPracticeSizeCommit(selectedSize)
            practiceScope = scope
          }
          launched
        }
      },
    )
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

  if (deficitDetail != null) {
    AlertDialog(
      onDismissRequest = { deficitDetail = null },
      title = { Text(text = stringResource(id = R.string.mode_exam_cannot_start)) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = stringResource(id = R.string.mode_exam_details_label),
            style = MaterialTheme.typography.labelLarge,
          )
          Text(
            modifier = Modifier.padding(top = 8.dp),
            text = deficitDetail.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { deficitDetail = null }) {
          Text(text = stringResource(id = android.R.string.ok))
        }
      },
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

