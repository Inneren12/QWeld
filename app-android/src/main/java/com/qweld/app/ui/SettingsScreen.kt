package com.qweld.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qweld.app.R
import com.qweld.app.data.content.ContentIndexReader
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.vm.PracticeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale

@Composable
fun SettingsScreen(
  userPrefs: UserPrefsDataStore,
  attemptsRepository: AttemptsRepository,
  answersRepository: AnswersRepository,
  questionRepository: AssetQuestionRepository,
  contentIndexReader: ContentIndexReader,
  onExportLogs: (() -> Unit)?,
  onBack: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val analyticsEnabled by userPrefs.analyticsEnabled.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_ANALYTICS_ENABLED,
  )
  val practiceSize by userPrefs.practiceSizeFlow().collectAsState(
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
  val prewarmDisabled by userPrefs.prewarmDisabled.collectAsState(
    initial = UserPrefsDataStore.DEFAULT_PREWARM_DISABLED,
  )
  val lruCacheSize by userPrefs.lruCacheSizeFlow().collectAsState(
    initial = UserPrefsDataStore.DEFAULT_LRU_CACHE_SIZE,
  )
  var contentIndex by remember { mutableStateOf<ContentIndexReader.Result?>(null) }
  val clipboardManager = LocalClipboardManager.current

  var lruCacheInput by remember(lruCacheSize) { mutableStateOf(lruCacheSize.toString()) }
  val lruCacheHint =
    stringResource(
      id = R.string.settings_lru_size_hint,
      UserPrefsDataStore.MIN_LRU_CACHE_SIZE,
      UserPrefsDataStore.MAX_LRU_CACHE_SIZE,
    )

  val resolvedPracticeConfig =
    PracticeConfig(
      size = PracticeConfig.sanitizeSize(practiceSize),
      wrongBiased = wrongBiased,
    )
  val practiceSummary = stringResource(id = R.string.settings_practice_size_summary, resolvedPracticeConfig.size)
  val practicePresets = PracticeConfig.PRESETS
  val clearedMessage = stringResource(id = R.string.settings_cleared_message)
  val clearedEnMessage = stringResource(id = R.string.settings_cleared_locale_message, "EN")
  val clearedRuMessage = stringResource(id = R.string.settings_cleared_locale_message, "RU")
  val errorMessage = stringResource(id = R.string.settings_error_message)
  val copySuccessMessage = stringResource(id = R.string.settings_content_copy_success)

  BackHandler(onBack = onBack)

  LaunchedEffect(Unit) { Timber.i("[settings_open]") }
  LaunchedEffect(contentIndexReader) {
    val indexResult = withContext(Dispatchers.IO) { contentIndexReader.read() }
    contentIndex = indexResult
    indexResult?.let { contentIndexReader.logContentInfo(it) }
  }

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

      SettingsContentInfoSection(
        contentIndex = contentIndex,
        onCopyIndex = {
          contentIndex?.let { result ->
            clipboardManager.setText(AnnotatedString(result.rawJson))
            scope.launch { snackbarHostState.showSnackbar(copySuccessMessage) }
          }
        },
      )

      Divider()

      SettingsToolsSection(
        hapticsEnabled = hapticsEnabled,
        soundsEnabled = soundsEnabled,
        prewarmEnabled = !prewarmDisabled,
        lruCacheSize = lruCacheInput,
        lruCacheHint = lruCacheHint,
        onTogglePrewarm = { enabled ->
          scope.launch {
            userPrefs.setPrewarmDisabled(!enabled)
            Timber.i("[settings_update] key=prewarmDisabled value=%s", !enabled)
          }
        },
        onLruCacheSizeChange = { text ->
          val digits = text.filter { it.isDigit() }
          lruCacheInput = digits
        },
        onLruCacheSizeCommit = {
          val parsed = lruCacheInput.toIntOrNull()
          if (parsed == null) {
            lruCacheInput = lruCacheSize.toString()
          } else {
            scope.launch {
              userPrefs.setLruCacheSize(parsed)
              Timber.i("[settings_update] key=lruCacheSize value=%d", parsed)
            }
          }
        },
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

@OptIn(ExperimentalLayoutApi::class)
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
private fun SettingsContentInfoSection(
  contentIndex: ContentIndexReader.Result?,
  onCopyIndex: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(text = stringResource(id = R.string.settings_section_content_info), style = MaterialTheme.typography.titleMedium)
    val locales = contentIndex?.locales?.toSortedMap()
    if (locales.isNullOrEmpty()) {
      Text(
        text = stringResource(id = R.string.settings_content_not_available),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      locales.forEach { (localeCode, localeInfo) ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(id = R.string.settings_content_locale_title, localeCode.uppercase(Locale.ROOT)),
            style = MaterialTheme.typography.bodyLarge,
          )
          localeInfo.blueprintId?.let { blueprint ->
            Text(
              text = stringResource(id = R.string.settings_content_blueprint, blueprint),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          localeInfo.bankVersion?.let { version ->
            Text(
              text = stringResource(id = R.string.settings_content_bank_version, version),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = stringResource(
              id = R.string.settings_content_files_count,
              localeInfo.filesCount,
              localeInfo.taskIds.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (localeInfo.hasBank) {
            Text(
              text = stringResource(id = R.string.settings_content_bank_present),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (localeInfo.hasTaskLabels) {
            Text(
              text = stringResource(id = R.string.settings_content_labels_present),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (localeInfo.taskIds.isNotEmpty()) {
            Text(
              text = stringResource(
                id = R.string.settings_content_tasks_list,
                localeInfo.taskIds.joinToString(separator = ", "),
              ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
    Button(onClick = onCopyIndex, enabled = !locales.isNullOrEmpty(), modifier = Modifier.fillMaxWidth()) {
      Text(text = stringResource(id = R.string.settings_content_copy_json))
    }
  }
}

@Composable
private fun SettingsToolsSection(
  hapticsEnabled: Boolean,
  soundsEnabled: Boolean,
  prewarmEnabled: Boolean,
  lruCacheSize: String,
  lruCacheHint: String,
  onTogglePrewarm: (Boolean) -> Unit,
  onLruCacheSizeChange: (String) -> Unit,
  onLruCacheSizeCommit: () -> Unit,
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = stringResource(id = R.string.settings_section_performance),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      SettingsToggleRow(
        label = stringResource(id = R.string.settings_prewarm_label),
        checked = prewarmEnabled,
        onCheckedChange = onTogglePrewarm,
      )
      OutlinedTextField(
        modifier = Modifier
          .fillMaxWidth()
          .onFocusChanged { focusState -> if (!focusState.isFocused) onLruCacheSizeCommit() },
        value = lruCacheSize,
        onValueChange = onLruCacheSizeChange,
        label = { Text(text = stringResource(id = R.string.settings_lru_size_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onLruCacheSizeCommit() }),
        singleLine = true,
      )
      Text(
        text = lruCacheHint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
