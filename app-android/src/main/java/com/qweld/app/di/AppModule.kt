package com.qweld.app.di

import android.content.Context
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.qweld.app.BuildConfig
import com.qweld.app.common.error.AppErrorHandler
import com.google.firebase.crashlytics.ktx.crashlytics
import com.qweld.app.error.AppErrorHandlerImpl
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.analytics.FirebaseAnalyticsImpl
import com.qweld.app.data.content.ContentIndexReader
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.dao.QueuedQuestionReportDao
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.DefaultAnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.repo.UserStatsRepositoryRoom
import com.qweld.app.data.reports.DefaultReportEnvironmentMetadataProvider
import com.qweld.app.data.reports.FirestoreQuestionReportRepository
import com.qweld.app.data.reports.QuestionReportRepository
import com.qweld.app.data.reports.RetryQueuedQuestionReportsUseCase
import com.qweld.app.common.di.IoDispatcher
import com.qweld.app.common.di.PrewarmDisabled
import com.qweld.app.error.CrashReporter
import com.qweld.app.error.CrashlyticsCrashReporter
import com.qweld.app.env.AppEnvImpl
import com.qweld.app.feature.auth.AuthService
import com.qweld.app.feature.auth.firebase.FirebaseAuthService
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.vm.PrewarmConfig
import com.qweld.core.common.AppEnv
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.google.firebase.crashlytics.ktx.crashlytics

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
  @IoDispatcher
  @Provides
  fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
  @Provides
  @Singleton
  fun provideAppEnv(): AppEnv = AppEnvImpl()

  @Provides
  @Singleton
  fun provideLogCollector(): LogCollector = LogCollector()

  @Provides
  @Singleton
  fun provideAnalytics(): Analytics = FirebaseAnalyticsImpl(Firebase.analytics, BuildConfig.ENABLE_ANALYTICS)

  @Provides
  @Singleton
  fun provideUserPrefs(@ApplicationContext context: Context): com.qweld.app.data.prefs.UserPrefs =
    UserPrefsDataStore(context)

  @Provides
  @Singleton
  fun providePrewarmConfig(): PrewarmConfig =
    PrewarmConfig(
      enabled = BuildConfig.PREWARM_ENABLED,
      maxConcurrency = BuildConfig.PREWARM_MAX_CONCURRENCY,
      taskTimeoutMs = BuildConfig.PREWARM_TIMEOUT_MS,
    )

  @Provides
  @PrewarmDisabled
  fun providePrewarmDisabledFlow(userPrefs: com.qweld.app.data.prefs.UserPrefs): Flow<Boolean> = userPrefs.prewarmDisabled

  @Provides
  @Singleton
  fun provideContentIndexReader(@ApplicationContext context: Context): ContentIndexReader = ContentIndexReader(context)

  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): QWeldDb = QWeldDb.create(context)

  @Provides
  fun provideAttemptDao(db: QWeldDb): AttemptDao = db.attemptDao()

  @Provides
  fun provideAnswerDao(db: QWeldDb): AnswerDao = db.answerDao()

  @Provides
  fun provideQueuedReportDao(db: QWeldDb): QueuedQuestionReportDao = db.queuedQuestionReportDao()

  @Provides
  @Singleton
  fun provideAttemptsRepository(attemptDao: AttemptDao): AttemptsRepository = AttemptsRepository(attemptDao)

  @Provides
  @Singleton
  fun provideAnswersRepository(answerDao: AnswerDao): AnswersRepository = DefaultAnswersRepository(answerDao)

  @Provides
  @Singleton
  fun provideUserStatsRepository(answerDao: AnswerDao): com.qweld.app.domain.exam.repo.UserStatsRepository =
    UserStatsRepositoryRoom(answerDao)

  @Provides
  @Singleton
  fun provideQuestionRepository(
    @ApplicationContext context: Context,
    userPrefs: com.qweld.app.data.prefs.UserPrefs,
  ): AssetQuestionRepository {
    val cacheSize = runBlocking { userPrefs.lruCacheSizeFlow().first() }
    return AssetQuestionRepository(context, cacheCapacity = cacheSize)
  }

  @Provides
  @Singleton
  fun provideExplanationRepository(@ApplicationContext context: Context): AssetExplanationRepository =
    AssetExplanationRepository(context)

  @Provides
  @Singleton
  fun provideCrashReporter(): CrashReporter? =
    CrashlyticsCrashReporter(Firebase.crashlytics.takeIf { BuildConfig.ENABLE_ANALYTICS })

  @Provides
  @Singleton
  fun provideAppErrorHandler(
    crashReporter: CrashReporter?,
    appEnv: AppEnv,
    logCollector: LogCollector,
  ): AppErrorHandler =
    AppErrorHandlerImpl(
      crashReporter = crashReporter,
      appEnv = appEnv,
      logCollector = logCollector,
      analyticsAllowedByBuild = BuildConfig.ENABLE_ANALYTICS,
      debugBehaviorEnabled = BuildConfig.DEBUG,
    )

  @Provides
  @Singleton
  fun provideReportEnvironment(appEnv: AppEnv): DefaultReportEnvironmentMetadataProvider =
    DefaultReportEnvironmentMetadataProvider(appEnv = appEnv)

  @Provides
  @Singleton
  fun provideQuestionReportRepository(
      queuedQuestionReportDao: QueuedQuestionReportDao,
      environmentMetadataProvider: DefaultReportEnvironmentMetadataProvider,
  ): QuestionReportRepository =
      FirestoreQuestionReportRepository(
          firestore = Firebase.firestore,
          queuedReportDao = queuedQuestionReportDao,
          environmentMetadataProvider = environmentMetadataProvider,
      )

  @Provides
  @Singleton
  fun provideRetryQueuedReportsUseCase(
    questionReportRepository: QuestionReportRepository,
  ): RetryQueuedQuestionReportsUseCase = RetryQueuedQuestionReportsUseCase(questionReportRepository)

  @Provides
  @Singleton
  fun provideAuthService(analytics: Analytics): AuthService =
    FirebaseAuthService(FirebaseAuth.getInstance(), analytics)
}
