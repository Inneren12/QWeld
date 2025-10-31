package com.qweld.app.feature.exam.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.ui.ModeScreen
import com.qweld.app.feature.exam.ui.ExamScreen
import com.qweld.app.feature.exam.vm.ExamViewModel
import com.qweld.app.feature.exam.vm.ExamViewModelFactory

object ExamDestinations {
  const val MODE = "exam_mode"
  const val EXAM = "exam_take"
}

@Composable
fun ExamNavGraph(
  navController: NavHostController,
  repository: AssetQuestionRepository,
  modifier: Modifier = Modifier,
) {
  NavHost(
    navController = navController,
    startDestination = ExamDestinations.MODE,
    modifier = modifier,
  ) {
    composable(route = ExamDestinations.MODE) {
      val examViewModel: ExamViewModel = viewModel(factory = ExamViewModelFactory(repository))
      ModeScreen(
        repository = repository,
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
      )
    }
    composable(route = ExamDestinations.EXAM) { backStackEntry ->
      val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(ExamDestinations.MODE) }
      val examViewModel: ExamViewModel = viewModel(parentEntry, factory = ExamViewModelFactory(repository))
      ExamScreen(viewModel = examViewModel)
    }
  }
}

private const val DEFAULT_PRACTICE_SIZE = 20
