package com.qweld.app.feature.exam.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.ui.ModeScreen

object ExamDestinations {
  const val MODE = "exam_mode"
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
      ModeScreen(repository = repository)
    }
  }
}
