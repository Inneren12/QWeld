package com.qweld.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.analytics.FirebaseAnalyticsImpl
import com.qweld.app.data.content.ContentIndexReader
import com.qweld.app.data.content.ContentLocaleResolver
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.logging.LogCollectorOwner
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.repo.UserStatsRepositoryRoom
import com.qweld.app.feature.auth.firebase.FirebaseAuthService
import com.qweld.app.feature.exam.data.AppRulesLoader
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.core.i18n.LocaleController
import com.qweld.app.navigation.AppNavGraph
import com.qweld.app.ui.theme.QWeldTheme
import kotlinx.coroutines.flow.distinctUntilChanged
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

        // Crashlytics on/off вместе с флагом аналитики
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(BuildConfig.ENABLE_ANALYTICS)

        // Единое место применения локали во всём приложении.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastApplied: String? = null
                userPrefs
                    .appLocaleFlow()
                    .distinctUntilChanged()
                    .collect { tag ->
                        LocaleController.apply(tag) // идемпотентно; пересоздание сделает AppCompat


                    }
            }
        }

        setContent {
            QWeldAppRoot(
                analytics = analytics,
                userPrefs = userPrefs,
            )
        }
    }
}

/**
 * Корневой Compose дерева навигации/DI.
 * Держим на верхнем уровне файла (вне класса) — это нормально для Compose.
 */
@Composable
fun QWeldAppRoot(
    analytics: Analytics,
    userPrefs: UserPrefsDataStore,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

  val lruCacheSize by userPrefs.lruCacheSizeFlow()
    .collectAsState(initial = UserPrefsDataStore.DEFAULT_LRU_CACHE_SIZE)

  val questionRepository = remember(appContext, lruCacheSize) {
    AssetQuestionRepository(appContext, cacheCapacity = lruCacheSize)
  }
  val explanationRepository = remember(appContext) { AssetExplanationRepository(appContext) }
  val contentIndexReader = remember(appContext) { ContentIndexReader(appContext) }
  val appLocaleState =
    userPrefs.appLocaleFlow().collectAsState(initial = UserPrefsDataStore.DEFAULT_APP_LOCALE)
  val contentLocaleResolver =
    remember(appContext) {
      ContentLocaleResolver(
        contentIndexReader = contentIndexReader,
        appLocaleProvider = { appLocaleState.value },
        systemLocalesProvider = { currentSystemLocales(appContext) },
      )
    }
  val database = remember(appContext) { QWeldDb.create(appContext) }
    val attemptsRepository = remember(database) { AttemptsRepository(database.attemptDao()) }
    val answersRepository = remember(database) { AnswersRepository(database.answerDao()) }
    val statsRepository = remember(database) { UserStatsRepositoryRoom(database.answerDao()) }
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
      appVersion = BuildConfig.VERSION_NAME,
      analytics = analytics,
      logCollector = logCollector,
      userPrefs = userPrefs,
      contentIndexReader = contentIndexReader,
      contentLocaleResolver = contentLocaleResolver,
      appLocaleTag = appLocaleState.value,
    )
  }
}

private fun currentSystemLocales(context: android.content.Context): List<java.util.Locale> {
  val locales = mutableListOf<java.util.Locale>()
  val appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
  for (index in 0 until appLocales.size()) {
    appLocales[index]?.let { locales += it }
  }
  if (locales.isNotEmpty()) return locales
  val configLocales = context.resources.configuration.locales
  for (index in 0 until configLocales.size()) {
    configLocales[index]?.let { locales += it }
  }
  if (locales.isEmpty()) locales += java.util.Locale.getDefault()
  return locales
}
