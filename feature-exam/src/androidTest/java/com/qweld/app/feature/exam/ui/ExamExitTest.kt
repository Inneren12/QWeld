package com.qweld.app.feature.exam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.ExamAttemptUiState
import com.qweld.app.feature.exam.model.ExamChoiceUiModel
import com.qweld.app.feature.exam.model.ExamQuestionUiModel
import com.qweld.app.feature.exam.model.ExamUiState
import com.qweld.app.feature.exam.navigation.ExamDestinations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast

@RunWith(AndroidJUnit4::class)
class ExamExitTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exitExamShowsDialogAndNavigatesHome() {
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            val context = LocalContext.current

            navController = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                    graph = createGraph(startDestination = ExamDestinations.MODE) {
                        composable(ExamDestinations.MODE) {}
                        composable(ExamDestinations.RESULT) {}
                    }
                    // Стартуем из RESULT, чтобы тест имитировал "мы уже в экране результата"
                    navigate(ExamDestinations.RESULT)
                }
            }

            MaterialTheme(colorScheme = lightColorScheme()) {
                var showDialog by remember { mutableStateOf(false) }

                if (showDialog) {
                    ConfirmExitDialog(
                        mode = ExamMode.IP_MOCK,
                        onCancel = { showDialog = false },
                        onExit = {
                            showDialog = false
                            navController.navigate(ExamDestinations.MODE) {
                                popUpTo(ExamDestinations.MODE) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                ExamScreenContent(
                    state = ExamUiState(attempt = createAttempt(ExamMode.IP_MOCK)),
                    onChoiceSelected = {},
                    onNext = {},
                    onPrevious = {},
                    onDismissDeficit = {},
                    onFinish = {},
                    onShowRestart = {},
                    onShowExit = { showDialog = true },
                    onReportQuestionClick = {},
                )
            }
        }

        // здесь Compose уже "поднял" иерархию, можно искать ноды
        composeTestRule
            .onNodeWithTag("btn-exit")
            .assertIsDisplayed()
            .performClick()

        composeTestRule
            .onAllNodesWithText("Exit")
            .onLast()
            .assertIsDisplayed()
            .performClick()

        // даём Compose/навигации доехать и проверяем маршрут
        composeTestRule.runOnIdle {
            assertEquals(ExamDestinations.MODE, navController.currentDestination?.route)
        }
    }

    private fun createAttempt(mode: ExamMode): ExamAttemptUiState {
    val choices = listOf("A", "B", "C", "D").mapIndexed { index, label ->
      ExamChoiceUiModel(
        id = "choice-$index",
        label = label,
        text = "Option $label",
        isSelected = false,
      )
    }
    val question = ExamQuestionUiModel(
      id = "question-1",
      stem = "Question text",
      choices = choices,
      selectedChoiceId = null,
    )
    return ExamAttemptUiState(
      attemptId = "attempt-${mode.name.lowercase()}",
      mode = mode,
      locale = "en",
      questions = listOf(question),
      currentIndex = 0,
    )
  }
}
