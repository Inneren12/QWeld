package com.qweld.app.feature.exam

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qweld.app.MainActivity
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.feature.exam.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExamResumeTimerRecreationTest {
  private val hiltRule = HiltAndroidRule(this)
  private val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

  @Inject lateinit var attemptsRepository: AttemptsRepository

  @Before
  fun setUp() {
    hiltRule.inject()
    runBlocking { attemptsRepository.clearAll() }
  }

  @Test
  fun answersAndTimerSurviveActivityRecreation() {
    startExamFromMode()
    waitForExamScreen()

    answerCurrentQuestion()
    goToNextQuestion()
    answerCurrentQuestion()
    goToNextQuestion()
    answerCurrentQuestion()

    val beforeRecreationSeconds = awaitStableTimerSeconds()

    composeRule.activityRule.scenario.recreate()
    waitForExamScreen()

    val afterRecreationSeconds = awaitStableTimerSeconds()

    assertTrue(afterRecreationSeconds <= beforeRecreationSeconds)
    assertTrue(beforeRecreationSeconds - afterRecreationSeconds <= TIMER_TOLERANCE_SECONDS)

    navigateBackToFirstQuestion()
    assertAnswerStillSelected()
  }

  private fun startExamFromMode() {
    val startCd = composeRule.activity.getString(R.string.mode_ip_mock_cd)
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule
        .onAllNodesWithContentDescription(startCd, useUnmergedTree = false)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeRule.onNodeWithContentDescription(startCd).performScrollTo()
    composeRule.onNodeWithContentDescription(startCd).performClick()
  }

  private fun waitForExamScreen() {
    val tag = "exam-content"
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(tag, useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun answerCurrentQuestion() {
    val unselected = composeRule.activity.getString(R.string.exam_choice_state_unselected)
    val selected = composeRule.activity.getString(R.string.exam_choice_state_selected)
    composeRule.onAllNodes(hasStateDescription(unselected), useUnmergedTree = true)[0].performClick()
    composeRule.onAllNodes(hasStateDescription(selected), useUnmergedTree = true)[0].assertExists()
  }

  private fun goToNextQuestion() {
    val nextCd = composeRule.activity.getString(R.string.exam_next_cd)
    composeRule.onNodeWithContentDescription(nextCd).performClick()
  }

  private fun navigateBackToFirstQuestion() {
    val previousCd = composeRule.activity.getString(R.string.exam_previous_cd)
    repeat(2) { composeRule.onNodeWithContentDescription(previousCd).performClick() }
  }

  private fun assertAnswerStillSelected() {
    val selected = composeRule.activity.getString(R.string.exam_choice_state_selected)
    composeRule.onAllNodes(hasStateDescription(selected), useUnmergedTree = true)[0].assertExists()
  }

  private fun awaitStableTimerSeconds(): Long {
    composeRule.waitUntil(timeoutMillis = 5_000) { findTimerNode() != null }
    val first = readTimerSeconds()
    composeRule.waitUntil(timeoutMillis = 5_000) { readTimerSeconds() < first }
    return readTimerSeconds()
  }

  private fun readTimerSeconds(): Long {
    val text = findTimerNode()
      ?.config
      ?.get(SemanticsProperties.Text)
      ?.joinToString(separator = "") { it.text }
    require(!text.isNullOrEmpty()) { "Timer label missing text" }
    val match = TIME_PATTERN.find(text.orEmpty()) ?: error("Timer label missing time component")
    val parts = match.value.split(":").map { it.toLong() }
    return TimeUnit.HOURS.toSeconds(parts[0]) + TimeUnit.MINUTES.toSeconds(parts[1]) + parts[2]
  }

  private fun findTimerNode() =
    composeRule
      .onAllNodesWithText(":", substring = true, useUnmergedTree = false)
      .fetchSemanticsNodes()
      .firstOrNull { semanticsNode ->
        semanticsNode.config[SemanticsProperties.Text]
          ?.joinToString(separator = "") { it.text }
          ?.let { TIME_PATTERN.containsMatchIn(it) } == true
      }

  companion object {
    private val TIME_PATTERN = Regex("\\d{2}:\\d{2}:\\d{2}")
    private const val TIMER_TOLERANCE_SECONDS = 5L
  }
}
