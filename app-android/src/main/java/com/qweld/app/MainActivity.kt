package com.qweld.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.analytics.FirebaseAnalyticsImpl
import com.qweld.app.data.content.ContentIndexReader
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.repo.UserStatsRepositoryRoom
import com.qweld.app.data.reports.FirestoreQuestionReportRepository
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.logging.LogCollectorOwner
import com.qweld.app.feature.exam.data.AppRulesLoader
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.auth.firebase.FirebaseAuthService
import com.qweld.app.i18n.LocaleController
import com.qweld.app.navigation.AppNavGraph
import com.qweld.app.ui.theme.QWeldTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
  companion object {
    private const val RULES_ASSET_PATH = "rules/welder_exam_2024.json"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.i("[ui] screen=Main | attrs=%s", "{\"start\":true}")
    runCatching { AppRulesLoader(applicationContext).load(RULES_ASSET_PATH) }
      .onFailure { Timber.e(it, "[rules_load_error] path=%s", RULES_ASSET_PATH) }
    val analytics = FirebaseAnalyticsImpl(Firebase.analytics, BuildConfig.ENABLE_ANALYTICS)
    val userPrefs = UserPrefsDataStore(applicationContext)
    userPrefs
      .appLocaleFlow()
      .distinctUntilChanged()
      .onEach { tag -> LocaleController.apply(tag) }
      .launchIn(lifecycleScope)
    lifecycleScope.launch {
      userPrefs.analyticsEnabled.collect { enabled -> analytics.setEnabled(enabled) }
    }
      Firebase.crashlytics.isCrashlyticsCollectionEnabled = BuildConfig.ENABLE_ANALYTICS
    setContent { QWeldAppRoot(analytics = analytics, userPrefs = userPrefs) }
  }
}

@Composable
fun QWeldAppRoot(
  analytics: Analytics,
  userPrefs: UserPrefsDataStore,
) {
  val context = LocalContext.current
  val appContext = context.applicationContext
  val lruCacheSize by userPrefs.lruCacheSizeFlow().collectAsState(
    initial = UserPrefsDataStore.DEFAULT_LRU_CACHE_SIZE,
  )
  val questionRepository = remember(appContext, lruCacheSize) {
    AssetQuestionRepository(appContext, cacheCapacity = lruCacheSize)
  }
  val explanationRepository = remember(appContext) { AssetExplanationRepository(appContext) }
  val contentIndexReader = remember(appContext) { ContentIndexReader(appContext) }
  val database = remember(appContext) { QWeldDb.create(appContext) }
  val attemptsRepository = remember(database) { AttemptsRepository(database.attemptDao()) }
  val answersRepository = remember(database) { AnswersRepository(database.answerDao()) }
  val statsRepository = remember(database) { UserStatsRepositoryRoom(database.answerDao()) }
  val questionReportRepository = remember { FirestoreQuestionReportRepository(Firebase.firestore) }
  val authService = remember { FirebaseAuthService(FirebaseAuth.getInstance(), analytics) }
  val logCollector: LogCollector? = remember(appContext) {
    (appContext as? LogCollectorOwner)?.logCollector
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
      appVersion = BuildConfig.VERSION_NAME,
      analytics = analytics,
      logCollector = logCollector,
      userPrefs = userPrefs,
      contentIndexReader = contentIndexReader,
    )
  }
}
