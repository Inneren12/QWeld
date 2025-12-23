package com.qweld.app.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.BuildConfig
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for AppModule DI configuration.
 *
 * These tests verify that all critical bindings in AppModule can be
 * instantiated and that dependencies are properly wired without causing
 * runtime errors like missing bindings or circular dependencies.
 *
 * This is a regression test suite ensuring that after DI introduction,
 * the configuration remains valid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class AppModuleConfigTest {

  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `AppModule provides AppEnv`() {
    // When
    val appEnv = AppModule.provideAppEnv()

    // Then
      assertNotNull("AppEnv should be provided", appEnv)
  }

  @Test
  fun `AppModule provides UserPrefsDataStore`() {
    // When
    val userPrefs = AppModule.provideUserPrefsDataStore(context)

    // Then
    assertNotNull( "UserPrefsDataStore should be provided",userPrefs)
  }

  @Test
  fun `AppModule provides QWeldDb`() {
    // When
    val db = AppModule.provideDatabase(context)

    // Then
    assertNotNull( "QWeldDb should be provided", db)
  }

  @Test
  fun `AppModule provides AttemptDao from database`() {
    // Given
    val db = AppModule.provideDatabase(context)

    // When
    val dao = AppModule.provideAttemptDao(db)

    // Then
    assertNotNull( "AttemptDao should be provided from database", dao)
  }

  @Test
  fun `AppModule provides AnswerDao from database`() {
    // Given
    val db = AppModule.provideDatabase(context)

    // When
    val dao = AppModule.provideAnswerDao(db)

    // Then
    assertNotNull( "AnswerDao should be provided from database", dao)
  }

  @Test
  fun `AppModule provides QueuedReportDao from database`() {
    // Given
    val db = AppModule.provideDatabase(context)

    // When
    val dao = AppModule.provideQueuedReportDao(db)

    // Then
    assertNotNull( "QueuedQuestionReportDao should be provided from database", dao)
  }

  @Test
  fun `AppModule provides AttemptsRepository with proper dependencies`() {
    // Given
    val db = AppModule.provideDatabase(context)
    val attemptDao = AppModule.provideAttemptDao(db)

    // When
    val repository = AppModule.provideAttemptsRepository(attemptDao)

    // Then
    assertNotNull( "AttemptsRepository should be provided", repository)
  }

  @Test
  fun `AppModule provides AnswersRepository with proper dependencies`() {
    // Given
    val db = AppModule.provideDatabase(context)
    val answerDao = AppModule.provideAnswerDao(db)

    // When
    val repository = AppModule.provideAnswersRepository(answerDao)

    // Then
    assertNotNull( "AnswersRepository should be provided", repository)
  }

  @Test
  fun `AppModule provides AttemptExporter with proper dependencies`() {
    // Given
    val db = AppModule.provideDatabase(context)
    val attemptDao = AppModule.provideAttemptDao(db)
    val answerDao = AppModule.provideAnswerDao(db)
    val attemptsRepository = AppModule.provideAttemptsRepository(attemptDao)
    val answersRepository = AppModule.provideAnswersRepository(answerDao)

    // When
    val exporter = AppModule.provideAttemptExporter(attemptsRepository, answersRepository)

    // Then
    assertNotNull("AttemptExporter should be provided", exporter)
  }

  @Test
  fun `AppModule provides UserStatsRepository with proper dependencies`() {
    // Given
    val db = AppModule.provideDatabase(context)
    val answerDao = AppModule.provideAnswerDao(db)

    // When
    val repository = AppModule.provideUserStatsRepository(answerDao)

    // Then
    assertNotNull( "UserStatsRepository should be provided", repository)
  }

  @Test
  fun `AppModule provides AssetQuestionRepository with proper dependencies`() {
    // Given
    val userPrefs = AppModule.provideUserPrefsDataStore(context)

    // When
    val repository = AppModule.provideQuestionRepository(context, userPrefs)

    // Then
    assertNotNull( "AssetQuestionRepository should be provided", repository)
  }

  @Test
  fun `AppModule provides AssetExplanationRepository`() {
    // When
    val repository = AppModule.provideExplanationRepository(context)

    // Then
    assertNotNull( "AssetExplanationRepository should be provided", repository)
  }

  @Test
  fun `AppModule provides AppErrorHandler with proper dependencies`() {
    // Given
      val crashReporter = null
    val appEnv = AppModule.provideAppEnv()
    val logCollector = AppModule.provideLogCollector()

    // When
    val errorHandler = AppModule.provideAppErrorHandler(crashReporter, appEnv, logCollector)

    // Then
    assertNotNull( "AppErrorHandler should be provided", errorHandler)
  }

  @Test
  fun `AppModule provides PrewarmConfig with build flags`() {
    // When
    val config = AppModule.providePrewarmConfig()

    // Then
    assertNotNull( "PrewarmConfig should be provided", config)
    assert(config.enabled == BuildConfig.PREWARM_ENABLED) {
      "PrewarmConfig.enabled should match BuildConfig.PREWARM_ENABLED"
    }
  }

  @Test
  fun `AppModule provides prewarm disabled flow from UserPrefsDataStore`() {
    // Given
    val userPrefs = AppModule.provideUserPrefsDataStore(context)

    // When
    val flow = AppModule.providePrewarmDisabledFlow(userPrefs)

    // Then
    assertNotNull( "Prewarm disabled Flow should be provided", flow)
  }

  @Test
  fun `DispatcherModule provides IO dispatcher`() {
    // When
    val dispatcher = DispatcherModule.provideIoDispatcher()

    // Then
    assertNotNull( "IO dispatcher should be provided", dispatcher)
  }
}
