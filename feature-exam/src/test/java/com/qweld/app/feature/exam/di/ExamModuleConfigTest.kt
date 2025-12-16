package com.qweld.app.feature.exam.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.blueprint.BlueprintProvider
import com.qweld.app.feature.exam.vm.BlueprintResolver
import com.qweld.app.feature.exam.vm.PrewarmConfig
import com.qweld.app.feature.exam.vm.PrewarmController
import com.qweld.app.feature.exam.vm.PrewarmUseCase
import com.qweld.app.feature.exam.vm.ResumeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

/**
 * Tests for ExamModule DI configuration.
 *
 * These tests verify that all critical bindings in ExamModule can be
 * instantiated and that dependencies are properly wired for the exam feature.
 *
 * This is a regression test suite ensuring that after the ExamViewModel refactor
 * and DI introduction, all exam-specific dependencies remain properly configured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ExamModuleConfigTest {

  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `ExamModule provides BlueprintProvider`() {
    // When
    val provider = ExamModule.provideBlueprintProvider(context)

    // Then
    assertNotNull(provider, "BlueprintProvider should be provided")
  }

  @Test
  fun `ExamModule provides BlueprintResolver with proper dependencies`() {
    // Given
    val blueprintProvider = ExamModule.provideBlueprintProvider(context)

    // When
    val resolver = ExamModule.provideBlueprintResolver(blueprintProvider)

    // Then
    assertNotNull(resolver, "BlueprintResolver should be provided")
  }

  @Test
  fun `ExamModule provides TimerController`() {
    // When
    val timerController = ExamModule.provideTimerController()

    // Then
    assertNotNull(timerController, "TimerController should be provided")
  }

  @Test
  fun `ExamModule provides PrewarmUseCase with proper dependencies`() {
    // Given
    val userPrefs = UserPrefsDataStore(context)
    val repository = runBlocking {
      AssetQuestionRepository(context, cacheCapacity = userPrefs.lruCacheSizeFlow().kotlinx.coroutines.flow.first())
    }
    val prewarmDisabled = flowOf(false)
    val ioDispatcher = Dispatchers.IO
    val prewarmConfig = PrewarmConfig(enabled = true, maxConcurrency = 4, taskTimeoutMs = 5000L)

    // When
    val prewarmUseCase = ExamModule.providePrewarmUseCase(
      repository = repository,
      prewarmDisabled = prewarmDisabled,
      ioDispatcher = ioDispatcher,
      prewarmConfig = prewarmConfig
    )

    // Then
    assertNotNull(prewarmUseCase, "PrewarmUseCase should be provided")
  }

  @Test
  fun `ExamModule provides PrewarmController with proper dependencies`() {
    // Given
    val userPrefs = UserPrefsDataStore(context)
    val repository = runBlocking {
      AssetQuestionRepository(context, cacheCapacity = userPrefs.lruCacheSizeFlow().kotlinx.coroutines.flow.first())
    }
    val prewarmUseCase = ExamModule.providePrewarmUseCase(
      repository = repository,
      prewarmDisabled = flowOf(false),
      ioDispatcher = Dispatchers.IO,
      prewarmConfig = PrewarmConfig(enabled = true, maxConcurrency = 4, taskTimeoutMs = 5000L)
    )

    // When
    val prewarmController = ExamModule.providePrewarmController(repository, prewarmUseCase)

    // Then
    assertNotNull(prewarmController, "PrewarmController should be provided")
  }

  @Test
  fun `ExamModule provides ResumeUseCase with proper dependencies`() {
    // Given
    val userPrefs = UserPrefsDataStore(context)
    val repository = runBlocking {
      AssetQuestionRepository(context, cacheCapacity = userPrefs.lruCacheSizeFlow().kotlinx.coroutines.flow.first())
    }
    val db = QWeldDb.create(context)
    val statsRepository = com.qweld.app.data.repo.UserStatsRepositoryRoom(db.answerDao())
    val blueprintProvider = ExamModule.provideBlueprintProvider(context)
    val blueprintResolver = ExamModule.provideBlueprintResolver(blueprintProvider)
    val ioDispatcher = Dispatchers.IO

    // When
    val resumeUseCase = ExamModule.provideResumeUseCase(
      repository = repository,
      statsRepository = statsRepository,
      blueprintResolver = blueprintResolver,
      ioDispatcher = ioDispatcher
    )

    // Then
    assertNotNull(resumeUseCase, "ResumeUseCase should be provided")
  }

  @Test
  fun `ExamModule bindings form complete dependency graph for exam feature`() {
    // This test verifies that all ExamModule bindings can be created together,
    // ensuring there are no circular dependencies or missing transitive dependencies.

    // Given
    val blueprintProvider = ExamModule.provideBlueprintProvider(context)
    val blueprintResolver = ExamModule.provideBlueprintResolver(blueprintProvider)
    val timerController = ExamModule.provideTimerController()

    val userPrefs = UserPrefsDataStore(context)
    val repository = runBlocking {
      AssetQuestionRepository(context, cacheCapacity = userPrefs.lruCacheSizeFlow().kotlinx.coroutines.flow.first())
    }
    val prewarmConfig = PrewarmConfig(enabled = true, maxConcurrency = 4, taskTimeoutMs = 5000L)
    val prewarmDisabled = flowOf(false)
    val ioDispatcher = Dispatchers.IO

    val prewarmUseCase = ExamModule.providePrewarmUseCase(
      repository = repository,
      prewarmDisabled = prewarmDisabled,
      ioDispatcher = ioDispatcher,
      prewarmConfig = prewarmConfig
    )
    val prewarmController = ExamModule.providePrewarmController(repository, prewarmUseCase)

    val db = QWeldDb.create(context)
    val statsRepository = com.qweld.app.data.repo.UserStatsRepositoryRoom(db.answerDao())
    val resumeUseCase = ExamModule.provideResumeUseCase(
      repository = repository,
      statsRepository = statsRepository,
      blueprintResolver = blueprintResolver,
      ioDispatcher = ioDispatcher
    )

    // Then - all should be non-null, indicating complete graph
    assertNotNull(blueprintProvider)
    assertNotNull(blueprintResolver)
    assertNotNull(timerController)
    assertNotNull(prewarmUseCase)
    assertNotNull(prewarmController)
    assertNotNull(resumeUseCase)
  }
}
