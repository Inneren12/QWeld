package com.qweld.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSnackbarHostState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qweld.app.R
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun SettingsScreen(
  userPrefs: UserPrefsDataStore,
  attemptsRepository: AttemptsRepository,
  answersRepository: AnswersRepository,
  questionRepository: AssetQuestionRepository,
  onExportLogs: (() -> Unit)?,
  onBack: () -> Unit,
) {
  val snackbarHostState = rememberSnackbarHostState()
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val analyticsEnabled by userPrefs.analyticsEnabled.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_ANALYTICS_ENABLED,
  )
  val practiceSize by userPrefs.practiceSize.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_PRACTICE_SIZE,
  )
  val fallbackToEN by userPrefs.fallbackToEN.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_FALLBACK_TO_EN,
  )

  var sliderValue by remember(practiceSize) { mutableStateOf(practiceSize.toFloat()) }
  var practiceInput by remember(practiceSize) { mutableStateOf(practiceSize.toString()) }
  val practiceLabel = stringResource(id = R.string.settings_practice_size_label)
  val practiceRange = 5f..50f
  val practiceStep = 5
  val clearedMessage = stringResource(id = R.string.settings_cleared_message)
  val errorMessage = stringResource(id = R.string.settings_error_message)

  BackHandler(onBack = onBack)

  LaunchedEffect(Unit) { Timber.i("[settings_open]") }

  Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      Text(
        text = stringResource(id = R.string.settings_title),
        style = MaterialTheme.typography.headlineMedium,
      )

      SettingsPrivacySection(
        analyticsEnabled = analyticsEnabled,
        onToggleAnalytics = { enabled ->
          scope.launch {
            userPrefs.setAnalyticsEnabled(enabled)
            Timber.i("[settings_toggle] key=analyticsEnabled value=%s", enabled)
          }
        },
      )

      Divider()

      SettingsPracticeSection(
        sliderValue = sliderValue,
        practiceInput = practiceInput,
        fallbackToEN = fallbackToEN,
        practiceLabel = practiceLabel,
        practiceRange = practiceRange,
        practiceStep = practiceStep,
        onSliderChange = { value ->
          val snapped = (value / practiceStep).roundToInt() * practiceStep
          sliderValue = snapped.toFloat().coerceIn(practiceRange.start, practiceRange.endInclusive)
          practiceInput = snapped.coerceIn(practiceRange.start.toInt(), practiceRange.endInclusive.toInt()).toString()
        },
        onSliderCommit = {
          val size = sliderValue.roundToInt().coerceIn(5, 50)
          scope.launch {
            if (size != practiceSize) {
              userPrefs.setPracticeSize(size)
              Timber.i("[settings_update] key=practiceSize value=%d", size)
            }
          }
        },
        onPracticeInputChange = { value ->
          val digits = value.filter { it.isDigit() }
          practiceInput = digits
          val parsed = digits.toIntOrNull()
          if (parsed != null) {
            val snapped = (parsed / practiceStep.toFloat()).roundToInt() * practiceStep
            sliderValue = snapped.toFloat().coerceIn(practiceRange.start, practiceRange.endInclusive)
          }
        },
        onPracticeInputCommit = {
          val parsed = practiceInput.toIntOrNull()
          if (parsed != null) {
            val snapped = (parsed / practiceStep.toFloat()).roundToInt() * practiceStep
            val size = snapped.coerceIn(practiceRange.start.toInt(), practiceRange.endInclusive.toInt())
            practiceInput = size.toString()
            sliderValue = size.toFloat()
            scope.launch {
              if (size != practiceSize) {
                userPrefs.setPracticeSize(size)
                Timber.i("[settings_update] key=practiceSize value=%d", size)
              }
            }
          } else {
            practiceInput = practiceSize.toString()
            sliderValue = practiceSize.toFloat()
          }
          focusManager.clearFocus()
        },
        onFallbackToggle = { enabled ->
          scope.launch {
            userPrefs.setFallbackToEN(enabled)
            Timber.i("[settings_update] key=fallbackToEN value=%s", enabled)
          }
        },
      )

      Divider()

      SettingsToolsSection(
        onExportLogs = onExportLogs,
        onClearAttempts = {
          scope.launch {
            runCatching {
              attemptsRepository.clearAll()
              answersRepository.clearAll()
            }
              .onSuccess {
                Timber.i("[settings_action] action=clear_attempts result=ok")
                snackbarHostState.showSnackbar(clearedMessage)
              }
              .onFailure { throwable ->
                Timber.e(throwable, "[settings_action] action=clear_attempts result=error")
                snackbarHostState.showSnackbar(errorMessage)
              }
          }
        },
        onClearCache = {
          scope.launch {
            runCatching { questionRepository.clearAllCaches() }
              .onSuccess {
                Timber.i("[settings_action] action=clear_cache result=ok")
                snackbarHostState.showSnackbar(clearedMessage)
              }
              .onFailure { throwable ->
                Timber.e(throwable, "[settings_action] action=clear_cache result=error")
                snackbarHostState.showSnackbar(errorMessage)
              }
          }
        },
        onExportClickLogged = {
          Timber.i("[settings_action] action=export_logs result=ok")
        },
      )
    }
  }
}

