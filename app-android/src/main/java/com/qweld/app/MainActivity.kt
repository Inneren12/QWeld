package com.qweld.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.qweld.app.common.error.AppErrorHandler
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.content.ContentIndexReader
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.reports.RetryQueuedQuestionReportsUseCase
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.auth.AuthService
import com.qweld.app.feature.exam.data.AppRulesLoader
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.i18n.LocaleController
import com.qweld.app.navigation.AppNavGraph
import com.qweld.app.ui.theme.QWeldTheme
import com.qweld.core.common.AppEnv
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  companion object {
    private const val RULES_ASSET_PATH = "rules/welder_exam_2024.json"
  }

  @Inject lateinit var analytics: Analytics
  @Inject lateinit var userPrefs: UserPrefsDataStore
  @Inject lateinit var questionRepository: AssetQuestionRepository
  @Inject lateinit var explanationRepository: AssetExplanationRepository
  @Inject lateinit var contentIndexReader: ContentIndexReader
  @Inject lateinit var attemptsRepository: AttemptsRepository
  @Inject lateinit var answersRepository: AnswersRepository
  @Inject lateinit var statsRepository: UserStatsRepository
  @Inject lateinit var questionReportRepository: com.qweld.app.data.reports.QuestionReportRepository
  @Inject lateinit var retryQueuedReports: RetryQueuedQuestionReportsUseCase
  @Inject lateinit var authService: AuthService
  @Inject lateinit var logCollector: LogCollector
  @Inject lateinit var appErrorHandler: AppErrorHandler
  @Inject lateinit var appEnv: AppEnv

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.i("[ui] screen=Main | attrs=%s", "{\"start\":true}")

    // Apply locale synchronously before setContent to avoid Activity recreation during startup
    runBlocking {
      val currentLocale = userPrefs.appLocaleFlow().first()
      LocaleController.apply(currentLocale)
    }

    runCatching { AppRulesLoader(applicationContext).load(RULES_ASSET_PATH) }
      .onFailure { Timber.e(it, "[rules_load_error] path=%s", RULES_ASSET_PATH) }

    // Observe locale changes for runtime updates (user changing locale in settings)
    userPrefs
      .appLocaleFlow()
      .distinctUntilChanged()
      .onEach { tag -> LocaleController.apply(tag) }
      .launchIn(lifecycleScope)
    lifecycleScope.launch {
      userPrefs.analyticsEnabled.collect { enabled ->
        val analyticsAllowed = BuildConfig.ENABLE_ANALYTICS && enabled
        analytics.setEnabled(analyticsAllowed)
        appErrorHandler.updateAnalyticsEnabled(enabled)
      }
    }
    setContent {
      QWeldAppRoot(
        analytics = analytics,
        userPrefs = userPrefs,
        questionRepository = questionRepository,
        explanationRepository = explanationRepository,
        attemptsRepository = attemptsRepository,
        answersRepository = answersRepository,
        statsRepository = statsRepository,
        questionReportRepository = questionReportRepository,
        contentIndexReader = contentIndexReader,
        retryQueuedReports = retryQueuedReports,
        authService = authService,
        appEnv = appEnv,
        appErrorHandler = appErrorHandler,
        logCollector = logCollector,
      )
    }
  }
}

@Composable
fun QWeldAppRoot(
  analytics: Analytics,
  userPrefs: UserPrefsDataStore,
  questionRepository: AssetQuestionRepository,
  explanationRepository: AssetExplanationRepository,
  attemptsRepository: AttemptsRepository,
  answersRepository: AnswersRepository,
  statsRepository: UserStatsRepository,
  questionReportRepository: com.qweld.app.data.reports.QuestionReportRepository,
  contentIndexReader: ContentIndexReader,
  retryQueuedReports: RetryQueuedQuestionReportsUseCase,
  authService: AuthService,
  appEnv: AppEnv,
  appErrorHandler: AppErrorHandler,
  logCollector: LogCollector?,
) {
  val appLocale = userPrefs.appLocaleFlow().collectAsState(initial = UserPrefsDataStore.DEFAULT_APP_LOCALE)
  val appErrorReporting by appErrorHandler.analyticsEnabled.collectAsState()

  // REMOVED duplicate locale apply - handled in onCreate flow observer to avoid thrashing

  LaunchedEffect(appErrorReporting) {
    appErrorHandler.updateAnalyticsEnabled(appErrorReporting)
  }

  LaunchedEffect(retryQueuedReports) {
    retryQueuedReports()
  }

  QWeldTheme {
      AppNavGraph(
        authService = authService,
        questionRepository = questionRepository,
        explanationRepository = explanationRepository,
        attemptsRepository = attemptsRepository,
        answersRepository = answersRepository,
        statsRepository = statsRepository,
        questionReportRepository = questionReportRepository,
        analytics = analytics,
        logCollector = logCollector,
        userPrefs = userPrefs,
        contentIndexReader = contentIndexReader,
      appErrorHandler = appErrorHandler,
      appEnv = appEnv,
      errorReportingEnabled = appErrorReporting,
      appLocaleTag = appLocale.value,
    )
  }
}
