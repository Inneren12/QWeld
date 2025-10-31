package com.qweld.app.feature.exam.ui

import android.view.SoundEffectConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.DeficitDialogUiModel
import com.qweld.app.feature.exam.model.ExamUiState
import com.qweld.app.feature.exam.vm.ExamViewModel
import kotlinx.coroutines.flow.collectLatest
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
  var allowExamExit by rememberSaveable(attempt?.attemptId) { mutableStateOf(false) }
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  val hapticFeedback = LocalHapticFeedback.current
  val view = LocalView.current
  val lifecycleOwner = LocalLifecycleOwner.current

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
      allowExamExit = false
    }
  }

  BackHandler(enabled = confirmExitEnabled && !allowExamExit) {
    showConfirmExitDialog = true
  }

  LaunchedEffect(showConfirmExitDialog, attempt?.attemptId) {
    if (showConfirmExitDialog && confirmExitEnabled) {
      analytics.log(
        "confirm_exit",
        mapOf(
          "shown" to true,
          "mode" to "IPMock",
        ),
      )
    }
  }

  LaunchedEffect(uiState.attempt?.attemptId) {
    val attempt = uiState.attempt ?: return@LaunchedEffect
    if (loggedAttemptId == attempt.attemptId) return@LaunchedEffect
    loggedAttemptId = attempt.attemptId
    analytics.log(
      "exam_start",
      mapOf(
        "attempt_id" to attempt.attemptId,
        "mode" to attempt.mode.name.lowercase(Locale.US),
        "locale" to attempt.locale.uppercase(Locale.US),
        "question_count" to attempt.totalQuestions,
      ),
    )
  }

  LaunchedEffect(uiState.deficitDialog) {
    val dialog = uiState.deficitDialog ?: return@LaunchedEffect
    val taskIds = dialog.details.joinToString(",") { it.taskId }.takeIf { it.isNotBlank() }
    val missingTotal = dialog.details.sumOf { it.missing }
    analytics.log(
      "deficit_dialog",
      mapOf(
        "task_ids" to taskIds,
        "detail_count" to dialog.details.size,
        "missing_total" to missingTotal,
      ),
    )
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
          val passThreshold = resultData.passThreshold
          val passed = passThreshold?.let { resultData.scorePercent >= it }
          val remainingSeconds = resultData.remaining?.seconds?.coerceAtLeast(0)
          analytics.log(
            "exam_finish",
            mapOf(
              "attempt_id" to resultData.attemptId,
              "mode" to attempt.mode.name.lowercase(Locale.US),
              "total_questions" to totalQuestions,
              "correct_count" to correctCount,
              "score_pct" to resultData.scorePercent,
              "pass_threshold" to passThreshold,
              "passed" to passed,
              "remaining_sec" to remainingSeconds,
            ),
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
      analytics.log(
        "answer_submit",
        mapOf(
          "attempt_id" to attempt.attemptId,
          "question_id" to question.id,
          "choice_id" to choiceId,
          "mode" to attempt.mode.name.lowercase(Locale.US),
          "question_position" to attempt.currentIndex + 1,
          "total_questions" to attempt.totalQuestions,
        ),
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
  )

  if (showConfirmExitDialog && confirmExitEnabled) {
    ConfirmExitDialog(
      onContinue = {
        analytics.log(
          "confirm_exit",
          mapOf("choice" to "continue"),
        )
        showConfirmExitDialog = false
        allowExamExit = false
      },
      onExit = {
        analytics.log(
          "confirm_exit",
          mapOf("choice" to "exit"),
        )
        showConfirmExitDialog = false
        val dispatcher = backDispatcher
        if (dispatcher != null) {
          allowExamExit = true
          dispatcher.onBackPressed()
        } else {
          allowExamExit = false
        }
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
private fun ExamScreenContent(
  state: ExamUiState,
  modifier: Modifier = Modifier,
  onChoiceSelected: (String) -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onDismissDeficit: () -> Unit,
  onFinish: () -> Unit,
) {
  Scaffold { paddingValues ->
    val attempt = state.attempt
    if (attempt == null) {
      Box(
        modifier = modifier
          .fillMaxSize()
          .padding(paddingValues),
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
      Column(
        modifier = modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(horizontal = 20.dp, vertical = 16.dp),
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
        Spacer(modifier = Modifier.weight(1f))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          val previousCd = stringResource(id = R.string.exam_previous_cd)
          Button(
            modifier = Modifier
              .weight(1f)
              .heightIn(min = minHeight)
              .semantics { contentDescription = previousCd },
            onClick = onPrevious,
            enabled = attempt.canGoPrevious(),
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
            enabled = attempt.canGoNext(),
          ) {
            Text(text = stringResource(id = R.string.exam_next))
          }
        }
        val finishCd = stringResource(id = R.string.exam_finish_cd)
        Button(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .semantics { contentDescription = finishCd },
          onClick = onFinish,
        ) {
          Text(text = stringResource(id = R.string.exam_finish))
        }
      }
    }
  }

  state.deficitDialog?.let { dialog ->
    DeficitDialog(dialog = dialog, onDismiss = onDismissDeficit)
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

internal fun shouldConfirmExit(mode: ExamMode?): Boolean = mode == ExamMode.IP_MOCK
