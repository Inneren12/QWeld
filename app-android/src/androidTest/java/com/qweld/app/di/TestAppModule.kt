package com.qweld.app.di

import android.content.Context
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.qweld.app.BuildConfig
import com.qweld.app.common.di.PrewarmDisabled
import com.qweld.app.common.error.AppErrorHandler
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.analytics.FirebaseAnalyticsImpl
import com.qweld.app.data.content.ContentIndexReader
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.dao.QueuedQuestionReportDao
import com.qweld.app.data.export.AttemptExporter
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.repo.DefaultAnswersRepository
import com.qweld.app.data.repo.UserStatsRepositoryRoom
import com.qweld.app.data.reports.DefaultReportEnvironmentMetadataProvider
import com.qweld.app.data.reports.FirestoreQuestionReportRepository
import com.qweld.app.data.reports.QuestionReportRepository
import com.qweld.app.data.reports.RetryQueuedQuestionReportsUseCase
import com.qweld.app.env.AppEnvImpl
import com.qweld.app.error.AppErrorHandlerImpl
import com.qweld.app.error.CrashReporter
import com.qweld.app.error.CrashlyticsCrashReporter
import com.qweld.app.feature.auth.AuthResult
import com.qweld.app.feature.auth.AuthService
import com.qweld.app.feature.auth.AuthUser
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.vm.PrewarmConfig
import com.qweld.core.common.AppEnv
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.runCatching

/**
 * Test module providing fake implementations for app-android instrumentation tests.
 *
 * Replaces AppModule with test-friendly implementations. The only difference is
 * AuthService, which provides a fake authenticated user to bypass auth flows.
 * All other bindings are copied from AppModule to ensure compatibility.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {
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
  fun provideUserPrefsDataStore(@ApplicationContext context: Context): UserPrefsDataStore = UserPrefsDataStore(context)

  @Provides
  @Singleton
  fun provideUserPrefs(impl: UserPrefsDataStore): com.qweld.app.data.prefs.UserPrefs = impl

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
  fun providePrewarmDisabledFlow(userPrefs: UserPrefsDataStore): Flow<Boolean> = userPrefs.prewarmDisabled

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
  fun provideAttemptExporter(
    attemptsRepository: AttemptsRepository,
    answersRepository: AnswersRepository,
  ): AttemptExporter =
    AttemptExporter(
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      versionProvider = { BuildConfig.VERSION_NAME },
    )

  @Provides
  @Singleton
  fun provideUserStatsRepository(answerDao: AnswerDao): com.qweld.app.domain.exam.repo.UserStatsRepository =
    UserStatsRepositoryRoom(answerDao)

  @Provides
  @Singleton
  fun provideQuestionRepository(
    @ApplicationContext context: Context,
    userPrefs: UserPrefsDataStore,
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
  fun provideCrashReporter(): CrashReporter? {
    if (!BuildConfig.ENABLE_ANALYTICS) return null
    val crashlytics = runCatching { Firebase.crashlytics }.getOrNull() ?: return null
    return CrashlyticsCrashReporter(crashlytics)
  }

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

  // *** TEST OVERRIDE: Fake AuthService provides authenticated user ***
  @Provides
  @Singleton
  fun provideAuthService(): AuthService = FakeAuthService()
}

/**
 * Fake AuthService that provides a non-anonymous authenticated user for testing.
 */
private class FakeAuthService : AuthService {
  private val fakeUser = AuthUser(
    uid = "test-user-id",
    email = "test@example.com",
    isAnonymous = false
  )

  override val currentUser: Flow<AuthUser?> = flowOf(fakeUser)

  override suspend fun signInAnonymously(): AuthResult {
    return AuthResult.Success(fakeUser)
  }

  override suspend fun signInWithGoogle(idToken: String): AuthResult {
    return AuthResult.Success(fakeUser)
  }

  override suspend fun signInWithEmail(email: String, password: String): AuthResult {
    return AuthResult.Success(fakeUser)
  }

  override suspend fun linkAnonymousToGoogle(idToken: String): AuthResult {
    return AuthResult.Success(fakeUser)
  }

  override suspend fun signOut() {
    // No-op for tests
  }
}
