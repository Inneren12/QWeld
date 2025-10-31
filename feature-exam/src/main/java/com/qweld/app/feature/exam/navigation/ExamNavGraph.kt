package com.qweld.app.feature.exam.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.export.AttemptExporter
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.ui.ModeScreen
import com.qweld.app.feature.exam.ui.ExamScreen
import com.qweld.app.feature.exam.ui.ResultScreen
import com.qweld.app.feature.exam.ui.ReviewScreen
import com.qweld.app.feature.exam.vm.ExamViewModel
import com.qweld.app.feature.exam.vm.ExamViewModelFactory
import com.qweld.app.feature.exam.vm.PracticeShortcuts
import com.qweld.app.feature.exam.vm.PracticeShortcutsFactory
import com.qweld.app.feature.exam.vm.ResultViewModel
import com.qweld.app.feature.exam.vm.ResultViewModelFactory
import timber.log.Timber

object ExamDestinations {
  const val MODE = "exam_mode"
  const val EXAM = "exam_take"
  const val RESULT = "exam_result"
  const val REVIEW = "exam_review"
}

@Composable
fun ExamNavGraph(
  navController: NavHostController,
  repository: AssetQuestionRepository,
  explanationRepository: AssetExplanationRepository,
  attemptsRepository: AttemptsRepository,
  answersRepository: AnswersRepository,
  statsRepository: UserStatsRepository,
  appVersion: String,
  analytics: Analytics,
  userPrefs: UserPrefsDataStore,
  modifier: Modifier = Modifier,
) {
  val attemptExporter =
    remember(attemptsRepository, answersRepository, appVersion) {
      AttemptExporter(
        attemptsRepository = attemptsRepository,
        answersRepository = answersRepository,
        versionProvider = { appVersion },
      )
    }
  NavHost(
    navController = navController,
    startDestination = ExamDestinations.MODE,
    modifier = modifier,
  ) {
    composable(route = ExamDestinations.MODE) {
      val examViewModel: ExamViewModel =
        viewModel(
          factory =
            ExamViewModelFactory(
              repository = repository,
              attemptsRepository = attemptsRepository,
              answersRepository = answersRepository,
              statsRepository = statsRepository,
            ),
        )
      val practiceShortcuts: PracticeShortcuts =
        viewModel(
          factory =
            PracticeShortcutsFactory(
              attemptsRepository = attemptsRepository,
              answersRepository = answersRepository,
              userPrefs = userPrefs,
            ),
        )
      ModeScreen(
        repository = repository,
        viewModel = examViewModel,
        practiceShortcuts = practiceShortcuts,
        onIpMockClick = { locale ->
          val launched = examViewModel.startAttempt(ExamMode.IP_MOCK, locale)
          if (launched) {
            navController.navigate(ExamDestinations.EXAM) { launchSingleTop = true }
          }
        },
        onPracticeClick = { locale ->
          val launched = examViewModel.startAttempt(
            mode = ExamMode.PRACTICE,
            locale = locale,
            practiceSize = DEFAULT_PRACTICE_SIZE,
          )
          if (launched) {
            navController.navigate(ExamDestinations.EXAM) { launchSingleTop = true }
          }
        },
        onRepeatMistakes = { locale, blueprint ->
          val launched = examViewModel.startAttempt(
            mode = ExamMode.PRACTICE,
            locale = locale,
            practiceSize = blueprint.totalQuestions,
            blueprintOverride = blueprint,
          )
          if (launched) {
            navController.navigate(ExamDestinations.EXAM) { launchSingleTop = true }
          }
        },
        onResumeAttempt = {
          navController.navigate(ExamDestinations.EXAM) { launchSingleTop = true }
        },
      )
    }
    composable(route = ExamDestinations.EXAM) { backStackEntry ->
      val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(ExamDestinations.MODE) }
      val examViewModel: ExamViewModel =
        viewModel(
          parentEntry,
          factory =
            ExamViewModelFactory(
              repository = repository,
              attemptsRepository = attemptsRepository,
              answersRepository = answersRepository,
              statsRepository = statsRepository,
            ),
        )
      ExamScreen(
        viewModel = examViewModel,
        onNavigateToResult = {
          Timber.i("[ui_nav] screen=Result")
          navController.navigate(ExamDestinations.RESULT) { launchSingleTop = true }
        },
        analytics = analytics,
        userPrefs = userPrefs,
      )
    }
    composable(route = ExamDestinations.RESULT) { backStackEntry ->
      val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(ExamDestinations.MODE) }
      val examViewModel: ExamViewModel =
        viewModel(
          parentEntry,
          factory =
            ExamViewModelFactory(
              repository = repository,
              attemptsRepository = attemptsRepository,
              answersRepository = answersRepository,
              statsRepository = statsRepository,
            ),
        )
      val resultViewModel: ResultViewModel =
        viewModel(
          backStackEntry,
          factory =
            ResultViewModelFactory(
              resultDataProvider = { examViewModel.requireLatestResult() },
              attemptExporter = attemptExporter,
            ),
        )
      ResultScreen(
        viewModel = resultViewModel,
        onReview = {
          Timber.i("[ui_nav] screen=Review")
          navController.navigate(ExamDestinations.REVIEW) { launchSingleTop = true }
        },
      )
    }
    composable(route = ExamDestinations.REVIEW) { backStackEntry ->
      val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(ExamDestinations.MODE) }
      val examViewModel: ExamViewModel =
        viewModel(
          parentEntry,
          factory =
            ExamViewModelFactory(
              repository = repository,
              attemptsRepository = attemptsRepository,
              answersRepository = answersRepository,
              statsRepository = statsRepository,
            ),
        )
      val resultViewModel: ResultViewModel =
        viewModel(
          backStackEntry,
          factory =
            ResultViewModelFactory(
              resultDataProvider = { examViewModel.requireLatestResult() },
              attemptExporter = attemptExporter,
            ),
        )
      ReviewScreen(
        resultData = examViewModel.requireLatestResult(),
        explanationRepository = explanationRepository,
        onBack = { navController.popBackStack() },
        resultViewModel = resultViewModel,
        analytics = analytics,
      )
    }
  }
}

private const val DEFAULT_PRACTICE_SIZE = 20
