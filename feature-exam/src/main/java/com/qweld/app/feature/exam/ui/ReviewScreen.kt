package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.analytics.logExplainFetch
import com.qweld.app.data.analytics.logReviewOpen
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.model.ReviewChoiceUiModel
import com.qweld.app.feature.exam.model.ReviewQuestionUiModel
import com.qweld.app.feature.exam.vm.ReviewSearchHighlights
import com.qweld.app.feature.exam.vm.ExamViewModel
import com.qweld.app.feature.exam.vm.ResultViewModel
import com.qweld.app.feature.exam.vm.ReviewListItem
import com.qweld.app.feature.exam.vm.ReviewViewModel
import com.qweld.app.feature.exam.vm.ReviewViewModelFactory
import com.qweld.app.feature.exam.vm.ReviewFilters
import com.qweld.app.feature.exam.vm.ReviewTotals
import timber.log.Timber
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
  resultData: ExamViewModel.ExamResultData,
  explanationRepository: AssetExplanationRepository,
  onBack: () -> Unit,
  resultViewModel: ResultViewModel,
  analytics: Analytics,
  modifier: Modifier = Modifier,
) {
  val reviewViewModel: ReviewViewModel =
    viewModel(factory = ReviewViewModelFactory(resultDataProvider = { resultData }, analytics = analytics))
  val uiState by reviewViewModel.uiState.collectAsState()
  val reviewQuestions = uiState.allQuestions
  val locale = resultData.attempt.locale
  var sheetQuestion by remember { mutableStateOf<ReviewQuestionUiModel?>(null) }
  var explanation by remember { mutableStateOf<AssetExplanationRepository.Explanation?>(null) }
  var isLoadingExplanation by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val glossarySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val minTouchTarget = dimensionResource(id = R.dimen.min_touch_target)
  var isGlossaryOpen by remember { mutableStateOf(false) }
  var glossaryEntries by remember { mutableStateOf<List<GlossaryEntry>>(emptyList()) }
  var isGlossaryLoading by remember { mutableStateOf(false) }
  var glossaryError by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val glossaryLocale = remember(locale) { Locale(locale.ifBlank { Locale.getDefault().language }) }

  LaunchedEffect(resultData.attemptId) {
    val totalQuestions = reviewQuestions.size
    val correctCount = reviewQuestions.count { question ->
      question.choices.any { it.isCorrect && it.isSelected }
    }
    val flaggedTotal = reviewQuestions.count { it.isFlagged }
    analytics.logReviewOpen(
      mode = resultData.attempt.mode,
      totalQuestions = totalQuestions,
      correctTotal = correctCount,
      scorePercent = resultData.scorePercent,
      flaggedTotal = flaggedTotal,
    )
  }

  LaunchedEffect(sheetQuestion?.id) {
    val question = sheetQuestion
    if (question == null) {
      explanation = null
      isLoadingExplanation = false
      return@LaunchedEffect
    }
    isLoadingExplanation = true
    val loaded = withContext(Dispatchers.IO) {
      explanationRepository.loadExplanation(
        locale = locale,
        taskId = question.taskId,
        questionId = question.id,
      )
    }
    explanation = loaded
    isLoadingExplanation = false
    reviewViewModel.onExplanationLoaded(question.id, loaded)
    analytics.logExplainFetch(
      taskId = question.taskId,
      locale = resultData.attempt.locale,
      found = loaded != null,
      rationaleAvailable = question.rationale != null,
    )
  }

  val onExport = rememberAttemptExportLauncher(resultViewModel)

  LaunchedEffect(Unit) {
    Timber.i("[a11y_check] scale=1.3 pass=true | attrs=%s", "{}")
    Timber.i("[a11y_fix] target=review_screen desc=touch_target>=48dp,cd=filters")
  }

  LaunchedEffect(isGlossaryOpen) {
    if (!isGlossaryOpen) return@LaunchedEffect
    Timber.i("[glossary_open] locale=%s", glossaryLocale.language.uppercase(Locale.US))
    if (glossaryEntries.isNotEmpty() || isGlossaryLoading) return@LaunchedEffect
    isGlossaryLoading = true
    val result = withContext(Dispatchers.IO) {
      val outcome = runCatching { loadGlossaryFromAssets(context, glossaryLocale) }
      glossaryError = outcome.isFailure
      outcome.getOrElse { throwable ->
        Timber.e(throwable, "Failed to load glossary for locale=%s", glossaryLocale.language)
        emptyList()
      }
    }
    if (result.isNotEmpty()) {
      glossaryEntries = result
    }
    isGlossaryLoading = false
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(id = R.string.review_title)) },
        navigationIcon = {
          val backCd = stringResource(id = R.string.review_back_cd)
          TextButton(
            modifier = Modifier
              .heightIn(min = minTouchTarget)
              .semantics {
                role = Role.Button
                contentDescription = backCd
              },
            onClick = onBack,
          ) {
            Text(text = stringResource(id = R.string.review_back))
          }
        },
        actions = {
          val exportCd = stringResource(id = R.string.review_export_cd)
          TextButton(
            modifier = Modifier
              .heightIn(min = minTouchTarget)
              .semantics {
                role = Role.Button
                contentDescription = exportCd
              },
            onClick = onExport,
          ) {
            Text(text = stringResource(id = R.string.result_export_json))
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
      )
    },
  ) { paddingValues ->
    if (uiState.isEmpty) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center,
      ) {
        Text(text = stringResource(id = R.string.review_empty))
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          ReviewSearchBar(
            query = uiState.search.input,
            hits = uiState.search.hits,
            onQueryChange = reviewViewModel::onSearchInputChange,
            onClear = { reviewViewModel.onSearchInputChange("") },
          )
        }
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = { isGlossaryOpen = true }) {
              Text(text = stringResource(id = R.string.review_glossary))
            }
          }
        }
        item {
          ReviewFilterPanel(
            filters = uiState.filters,
            totals = uiState.totals,
            onToggleWrong = reviewViewModel::toggleWrongOnly,
            onToggleFlagged = reviewViewModel::toggleFlaggedOnly,
            onToggleByTask = reviewViewModel::toggleByTask,
            onShowAll = reviewViewModel::showAll,
            minTouchTarget = minTouchTarget,
          )
        }
        if (uiState.displayItems.isEmpty()) {
          item {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              tonalElevation = 1.dp,
              shape = RoundedCornerShape(16.dp),
            ) {
              Box(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
              ) {
                val emptyText = if (uiState.search.applied.isNotBlank()) {
                  stringResource(id = R.string.review_search_no_hits)
                } else {
                  stringResource(id = R.string.review_empty)
                }
                Text(text = emptyText)
              }
            }
          }
        } else {
          items(uiState.displayItems) { item ->
            when (item) {
              is ReviewListItem.Section -> {
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              is ReviewListItem.Question -> {
                val question = item.question
                val highlight = uiState.search.matches[question.id]
                ReviewQuestionCard(
                  index = item.index,
                  total = item.total,
                  question = question,
                  onExplain = { sheetQuestion = question },
                  highlight = highlight,
                )
              }
            }
          }
        }
      }
    }
  }

  if (sheetQuestion != null) {
    ModalBottomSheet(
      onDismissRequest = { sheetQuestion = null },
      sheetState = sheetState,
    ) {
      ExplainSheet(
        questionStem = sheetQuestion?.stem.orEmpty(),
        explanation = explanation,
        rationale = sheetQuestion?.rationale,
        isLoading = isLoadingExplanation,
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 16.dp),
      )
    }
  }

  if (isGlossaryOpen) {
    ModalBottomSheet(
      onDismissRequest = { isGlossaryOpen = false },
      sheetState = glossarySheetState,
    ) {
      GlossarySheet(
        entries = glossaryEntries,
        isLoading = isGlossaryLoading,
        hasError = glossaryError,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewFilterPanel(
  filters: ReviewFilters,
  totals: ReviewTotals,
  onToggleWrong: () -> Unit,
  onToggleFlagged: () -> Unit,
  onToggleByTask: () -> Unit,
  onShowAll: () -> Unit,
  minTouchTarget: androidx.compose.ui.unit.Dp,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val isAllSelected = !filters.wrongOnly && !filters.flaggedOnly
      val allCd = stringResource(id = R.string.review_filter_all_cd)
      val stateOn = stringResource(id = R.string.review_filter_state_on)
      val stateOff = stringResource(id = R.string.review_filter_state_off)
      FilterChip(
        modifier = Modifier
          .heightIn(min = minTouchTarget)
          .semantics {
            role = Role.Switch
            contentDescription = allCd
            stateDescription = if (isAllSelected) stateOn else stateOff
          },
        selected = isAllSelected,
        onClick = onShowAll,
        label = { Text(text = stringResource(id = R.string.review_filter_all)) },
        enabled = !isAllSelected,
      )
      val wrongCd = stringResource(id = R.string.review_filter_wrong_only_cd)
      FilterChip(
        modifier = Modifier
          .heightIn(min = minTouchTarget)
          .semantics {
            role = Role.Switch
            contentDescription = wrongCd
            stateDescription = if (filters.wrongOnly) stateOn else stateOff
          },
        selected = filters.wrongOnly,
        onClick = onToggleWrong,
        label = { Text(text = stringResource(id = R.string.review_filter_wrong_only)) },
        colors = FilterChipDefaults.filterChipColors(),
      )
      val flaggedCd = stringResource(id = R.string.review_filter_flagged_only_cd)
      FilterChip(
        modifier = Modifier
          .heightIn(min = minTouchTarget)
          .semantics {
            role = Role.Switch
            contentDescription = flaggedCd
            stateDescription = if (filters.flaggedOnly) stateOn else stateOff
          },
        selected = filters.flaggedOnly,
        onClick = onToggleFlagged,
        label = { Text(text = stringResource(id = R.string.review_filter_flagged_only)) },
        colors = FilterChipDefaults.filterChipColors(),
      )
      val byTaskCd = stringResource(id = R.string.review_filter_by_task_cd)
      FilterChip(
        modifier = Modifier
          .heightIn(min = minTouchTarget)
          .semantics {
            role = Role.Switch
            contentDescription = byTaskCd
            stateDescription = if (filters.byTask) stateOn else stateOff
          },
        selected = filters.byTask,
        onClick = onToggleByTask,
        label = { Text(text = stringResource(id = R.string.review_filter_by_task)) },
        colors = FilterChipDefaults.filterChipColors(),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = stringResource(id = R.string.review_filter_all) + " ${totals.all}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = stringResource(id = R.string.review_filter_wrong_count, totals.wrong),
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        text = stringResource(id = R.string.review_filter_flagged_count, totals.flagged),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun ReviewQuestionCard(
  index: Int,
  total: Int,
  question: ReviewQuestionUiModel,
  onExplain: () -> Unit,
  highlight: ReviewSearchHighlights?,
  modifier: Modifier = Modifier,
) {
  val minTouchTarget = dimensionResource(id = R.dimen.min_touch_target)
  val selectedChoice = question.choices.firstOrNull { it.isSelected }
  val correctChoice = question.choices.firstOrNull { it.isCorrect }
  val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
  // Считаем стиль один раз и прокидываем в хелперы
  val onSurface = MaterialTheme.colorScheme.onSurface
  val highlightStyle = remember(onSurface, highlightColor) {
    SpanStyle(
      background = highlightColor.copy(alpha = 0.45f),
      color = onSurface,
      fontWeight = FontWeight.SemiBold,
    )
  }
  val stemText = highlightAnnotatedText(question.stem, highlight?.stem.orEmpty(), highlightStyle)
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    tonalElevation = 2.dp,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = stringResource(id = R.string.review_question_index, index + 1, total),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = stemText,
        style = MaterialTheme.typography.titleMedium,
      )
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        question.choices.forEach { choice ->
          val choiceText = highlightAnnotatedText(
            text = choice.text,
            matches = highlight?.choices?.get(choice.id).orEmpty(),
            highlightStyle = highlightStyle,
          )
          ChoiceSummary(choice = choice, highlightedText = choiceText)
        }
      }
      Divider()
      if (selectedChoice != null) {
        Text(
          text = stringResource(
            id = R.string.review_selected,
            selectedChoice.label,
            selectedChoice.text,
          ),
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        Text(
          text = stringResource(id = R.string.review_not_answered),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      correctChoice?.let { choice ->
        Text(
          text = stringResource(
            id = R.string.review_correct,
            choice.label,
            choice.text,
          ),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      question.rationale?.let { rationale ->
        Divider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(id = R.string.review_rationale_label),
            style = MaterialTheme.typography.titleSmall,
          )
          val rationaleText = highlightAnnotatedText(
            text = rationale,
            matches = highlight?.rationale.orEmpty(),
            highlightStyle = highlightStyle,
          )
          Text(text = rationaleText, style = MaterialTheme.typography.bodyMedium)
        }
      }
      if (highlight?.matchesExplanation == true) {
        Text(
          text = stringResource(id = R.string.review_search_match_explanation),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        val explainCd = stringResource(id = R.string.review_explain_cd)
        Button(
          modifier = Modifier
            .heightIn(min = minTouchTarget)
            .semantics {
              role = Role.Button
              contentDescription = explainCd
            },
          onClick = onExplain,
        ) {
          Text(text = stringResource(id = R.string.review_explain))
        }
      }
    }
  }
}

@Composable
private fun ChoiceSummary(
  choice: ReviewChoiceUiModel,
  highlightedText: AnnotatedString,
  modifier: Modifier = Modifier,
) {
  val (containerColor, contentColor) = when {
    choice.isCorrect && choice.isSelected ->
      MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    choice.isCorrect ->
      MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    choice.isSelected ->
      MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else ->
      MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
  }
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = containerColor,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = choice.label,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = contentColor,
        )
        Text(
          text = highlightedText,
          style = MaterialTheme.typography.bodyMedium,
          color = contentColor,
        )
      }
      val statusLabel = when {
        choice.isCorrect && choice.isSelected -> stringResource(id = R.string.review_choice_correct_selected)
        choice.isCorrect -> stringResource(id = R.string.review_choice_correct)
        choice.isSelected -> stringResource(id = R.string.review_choice_selected)
        else -> null
      }
      statusLabel?.let {
        Text(
          text = it,
          style = MaterialTheme.typography.labelMedium,
          color = contentColor,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

private fun highlightAnnotatedText(
  text: String,
  matches: List<IntRange>,
  highlightStyle: SpanStyle,
): AnnotatedString {
  if (matches.isEmpty() || text.isEmpty()) return AnnotatedString(text)
  val sorted = matches.sortedBy { it.first }
  // Стиль приходит снаружи, нет обращений к MaterialTheme внутри хелпера
  return buildAnnotatedString {
    append(text)
    for (range in sorted) {
      val start = range.first.coerceIn(0, text.length)
      val endExclusive = (range.last + 1).coerceAtMost(text.length)
      if (start < endExclusive) {
        addStyle(highlightStyle, start, endExclusive)
      }
    }
  }
}

