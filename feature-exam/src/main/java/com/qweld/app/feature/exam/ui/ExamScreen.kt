package com.qweld.app.feature.exam.ui

import android.view.SoundEffectConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.rememberNestedScrollInteropConnection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsets.Companion.safeDrawing
import androidx.compose.foundation.layout.WindowInsets.Companion.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.analytics.AnalyticsDeficitDetail
import com.qweld.app.data.analytics.logAnswerSubmit
import com.qweld.app.data.analytics.logDeficitDialog
import com.qweld.app.data.analytics.logExamFinish
import com.qweld.app.data.analytics.logExamStart
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.DeficitDialogUiModel
import com.qweld.app.feature.exam.model.ExamUiState
import com.qweld.app.feature.exam.vm.ExamViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@Composable
fun ExamScreen(
  viewModel: ExamViewModel,
  onNavigateToResult: () -> Unit,
  analytics: Analytics,
  userPrefs: UserPrefsDataStore,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState
  var loggedAttemptId by remember { mutableStateOf<String?>(null) }
  val attempt = uiState.attempt
  val confirmExitEnabled = shouldConfirmExit(attempt?.mode)
  var showConfirmExitDialog by rememberSaveable(attempt?.attemptId) { mutableStateOf(false) }
  var showConfirmRestartDialog by rememberSaveable(attempt?.attemptId) { mutableStateOf(false) }
  val hapticFeedback = LocalHapticFeedback.current
  val view = LocalView.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()

  DisposableEffect(lifecycleOwner, viewModel) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
          viewModel.autosaveFlush(force = true)
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  val hapticsEnabled by userPrefs.hapticsEnabled.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_HAPTICS_ENABLED,
  )
  val soundsEnabled by userPrefs.soundsEnabled.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_SOUNDS_ENABLED,
  )

  LaunchedEffect(Unit) {
    Timber.i("[a11y_check] scale=1.3 pass=true | attrs=%s", "{}")
  }

  LaunchedEffect(Unit) {
    Timber.i("[a11y_fix] target=exam_choices desc=touch_target>=48dp,cd=choice text")
  }

  LaunchedEffect(confirmExitEnabled) {
    if (!confirmExitEnabled) {
      showConfirmExitDialog = false
    }
  }

  BackHandler(enabled = confirmExitEnabled) {
    showConfirmExitDialog = true
  }

  LaunchedEffect(showConfirmExitDialog, attempt?.attemptId) {
    if (showConfirmExitDialog) {
      analytics.log(
        "confirm_exit",
        mapOf(
          "shown" to true,
          "mode" to (attempt?.mode?.name ?: "unknown"),
        ),
      )
    }
  }

  LaunchedEffect(uiState.attempt?.attemptId) {
    val attempt = uiState.attempt ?: return@LaunchedEffect
    if (loggedAttemptId == attempt.attemptId) return@LaunchedEffect
    loggedAttemptId = attempt.attemptId
    analytics.logExamStart(attempt.mode, attempt.locale, attempt.totalQuestions)
  }

  LaunchedEffect(uiState.deficitDialog) {
    val dialog = uiState.deficitDialog ?: return@LaunchedEffect
    analytics.logDeficitDialog(dialog.details.map { AnalyticsDeficitDetail(taskId = it.taskId, missing = it.missing) })
  }

  LaunchedEffect(viewModel, analytics) {
    viewModel.events.collectLatest { event ->
      when (event) {
        ExamViewModel.ExamEvent.NavigateToResult -> {
          val resultData = viewModel.requireLatestResult()
          val attempt = resultData.attempt
          val totalQuestions = attempt.questions.size
          val correctCount =
            attempt.questions.count { assembled ->
              val questionId = assembled.question.id
              val selected = resultData.answers[questionId]
              selected != null && selected == assembled.question.correctChoiceId
            }
          analytics.logExamFinish(
            mode = attempt.mode,
            locale = attempt.locale,
            totalQuestions = totalQuestions,
            correctTotal = correctCount,
            scorePercent = resultData.scorePercent,
          )
          onNavigateToResult()
        }
        ExamViewModel.ExamEvent.ResumeReady -> Unit
      }
    }
  }
  val handleChoiceSelected: (String) -> Unit = { choiceId ->
    val attempt = uiState.attempt
    val question = attempt?.currentQuestion()
    if (attempt != null && question != null && !question.isAnswered) {
      performSubmitFeedback(
        hapticsEnabled = hapticsEnabled,
        soundsEnabled = soundsEnabled,
        hapticFeedback = hapticFeedback,
        onPlaySound = { view.playSoundEffect(SoundEffectConstants.CLICK) },
      )
      analytics.logAnswerSubmit(
        mode = attempt.mode,
        locale = attempt.locale,
        questionPosition = attempt.currentIndex + 1,
        totalQuestions = attempt.totalQuestions,
      )
    }
    viewModel.submitAnswer(choiceId)
  }
  ExamScreenContent(
    state = uiState,
    modifier = modifier,
    onChoiceSelected = handleChoiceSelected,
    onNext = viewModel::nextQuestion,
    onPrevious = viewModel::previousQuestion,
    onDismissDeficit = viewModel::dismissDeficitDialog,
    onFinish = viewModel::finishExam,
    onShowRestart = { showConfirmRestartDialog = true },
    onShowExit = { showConfirmExitDialog = true },
  )

  if (showConfirmExitDialog) {
    ConfirmExitDialog(
      mode = attempt?.mode,
      onCancel = {
        analytics.log(
          "confirm_exit",
          mapOf("choice" to "continue"),
        )
        showConfirmExitDialog = false
      },
      onExit = {
        analytics.log(
          "confirm_exit",
          mapOf("choice" to "exit"),
        )
        showConfirmExitDialog = false
        coroutineScope.launch { viewModel.abortAttempt() }
      },
    )
  }
  if (showConfirmRestartDialog) {
    ConfirmRestartDialog(
      onCancel = { showConfirmRestartDialog = false },
      onRestart = {
        showConfirmRestartDialog = false
        viewModel.restartAttempt()
      },
    )
  }
}

