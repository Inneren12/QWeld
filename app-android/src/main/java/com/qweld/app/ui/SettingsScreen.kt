package com.qweld.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qweld.app.R
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.vm.PracticeConfig
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
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val analyticsEnabled by userPrefs.analyticsEnabled.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_ANALYTICS_ENABLED,
  )
  val practiceSize by userPrefs.practiceSize.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_PRACTICE_SIZE,
  )
  val wrongBiased by userPrefs.wrongBiased.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_WRONG_BIASED,
  )
  val fallbackToEN by userPrefs.fallbackToEN.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_FALLBACK_TO_EN,
  )
  val hapticsEnabled by userPrefs.hapticsEnabled.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_HAPTICS_ENABLED,
  )
  val soundsEnabled by userPrefs.soundsEnabled.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_SOUNDS_ENABLED,
  )

  val resolvedPracticeConfig = PracticeConfig(practiceSize, wrongBiased)
  val practiceSummary = stringResource(id = R.string.settings_practice_size_summary, resolvedPracticeConfig.size)
  val practicePresets = PracticeConfig.PRESETS
  val clearedMessage = stringResource(id = R.string.settings_cleared_message)
  val clearedEnMessage = stringResource(id = R.string.settings_cleared_locale_message, "EN")
  val clearedRuMessage = stringResource(id = R.string.settings_cleared_locale_message, "RU")
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
        practiceSize = resolvedPracticeConfig.size,
        practicePresets = practicePresets,
        practiceSummary = practiceSummary,
        fallbackToEN = fallbackToEN,
        wrongBiased = wrongBiased,
        onFallbackToggle = { enabled ->
          scope.launch {
            userPrefs.setFallbackToEN(enabled)
            Timber.i("[settings_update] key=fallbackToEN value=%s", enabled)
          }
        },
        onPracticeSizeSelected = { size ->
          scope.launch {
            if (size != practiceSize) {
              userPrefs.setPracticeSize(size)
              Timber.i("[settings_update] key=practiceSize value=%d", size)
            }
          }
        },
        onWrongBiasedToggle = { enabled ->
          scope.launch {
            userPrefs.setWrongBiased(enabled)
            Timber.i("[settings_update] key=wrongBiased value=%s", enabled)
          }
        },
      )

      Divider()

      SettingsToolsSection(
        hapticsEnabled = hapticsEnabled,
        soundsEnabled = soundsEnabled,
        onToggleHaptics = { enabled ->
          scope.launch {
            userPrefs.setHapticsEnabled(enabled)
            Timber.i("[settings_update] key=hapticsEnabled value=%s", enabled)
          }
        },
        onToggleSounds = { enabled ->
          scope.launch {
            userPrefs.setSoundsEnabled(enabled)
            Timber.i("[settings_update] key=soundsEnabled value=%s", enabled)
          }
        },
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
        onClearLocaleEn = {
          scope.launch {
            runCatching { questionRepository.clearLocaleCache("en") }
              .onSuccess {
                Timber.i("[settings_action] action=clear_cache locale=en result=ok")
                snackbarHostState.showSnackbar(clearedEnMessage)
              }
              .onFailure { throwable ->
                Timber.e(throwable, "[settings_action] action=clear_cache locale=en result=error")
                snackbarHostState.showSnackbar(errorMessage)
              }
          }
        },
        onClearLocaleRu = {
          scope.launch {
            runCatching { questionRepository.clearLocaleCache("ru") }
              .onSuccess {
                Timber.i("[settings_action] action=clear_cache locale=ru result=ok")
                snackbarHostState.showSnackbar(clearedRuMessage)
              }
              .onFailure { throwable ->
                Timber.e(throwable, "[settings_action] action=clear_cache locale=ru result=error")
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
  practiceSize: Int,
  practicePresets: List<Int>,
  practiceSummary: String,
  fallbackToEN: Boolean,
  wrongBiased: Boolean,
  onFallbackToggle: (Boolean) -> Unit,
  onPracticeSizeSelected: (Int) -> Unit,
  onWrongBiasedToggle: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(text = stringResource(id = R.string.settings_section_practice), style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(text = stringResource(id = R.string.settings_practice_size_label), style = MaterialTheme.typography.bodyLarge)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        practicePresets.forEach { preset ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            RadioButton(
              selected = practiceSize == preset,
              onClick = { onPracticeSizeSelected(preset) },
            )
            Text(text = preset.toString(), style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
      Text(
        text = practiceSummary,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    SettingsToggleRow(
      label = stringResource(id = R.string.settings_wrong_biased_label),
      checked = wrongBiased,
      onCheckedChange = onWrongBiasedToggle,
    )
    Text(
      text = stringResource(id = R.string.settings_wrong_biased_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SettingsToggleRow(
      label = stringResource(id = R.string.settings_fallback_label),
      checked = fallbackToEN,
      onCheckedChange = onFallbackToggle,
    )
    Text(
      text = stringResource(id = R.string.settings_fallback_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SettingsToolsSection(
  hapticsEnabled: Boolean,
  soundsEnabled: Boolean,
  onToggleHaptics: (Boolean) -> Unit,
  onToggleSounds: (Boolean) -> Unit,
  onExportLogs: (() -> Unit)?,
  onClearAttempts: () -> Unit,
  onClearCache: () -> Unit,
  onClearLocaleEn: () -> Unit,
  onClearLocaleRu: () -> Unit,
  onExportClickLogged: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(text = stringResource(id = R.string.settings_section_tools), style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = stringResource(id = R.string.settings_section_accessibility),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      SettingsToggleRow(
        label = stringResource(id = R.string.settings_haptics_label),
        checked = hapticsEnabled,
        onCheckedChange = onToggleHaptics,
      )
      SettingsToggleRow(
        label = stringResource(id = R.string.settings_sounds_label),
        checked = soundsEnabled,
        onCheckedChange = onToggleSounds,
      )
    }
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
    Button(onClick = onClearLocaleEn, modifier = Modifier.fillMaxWidth()) {
      Text(text = stringResource(id = R.string.settings_clear_cache_en))
    }
    Button(onClick = onClearLocaleRu, modifier = Modifier.fillMaxWidth()) {
      Text(text = stringResource(id = R.string.settings_clear_cache_ru))
    }
  }
}

@Composable
private fun SettingsToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
