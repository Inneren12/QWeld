package com.qweld.app.feature.exam.vm

import com.qweld.app.data.analytics.Analytics
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.domain.exam.AssembledQuestion
import com.qweld.app.domain.exam.AttemptSeed
import com.qweld.app.domain.exam.Choice
import com.qweld.app.domain.exam.ExamAttempt
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.Question
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.feature.exam.vm.ReviewListItem.Section
import com.qweld.app.feature.exam.vm.ReviewListItem.Question as QuestionItem
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ReviewViewModelTest {

  @get:Rule val dispatcherRule = MainDispatcherRule()

  private val analytics = RecordingAnalytics()

  @Test
  fun initialState_containsAllQuestionsAndTotals() {
    val resultData = createResultData(
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = true, isFlagged = false),
      QuestionConfig(taskId = "A-2", blockId = "A", isCorrect = false, isFlagged = true),
    )

    val viewModel = ReviewViewModel(resultData, analytics)

    val state = viewModel.uiState.value
    assertEquals(2, state.allQuestions.size)
    assertEquals(2, state.totals.all)
    assertEquals(1, state.totals.wrong)
    assertEquals(1, state.totals.flagged)
    assertFalse(state.filters.wrongOnly)
    assertFalse(state.filters.flaggedOnly)
    assertFalse(state.filters.byTask)
  }

  @Test
  fun toggleWrongOnly_filtersIncorrectQuestions() {
    val resultData = createResultData(
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = true, isFlagged = false),
      QuestionConfig(taskId = "A-2", blockId = "A", isCorrect = false, isFlagged = false),
    )
    val viewModel = ReviewViewModel(resultData, analytics)

    viewModel.toggleWrongOnly()

    val state = viewModel.uiState.value
    assertTrue(state.filters.wrongOnly)
    assertEquals(1, state.filteredQuestions.size)
    assertTrue(state.filteredQuestions.all { !it.isCorrect })
  }

  @Test
  fun toggleFlaggedOnly_filtersFlaggedQuestions() {
    val resultData = createResultData(
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = false, isFlagged = true),
      QuestionConfig(taskId = "B-1", blockId = "B", isCorrect = true, isFlagged = false),
    )
    val viewModel = ReviewViewModel(resultData, analytics)

    viewModel.toggleFlaggedOnly()

    val state = viewModel.uiState.value
    assertTrue(state.filters.flaggedOnly)
    assertEquals(1, state.filteredQuestions.size)
    assertTrue(state.filteredQuestions.all { it.isFlagged })
  }

  @Test
  fun combinedWrongAndFlagged_filtersIntersection() {
    val resultData = createResultData(
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = false, isFlagged = true),
      QuestionConfig(taskId = "A-2", blockId = "A", isCorrect = false, isFlagged = false),
      QuestionConfig(taskId = "B-1", blockId = "B", isCorrect = true, isFlagged = true),
    )
    val viewModel = ReviewViewModel(resultData, analytics)

    viewModel.toggleWrongOnly()
    viewModel.toggleFlaggedOnly()

    val state = viewModel.uiState.value
    assertTrue(state.filters.wrongOnly)
    assertTrue(state.filters.flaggedOnly)
    assertEquals(1, state.filteredQuestions.size)
    assertTrue(state.filteredQuestions.all { !it.isCorrect && it.isFlagged })
  }

  @Test
  fun toggleByTask_groupsIntoSections() {
    val resultData = createResultData(
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = true, isFlagged = false),
      QuestionConfig(taskId = "B-2", blockId = "B", isCorrect = false, isFlagged = true),
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = false, isFlagged = false),
    )
    val viewModel = ReviewViewModel(resultData, analytics)

    viewModel.toggleByTask()

    val state = viewModel.uiState.value
    val sections = state.displayItems.filterIsInstance<Section>()
    assertEquals(listOf("A-1", "B-2"), sections.map { it.title })
    assertTrue(state.displayItems.any { item ->
      item is QuestionItem && item.question.taskId == "A-1"
    })
  }

  @Test
  fun showAll_resetsWrongAndFlaggedFilters() {
    val resultData = createResultData(
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = false, isFlagged = true),
      QuestionConfig(taskId = "B-1", blockId = "B", isCorrect = true, isFlagged = false),
    )
    val viewModel = ReviewViewModel(resultData, analytics)

    viewModel.toggleWrongOnly()
    viewModel.toggleFlaggedOnly()
    viewModel.showAll()

    val state = viewModel.uiState.value
    assertFalse(state.filters.wrongOnly)
    assertFalse(state.filters.flaggedOnly)
  }

  @Test
  fun toggleFilter_emitsAnalyticsEvent() {
    val resultData = createResultData(
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = true, isFlagged = false),
      QuestionConfig(taskId = "A-2", blockId = "A", isCorrect = false, isFlagged = true),
    )
    val viewModel = ReviewViewModel(resultData, analytics)

    analytics.events.clear()
    viewModel.toggleWrongOnly()

    val (eventName, params) = analytics.events.last()
    assertEquals("review_filter", eventName)
    assertEquals(true, params["wrong"])
    assertEquals(false, params["flagged"])
    assertEquals(false, params["byTask"])
    @Suppress("UNCHECKED_CAST")
    val totals = params["totals"] as Map<String, Int>
    assertEquals(2, totals["all"])
    assertEquals(1, totals["wrong"])
    assertEquals(1, totals["flagged"])
  }

  @Test
  fun searchFiltersQuestionsAndHighlightsStem() = runTest {
    val resultData = createResultData(
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = true, isFlagged = false),
      QuestionConfig(taskId = "B-2", blockId = "B", isCorrect = false, isFlagged = false),
    )
    val viewModel = ReviewViewModel(resultData, analytics)

    viewModel.onSearchInputChange("stem 0")
    advanceTimeBy(200)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("stem 0", state.search.applied)
    assertEquals(1, state.search.hits)
    val match = state.search.matches[resultData.attempt.questions[0].question.id]
    assertTrue(match?.stem?.isNotEmpty() == true)
  }

  @Test
  fun searchMatchesRationaleText() = runTest {
    val rationale = mapOf("q1" to "The weld pool must be monitored closely")
    val resultData = createResultData(
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = true, isFlagged = false),
      QuestionConfig(taskId = "A-2", blockId = "A", isCorrect = false, isFlagged = false),
      rationales = rationale,
    )
    val viewModel = ReviewViewModel(resultData, analytics)

    viewModel.onSearchInputChange("weld pool")
    advanceTimeBy(200)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(1, state.search.hits)
    val match = state.search.matches["q1"]
    assertTrue(match?.rationale?.isNotEmpty() == true)
  }

  @Test
  fun searchIncludesLoadedExplanations() = runTest {
    val resultData = createResultData(
      QuestionConfig(taskId = "A-1", blockId = "A", isCorrect = true, isFlagged = false),
    )
    val viewModel = ReviewViewModel(resultData, analytics)

    val explanation = AssetExplanationRepository.Explanation(
      id = "q0",
      summary = "Always clean the base metal",
      steps = emptyList(),
      whyNot = emptyList(),
      tips = emptyList(),
    )
    viewModel.onExplanationLoaded("q0", explanation)

    viewModel.onSearchInputChange("base metal")
    advanceTimeBy(200)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(1, state.search.hits)
    val match = state.search.matches["q0"]
    assertTrue(match?.matchesExplanation == true)
  }

  private fun createResultData(
    vararg configs: QuestionConfig,
    rationales: Map<String, String> = emptyMap(),
  ): ExamViewModel.ExamResultData {
    val blueprint = ExamBlueprint(
      totalQuestions = configs.size,
      taskQuotas = configs.toList().groupBy { it.taskId }.map { (taskId, entries) ->
        TaskQuota(taskId = taskId, blockId = entries.first().blockId, required = entries.size)
      },
    )
    val assembled = configs.mapIndexed { index, config ->
      val correctId = "choice_${index}_correct"
      val wrongId = "choice_${index}_wrong"
      val choices = listOf(
        Choice(id = correctId, text = mapOf("en" to "Correct")),
        Choice(id = wrongId, text = mapOf("en" to "Wrong")),
      )
      val question = Question(
        id = "q$index",
        taskId = config.taskId,
        blockId = config.blockId,
        locale = "en",
        stem = mapOf("en" to "Stem $index"),
        choices = choices,
        correctChoiceId = correctId,
      )
      val assembledQuestion = AssembledQuestion(
        question = question,
        choices = choices,
        correctIndex = 0,
      )
      assembledQuestion to if (config.isCorrect) correctId else wrongId
    }
    val answers = assembled.associate { (question, choiceId) -> question.question.id to choiceId }
    val flagged = configs.mapIndexedNotNull { index, config ->
      if (config.isFlagged) "q$index" else null
    }.toSet()

    val attempt = ExamAttempt(
      mode = ExamMode.IP_MOCK,
      locale = "en",
      seed = AttemptSeed(0),
      questions = assembled.map { it.first },
      blueprint = blueprint,
    )

    return ExamViewModel.ExamResultData(
      attemptId = "test",
      attempt = attempt,
      answers = answers,
      remaining = Duration.ZERO,
      rationales = rationales,
      scorePercent = 0.0,
      passThreshold = null,
      flaggedQuestionIds = flagged,
    )
  }

  private data class QuestionConfig(
    val taskId: String,
    val blockId: String,
    val isCorrect: Boolean,
    val isFlagged: Boolean,
  )

  private class RecordingAnalytics : Analytics {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    override fun log(event: String, params: Map<String, Any?>) {
      events += event to params
    }
  }
}