@Composable
private fun SettingsPrivacySection(
  analyticsEnabled: Boolean,
  onToggleAnalytics: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(text = stringResource(id = R.string.settings_section_privacy), style = MaterialTheme.typography.titleMedium)
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = stringResource(id = R.string.settings_analytics_label), style = MaterialTheme.typography.bodyLarge)
        Text(
          text = stringResource(id = R.string.settings_analytics_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = analyticsEnabled, onCheckedChange = onToggleAnalytics)
    }
  }
}

@Composable
private fun SettingsPracticeSection(
  sliderValue: Float,
  practiceInput: String,
  fallbackToEN: Boolean,
  practiceLabel: String,
  practiceRange: ClosedFloatingPointRange<Float>,
  practiceStep: Int,
  onSliderChange: (Float) -> Unit,
  onSliderCommit: () -> Unit,
  onPracticeInputChange: (String) -> Unit,
  onPracticeInputCommit: () -> Unit,
  onFallbackToggle: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(text = stringResource(id = R.string.settings_section_practice), style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(text = practiceLabel, style = MaterialTheme.typography.bodyLarge)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = practiceInput,
          onValueChange = onPracticeInputChange,
          modifier = Modifier.weight(1f),
          label = { Text(text = practiceLabel) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { onPracticeInputCommit() }),
        )
        TextButton(onClick = onPracticeInputCommit) { Text(text = stringResource(id = R.string.settings_apply)) }
      }
      androidx.compose.material3.Slider(
        value = sliderValue,
        onValueChange = onSliderChange,
        valueRange = practiceRange,
        steps = ((practiceRange.endInclusive - practiceRange.start) / practiceStep - 1).toInt(),
        onValueChangeFinished = onSliderCommit,
      )
      Text(text = stringResource(id = R.string.settings_practice_size_summary, sliderValue.roundToInt()))
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(id = R.string.settings_fallback_label),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f),
      )
      Switch(checked = fallbackToEN, onCheckedChange = onFallbackToggle)
    }
    Text(
      text = stringResource(id = R.string.settings_fallback_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SettingsToolsSection(
  onExportLogs: (() -> Unit)?,
  onClearAttempts: () -> Unit,
  onClearCache: () -> Unit,
  onExportClickLogged: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(text = stringResource(id = R.string.settings_section_tools), style = MaterialTheme.typography.titleMedium)
    Button(
      onClick = {
        onExportClickLogged()
        onExportLogs?.invoke()
      },
      enabled = onExportLogs != null,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = stringResource(id = R.string.settings_export_logs))
    }
    Button(onClick = onClearAttempts, modifier = Modifier.fillMaxWidth()) {
      Text(text = stringResource(id = R.string.settings_clear_attempts))
    }
    Button(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
      Text(text = stringResource(id = R.string.settings_clear_cache))
    }
  }
}