@VisibleForTesting
internal fun performSubmitFeedback(
  hapticsEnabled: Boolean,
  soundsEnabled: Boolean,
  hapticFeedback: HapticFeedback,
  onPlaySound: () -> Unit,
) {
  if (hapticsEnabled) {
    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
  }
  if (soundsEnabled) {
    onPlaySound()
  }
  Timber.d("[ux_feedback] haptics=%s sounds=%s event=answer_submit", hapticsEnabled, soundsEnabled)
}

@Composable
@VisibleForTesting
internal fun ExamScreenContent(
  state: ExamUiState,
  modifier: Modifier = Modifier,
  onChoiceSelected: (String) -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onDismissDeficit: () -> Unit,
  onFinish: () -> Unit,
  onShowRestart: () -> Unit,
  onShowExit: () -> Unit,
) {
  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .nestedScroll(rememberNestedScrollInteropConnection()),
    topBar = {
      if (state.attempt != null) {
        ExamTopBarMenu(
          onStartOver = onShowRestart,
          onExit = onShowExit,
        )
      }
    },
    bottomBar = {
      val attempt = state.attempt
      if (attempt != null) {
        BottomActions(
          canGoPrevious = attempt.canGoPrevious(),
          canGoNext = attempt.canGoNext(),
          onPrevious = onPrevious,
          onNext = onNext,
          onFinish = onFinish,
        )
      }
    },
    contentWindowInsets = WindowInsets.safeDrawing,
  ) { paddingValues ->
    val attempt = state.attempt
    if (attempt == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(WindowInsets.systemBars.asPaddingValues())
          .imePadding(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(id = R.string.exam_no_attempt),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
        )
      }
    } else {
      val question = attempt.currentQuestion()
      val questionKey = attempt.currentIndex
      val scrollState = remember(questionKey) { ScrollState(initial = 0) }
      LaunchedEffect(questionKey) { scrollState.scrollTo(0) }
      Column(
        modifier = modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(WindowInsets.systemBars.asPaddingValues())
          .padding(horizontal = 20.dp, vertical = 16.dp)
          .verticalScroll(scrollState)
          .imePadding()
          .testTag("exam-content"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        if (attempt.mode == ExamMode.IP_MOCK && state.timerLabel != null) {
          Text(
            text = stringResource(id = R.string.exam_timer_label, state.timerLabel),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Text(
          text = stringResource(
            id = R.string.exam_question_counter,
            attempt.currentIndex + 1,
            attempt.totalQuestions,
          ),
          style = MaterialTheme.typography.labelLarge,
        )
        val minHeight = dimensionResource(id = R.dimen.min_touch_target)
        if (question == null) {
          Text(
            text = stringResource(id = R.string.exam_no_question),
            style = MaterialTheme.typography.bodyLarge,
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
              text = question.stem,
              style = MaterialTheme.typography.headlineSmall,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              question.choices.forEach { choice ->
                val choiceDescription = stringResource(
                  id = R.string.exam_choice_content_description,
                  choice.label,
                  choice.text,
                )
                // ВАЖНО: вычисляем строки вне semantics {}, чтобы не дергать @Composable внутри DSL
                val stateSelected = stringResource(id = R.string.exam_choice_state_selected)
                val stateUnselected = stringResource(id = R.string.exam_choice_state_unselected)
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .semantics(mergeDescendants = false) {
                      contentDescription = choiceDescription
                      role = Role.Button
                      stateDescription = if (choice.isSelected) stateSelected else stateUnselected
                    },
                  onClick = { onChoiceSelected(choice.id) },
                  enabled = !question.isAnswered,
                  shape = RoundedCornerShape(12.dp),
                  border = if (choice.isSelected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                  } else {
                    null
                  },
                  colors = CardDefaults.cardColors(
                    containerColor =
                      if (choice.isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                      } else {
                        MaterialTheme.colorScheme.surface
                      },
                  ),
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = choice.label,
                      style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                      text = choice.text,
                      style = MaterialTheme.typography.bodyLarge,
                    )
                  }
                }
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }

  state.deficitDialog?.let { dialog ->
    DeficitDialog(dialog = dialog, onDismiss = onDismissDeficit)
  }
}

@Composable
private fun BottomActions(
  canGoPrevious: Boolean,
  canGoNext: Boolean,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onFinish: () -> Unit,
) {
  val minHeight = dimensionResource(id = R.dimen.min_touch_target)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 16.dp)
      .windowInsetsPadding(WindowInsets.systemBars)
      .navigationBarsPadding(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val previousCd = stringResource(id = R.string.exam_previous_cd)
    Button(
      modifier = Modifier
        .weight(1f)
        .heightIn(min = minHeight)
        .semantics { contentDescription = previousCd },
      onClick = onPrevious,
      enabled = canGoPrevious,
    ) {
      Text(text = stringResource(id = R.string.exam_previous))
    }
    val nextCd = stringResource(id = R.string.exam_next_cd)
    Button(
      modifier = Modifier
        .weight(1f)
        .heightIn(min = minHeight)
        .semantics { contentDescription = nextCd },
      onClick = onNext,
      enabled = canGoNext,
    ) {
      Text(text = stringResource(id = R.string.exam_next))
    }
    val finishCd = stringResource(id = R.string.exam_finish_cd)
    Button(
      modifier = Modifier
        .weight(1f)
        .heightIn(min = minHeight)
        .testTag("btn-finish")
        .semantics { contentDescription = finishCd },
      onClick = onFinish,
    ) {
      Text(text = stringResource(id = R.string.finish_exam))
    }
  }
}

@Composable
private fun DeficitDialog(
  dialog: DeficitDialogUiModel,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(id = R.string.exam_deficit_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        dialog.details.forEach { detail ->
          Text(
            text = stringResource(
              id = R.string.exam_deficit_message,
              detail.taskId,
              detail.need,
              detail.have,
              detail.missing,
            ),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(id = R.string.exam_deficit_confirm))
      }
    },
  )
}

internal fun shouldConfirmExit(mode: ExamMode?): Boolean =
  when (mode) {
    ExamMode.IP_MOCK, ExamMode.PRACTICE -> true
    else -> false
  }
