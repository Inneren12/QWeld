package com.qweld.app.feature.exam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.compose.composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.model.ResultUiState
import com.qweld.app.feature.exam.navigation.ExamDestinations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import androidx.activity.ComponentActivity

@RunWith(AndroidJUnit4::class)
class ResultsHasExitTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun exitButtonNavigatesHome() {
    lateinit var navController: TestNavHostController
    val state = ResultUiState(
      mode = ExamMode.IP_MOCK,
      totalCorrect = 0,
      totalQuestions = 0,
      scorePercent = 0.0,
      passStatus = null,
      blockSummaries = emptyList(),
      taskSummaries = emptyList(),
      timeLeftLabel = null,
    )

    composeTestRule.setContent {
      val localContext = LocalContext.current
      navController =
        remember {
          TestNavHostController(localContext).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = ExamDestinations.MODE) {
              composable(ExamDestinations.MODE) {}
              composable(ExamDestinations.RESULT) {}
            }

            navigate(ExamDestinations.RESULT)
          }
        }

      MaterialTheme(colorScheme = lightColorScheme()) {
        ResultScreenLayout(
          state = state,
          scoreLabel = "0%",
          onExport = {},
          onReview = {},
          logExportActions = null,
          onExit = {
            navController.navigate(ExamDestinations.MODE) {
              popUpTo(ExamDestinations.MODE) { inclusive = false }
              launchSingleTop = true
            }
          },
        )
      }
    }

    composeTestRule.onNodeWithTag("btn-exit").assertIsDisplayed().performClick()

    composeTestRule.runOnIdle {
      assertEquals(ExamDestinations.MODE, navController.currentDestination?.route)
    }
  }
}
