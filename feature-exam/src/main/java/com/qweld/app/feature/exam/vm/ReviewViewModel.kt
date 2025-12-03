package com.qweld.app.feature.exam.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.explain.ExplanationRepositoryImpl
import com.qweld.app.feature.exam.model.ReviewChoiceUiModel
import com.qweld.app.feature.exam.model.ReviewQuestionUiModel
import kotlinx.coroutines.FlowPreview
import java.util.LinkedHashMap
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(FlowPreview::class)
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
  private var currentSearchInput: String = ""
  private var appliedSearchQuery: String = ""
  private var searchMatches: Map<String, ReviewSearchHighlights> = emptyMap()
  private val explanationRepository = ExplanationRepositoryImpl()
  private val searchQueries = MutableStateFlow("")

  private val _uiState = MutableStateFlow(createState(currentFilters))
  val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      searchQueries
        .debounce(150)
        .distinctUntilChanged()
        .collect { query ->
          applySearch(query)
        }
    }
  }

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

  fun onSearchInputChange(query: String) {
    currentSearchInput = query
    searchQueries.value = query
    if (query.isBlank()) {
      if (appliedSearchQuery.isBlank()) {
        _uiState.update { state ->
          state.copy(search = state.search.copy(input = query))
        }
      } else {
        appliedSearchQuery = ""
        searchMatches = emptyMap()
        _uiState.value = createState(currentFilters)
      }
      return
    }
    _uiState.update { state ->
      state.copy(search = state.search.copy(input = query))
    }
  }

  fun onExplanationLoaded(questionId: String, explanation: AssetExplanationRepository.Explanation?) {
    explanationRepository.put(questionId, explanation)
    if (appliedSearchQuery.isBlank()) return
    searchMatches = buildSearchMatches(appliedSearchQuery)
    _uiState.value = createState(currentFilters)
  }

  private fun applySearch(raw: String) {
    val query = raw.trim()
    if (query == appliedSearchQuery) {
      if (query.isBlank()) {
        Timber.i("[review_search] q=\"\" hits=0")
      }
      return
    }
    appliedSearchQuery = query
    searchMatches = if (query.isBlank()) emptyMap() else buildSearchMatches(query)
    val state = createState(currentFilters)
    if (query.isNotEmpty()) {
      Timber.i("[review_search] q=\"%s\" hits=%d", query, state.search.hits)
    } else {
      Timber.i("[review_search] q=\"\" hits=0")
    }
    _uiState.value = state
  }

  private fun buildSearchMatches(query: String): Map<String, ReviewSearchHighlights> {
    val explanationHits = explanationRepository.search(query)
    val matches = mutableMapOf<String, ReviewSearchHighlights>()
    for (question in allQuestions) {
      val stemMatches = findMatches(question.stem, query)
      val choiceMatches = mutableMapOf<String, List<IntRange>>()
      for (choice in question.choices) {
        val choiceMatch = findMatches(choice.text, query)
        if (choiceMatch.isNotEmpty()) {
          choiceMatches[choice.id] = choiceMatch
        }
      }
      val rationaleMatches = question.rationale?.let { findMatches(it, query) }.orEmpty()
      val explanationMatch = explanationHits.contains(question.id)
      if (stemMatches.isNotEmpty() || choiceMatches.isNotEmpty() || rationaleMatches.isNotEmpty() || explanationMatch) {
        matches[question.id] = ReviewSearchHighlights(
          stem = stemMatches,
          choices = choiceMatches,
          rationale = rationaleMatches,
          matchesExplanation = explanationMatch,
        )
      }
    }
    return matches
  }

  private fun findMatches(text: String, query: String): List<IntRange> {
    if (text.isBlank() || query.isBlank()) return emptyList()
    val lowerText = text.lowercase(Locale.getDefault())
    val lowerQuery = query.lowercase(Locale.getDefault())
    val ranges = mutableListOf<IntRange>()
    var index = lowerText.indexOf(lowerQuery)
    while (index >= 0) {
      val endExclusive = index + lowerQuery.length
      ranges += index until endExclusive
      index = lowerText.indexOf(lowerQuery, startIndex = endExclusive)
    }
    return ranges
  }

  private fun createState(filters: ReviewFilters): ReviewUiState {
    val filtered = allQuestions.filter { question ->
      (!filters.wrongOnly || !question.isCorrect) && (!filters.flaggedOnly || question.isFlagged)
    }
    val matchesForFiltered = if (appliedSearchQuery.isBlank()) {
      emptyMap()
    } else {
      searchMatches.filterKeys { id -> filtered.any { it.id == id } }
    }
    val searched = if (appliedSearchQuery.isBlank()) {
      filtered
    } else {
      filtered.filter { matchesForFiltered.containsKey(it.id) }
    }
    val displayItems = buildDisplayItems(filters, searched)
    return ReviewUiState(
      filters = filters,
      allQuestions = allQuestions,
      filteredQuestions = filtered,
      displayItems = displayItems,
      totals = totals,
      search = ReviewSearchState(
        input = currentSearchInput,
        applied = appliedSearchQuery,
        hits = if (appliedSearchQuery.isBlank()) 0 else searched.size,
        matches = matchesForFiltered,
      ),
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
  val search: ReviewSearchState = ReviewSearchState(),
) {
  val isEmpty: Boolean get() = allQuestions.isEmpty()
}

data class ReviewSearchState(
  val input: String = "",
  val applied: String = "",
  val hits: Int = 0,
  val matches: Map<String, ReviewSearchHighlights> = emptyMap(),
)

data class ReviewSearchHighlights(
  val stem: List<IntRange> = emptyList(),
  val choices: Map<String, List<IntRange>> = emptyMap(),
  val rationale: List<IntRange> = emptyList(),
  val matchesExplanation: Boolean = false,
)

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
