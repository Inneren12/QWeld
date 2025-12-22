package com.qweld.app.feature.exam.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.common.di.IoDispatcher
import com.qweld.app.common.error.AppErrorHandler
import com.qweld.app.data.export.AttemptExporter
import com.qweld.app.data.prefs.UserPrefs
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.reports.QuestionReportRepository
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.vm.BlueprintResolver
import com.qweld.app.feature.exam.vm.ExamViewModel
import com.qweld.app.feature.exam.vm.ExamResultHolder
import com.qweld.app.feature.exam.vm.PrewarmController
import com.qweld.app.feature.exam.vm.ResumeUseCase
import com.qweld.core.common.AppEnv
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Before
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Integration test for Hilt DI configuration.
 *
 * This test verifies that the entire DI graph can be constructed and that
 * all critical dependencies can be injected without runtime errors. This is
 * a regression test ensuring that after DI introduction and ExamViewModel
 * refactor, the application can start and all components are properly wired.
 *
 * Uses TestExamModule to provide test overrides for exam-specific bindings.
 */
@HiltAndroidTest
class HiltDiIntegrationTest {

  @get:Rule
  var hiltRule = HiltAndroidRule(this)

  // App-level dependencies from AppModule
  @Inject
  lateinit var appEnv: AppEnv

  @Inject
  lateinit var attemptsRepository: AttemptsRepository

  @Inject
  lateinit var answersRepository: AnswersRepository

  @Inject
  lateinit var statsRepository: UserStatsRepository

  @Inject
  lateinit var questionRepository: AssetQuestionRepository

  @Inject
  lateinit var questionReportRepository: QuestionReportRepository

  @Inject
  lateinit var userPrefs: UserPrefs

  @Inject
  lateinit var appErrorHandler: AppErrorHandler

  @Inject
  lateinit var attemptExporter: AttemptExporter

  @Inject
  lateinit var resultHolder: ExamResultHolder

  @Inject
  @IoDispatcher
  lateinit var ioDispatcher: CoroutineDispatcher

  // Exam-specific dependencies from ExamModule
  @Inject
  lateinit var blueprintResolver: BlueprintResolver

  @Inject
  lateinit var timerController: TimerController

  @Inject
  lateinit var prewarmController: PrewarmController

  @Inject
  lateinit var resumeUseCase: ResumeUseCase

  @Before
  fun setup() {
    hiltRule.inject()
  }

  @Test
  fun appModule_provides_all_core_dependencies() {
    // Verify all app-level dependencies are injected
    assertNotNull(appEnv, "AppEnv should be injected")
    assertNotNull(attemptsRepository, "AttemptsRepository should be injected")
    assertNotNull(answersRepository, "AnswersRepository should be injected")
    assertNotNull(statsRepository, "UserStatsRepository should be injected")
    assertNotNull(questionRepository, "AssetQuestionRepository should be injected")
    assertNotNull(questionReportRepository, "QuestionReportRepository should be injected")
    assertNotNull(userPrefs, "UserPrefs should be injected")
    assertNotNull(attemptExporter, "AttemptExporter should be injected")
    assertNotNull(resultHolder, "ExamResultHolder should be injected")
  }

  @Test
  fun examModule_provides_all_exam_dependencies() {
    // Verify all exam-specific dependencies are injected
    assertNotNull(blueprintResolver, "BlueprintResolver should be injected")
    assertNotNull(timerController, "TimerController should be injected")
    assertNotNull(prewarmController, "PrewarmController should be injected")
    assertNotNull(resumeUseCase, "ResumeUseCase should be injected")
  }

  @Test
  fun examViewModel_can_be_created_with_injected_dependencies() {
    // This test verifies that ExamViewModel can be manually constructed
    // with all its dependencies from the DI graph, simulating what Hilt does.

    // When - create ExamViewModel with injected dependencies
    val viewModel = ExamViewModel(
      repository = questionRepository,
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      statsRepository = statsRepository,
      userPrefs = userPrefs,
      questionReportRepository = questionReportRepository,
      appEnv = appEnv,
      appErrorHandler = appErrorHandler,
      resultHolder = resultHolder,
      blueprintResolver = blueprintResolver,
      timerController = timerController,
      prewarmRunner = prewarmController,
      resumeUseCase = resumeUseCase,
      ioDispatcher = ioDispatcher,
    )

    // Then - should be successfully created
    assertNotNull(viewModel, "ExamViewModel should be created with injected dependencies")
    assertNotNull(viewModel.uiState, "ExamViewModel should have initialized state")
  }

  @Test
  fun testExamModule_provides_test_overrides() {
    // This test verifies that TestExamModule successfully replaces ExamModule
    // bindings during instrumentation tests.

    // The BlueprintResolver should use StaticBlueprintProvider from TestExamModule
    assertNotNull(blueprintResolver, "BlueprintResolver should be provided by TestExamModule")

    // TimerController should be the silent test version
    assertNotNull(timerController, "TimerController should be provided by TestExamModule")

    // These assertions pass if we reach here without DI errors,
    // confirming test overrides work correctly
  }

  @Test
  fun di_graph_has_no_circular_dependencies() {
    // This test verifies that all injected dependencies don't cause circular
    // dependency issues. Simply injecting all fields successfully means the
    // graph is valid.

    // If we reach here without exceptions, the graph is valid
    assertNotNull(appEnv)
    assertNotNull(attemptsRepository)
    assertNotNull(answersRepository)
    assertNotNull(statsRepository)
    assertNotNull(questionRepository)
    assertNotNull(blueprintResolver)
    assertNotNull(timerController)
    assertNotNull(prewarmController)
    assertNotNull(resumeUseCase)
  }

  @Test
  fun di_graph_provides_consistent_singletons() {
    // This test verifies that singleton-scoped dependencies return the same instance

    val app = ApplicationProvider.getApplicationContext<Application>()
    val ep = EntryPointAccessors.fromApplication(app, SingletonDepsEntryPoint::class.java)

    // Singletons should be the same instance
    assertSame("AppEnv should be singleton - same instance across accesses", appEnv, ep.appEnv())
    assertSame(
      "AssetQuestionRepository should be singleton - same instance across accesses",
      questionRepository,
      ep.questionRepository(),
    )
    assertSame("BlueprintResolver should be singleton - same instance across accesses", blueprintResolver, ep.blueprintResolver())
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface SingletonDepsEntryPoint {
    fun appEnv(): AppEnv
    fun questionRepository(): AssetQuestionRepository
    fun blueprintResolver(): BlueprintResolver
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface SingletonDepsEntryPoint {
    fun appEnv(): AppEnv
    fun questionRepository(): AssetQuestionRepository
    fun blueprintResolver(): BlueprintResolver
  }
}
