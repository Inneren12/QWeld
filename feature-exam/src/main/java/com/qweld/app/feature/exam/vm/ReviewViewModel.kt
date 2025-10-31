package com.qweld.app.feature.exam.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.feature.exam.model.ReviewChoiceUiModel
import com.qweld.app.feature.exam.model.ReviewQuestionUiModel
import java.util.LinkedHashMap
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReviewViewModel(
  resultData: ExamViewModel.ExamResultData,
  private val analytics: Analytics,
) : ViewModel() {

  private val allQuestions: List<ReviewQuestionUiModel> = buildReviewQuestions(resultData)
  private val totals = ReviewTotals(
    all = allQuestions.size,
    wrong = allQuestions.count { !it.isCorrect },
    flagged = allQuestions.count { it.isFlagged },
  )

  private var currentFilters: ReviewFilters = ReviewFilters()
  private val _uiState = MutableStateFlow(createState(currentFilters))
  val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

  fun toggleWrongOnly() {
    updateFilters(currentFilters.copy(wrongOnly = !currentFilters.wrongOnly))
  }

  fun toggleFlaggedOnly() {
    updateFilters(currentFilters.copy(flaggedOnly = !currentFilters.flaggedOnly))
  }

  fun toggleByTask() {
    updateFilters(currentFilters.copy(byTask = !currentFilters.byTask))
  }

  fun showAll() {
    if (!currentFilters.wrongOnly && !currentFilters.flaggedOnly) return
    updateFilters(currentFilters.copy(wrongOnly = false, flaggedOnly = false))
  }

  private fun updateFilters(filters: ReviewFilters) {
    if (filters == currentFilters) return
    currentFilters = filters
    _uiState.value = createState(filters)
    analytics.log(
      "review_filter",
      mapOf(
        "wrong" to filters.wrongOnly,
        "flagged" to filters.flaggedOnly,
        "byTask" to filters.byTask,
        "totals" to mapOf(
          "all" to totals.all,
          "wrong" to totals.wrong,
          "flagged" to totals.flagged,
        ),
      ),
    )
  }

  private fun createState(filters: ReviewFilters): ReviewUiState {
    val filtered = allQuestions.filter { question ->
      (!filters.wrongOnly || !question.isCorrect) && (!filters.flaggedOnly || question.isFlagged)
    }
    val displayItems = buildDisplayItems(filters, filtered)
    return ReviewUiState(
      filters = filters,
      allQuestions = allQuestions,
      filteredQuestions = filtered,
      displayItems = displayItems,
      totals = totals,
    )
  }

  private fun buildDisplayItems(
    filters: ReviewFilters,
    questions: List<ReviewQuestionUiModel>,
  ): List<ReviewListItem> {
    if (questions.isEmpty()) return emptyList()
    if (!filters.byTask) {
      return questions.mapIndexed { index, question ->
        ReviewListItem.Question(index = index, total = questions.size, question = question)
      }
    }
    val grouped = LinkedHashMap<String, MutableList<ReviewQuestionUiModel>>()
    for (question in questions) {
      val list = grouped.getOrPut(question.taskId) { mutableListOf() }
      list += question
    }
    var position = 0
    val total = questions.size
    val items = mutableListOf<ReviewListItem>()
    for ((taskId, taskQuestions) in grouped) {
      items += ReviewListItem.Section(title = taskId)
      for (question in taskQuestions) {
        items += ReviewListItem.Question(index = position, total = total, question = question)
        position += 1
      }
    }
    return items
  }

  private fun buildReviewQuestions(resultData: ExamViewModel.ExamResultData): List<ReviewQuestionUiModel> {
    val locale = resultData.attempt.locale.lowercase(Locale.US)
    val flagged = resultData.flaggedQuestionIds
    return resultData.attempt.questions.map { assembled ->
      val question = assembled.question
      val stem = question.stem[locale] ?: question.stem.values.firstOrNull().orEmpty()
      val selectedId = resultData.answers[question.id]
      val choices = assembled.choices.mapIndexed { index, choice ->
        val text = choice.text[locale] ?: choice.text.values.firstOrNull().orEmpty()
        ReviewChoiceUiModel(
          id = choice.id,
          label = ('A'.code + index).toChar().toString(),
          text = text,
          isCorrect = choice.id == question.correctChoiceId,
          isSelected = choice.id == selectedId,
        )
      }
      ReviewQuestionUiModel(
        id = question.id,
        taskId = question.taskId,
        stem = stem,
        choices = choices,
        rationale = resultData.rationales[question.id],
        isCorrect = selectedId != null && selectedId == question.correctChoiceId,
        isFlagged = flagged.contains(question.id),
      )
    }
  }
}

data class ReviewFilters(
  val wrongOnly: Boolean = false,
  val flaggedOnly: Boolean = false,
  val byTask: Boolean = false,
)

data class ReviewTotals(
  val all: Int,
  val wrong: Int,
  val flagged: Int,
)

data class ReviewUiState(
  val filters: ReviewFilters = ReviewFilters(),
  val allQuestions: List<ReviewQuestionUiModel> = emptyList(),
  val filteredQuestions: List<ReviewQuestionUiModel> = emptyList(),
  val displayItems: List<ReviewListItem> = emptyList(),
  val totals: ReviewTotals = ReviewTotals(all = 0, wrong = 0, flagged = 0),
) {
  val isEmpty: Boolean get() = allQuestions.isEmpty()
}

sealed interface ReviewListItem {
  data class Section(val title: String) : ReviewListItem
  data class Question(val index: Int, val total: Int, val question: ReviewQuestionUiModel) : ReviewListItem
}

class ReviewViewModelFactory(
  private val resultDataProvider: () -> ExamViewModel.ExamResultData,
  private val analytics: Analytics,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(ReviewViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return ReviewViewModel(resultDataProvider(), analytics) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
  }
}
