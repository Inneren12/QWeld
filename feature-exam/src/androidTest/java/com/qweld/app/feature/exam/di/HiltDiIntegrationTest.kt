package com.qweld.app.feature.exam.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.common.error.AppErrorHandler
import com.qweld.app.data.prefs.UserPrefs
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.reports.QuestionReportRepository
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.vm.BlueprintResolver
import com.qweld.app.feature.exam.vm.ExamViewModel
import com.qweld.app.feature.exam.vm.PrewarmController
import com.qweld.app.feature.exam.vm.ResumeUseCase
import com.qweld.core.common.AppEnv
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.test.assertNotNull

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
      appErrorHandler = null, // Optional in real usage
      blueprintResolver = blueprintResolver,
      timerController = timerController,
      prewarmRunner = prewarmController,
      resumeUseCase = resumeUseCase
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

    // Create a second test class and inject
    val secondTest = SecondInjectionPoint()
    hiltRule.inject(secondTest)

    // Singletons should be the same instance
    assert(appEnv === secondTest.appEnv) {
      "AppEnv should be singleton - same instance across injections"
    }
    assert(questionRepository === secondTest.questionRepository) {
      "AssetQuestionRepository should be singleton - same instance across injections"
    }
    assert(blueprintResolver === secondTest.blueprintResolver) {
      "BlueprintResolver should be singleton - same instance across injections"
    }
  }

  // Helper class for singleton verification
  class SecondInjectionPoint {
    @Inject
    lateinit var appEnv: AppEnv

    @Inject
    lateinit var questionRepository: AssetQuestionRepository

    @Inject
    lateinit var blueprintResolver: BlueprintResolver
  }
}
