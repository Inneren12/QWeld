package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.vm.ExamViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
  resultData: ExamViewModel.ExamResultData,
  explanationRepository: AssetExplanationRepository,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val reviewQuestions = remember(resultData) { buildReviewQuestions(resultData) }
  val locale = resultData.attempt.locale
  var sheetQuestion by remember { mutableStateOf<ReviewQuestionUiModel?>(null) }
  var explanation by remember { mutableStateOf<AssetExplanationRepository.Explanation?>(null) }
  var isLoadingExplanation by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(id = R.string.review_title)) },
        navigationIcon = {
          TextButton(onClick = onBack) {
            Text(text = stringResource(id = R.string.review_back))
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
      )
    },
  ) { paddingValues ->
    if (reviewQuestions.isEmpty()) {
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
        itemsIndexed(reviewQuestions) { index, question ->
          ReviewQuestionCard(
            index = index,
            total = reviewQuestions.size,
            question = question,
            onExplain = {
              sheetQuestion = question
            },
          )
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
}

@Composable
private fun ReviewQuestionCard(
  index: Int,
  total: Int,
  question: ReviewQuestionUiModel,
  onExplain: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedChoice = question.choices.firstOrNull { it.isSelected }
  val correctChoice = question.choices.firstOrNull { it.isCorrect }
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
        text = question.stem,
        style = MaterialTheme.typography.titleMedium,
      )
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        question.choices.forEach { choice ->
          ChoiceSummary(choice = choice)
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
          Text(
            text = rationale,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        Button(onClick = onExplain) {
          Text(text = stringResource(id = R.string.review_explain))
        }
      }
    }
  }
}

@Composable
private fun ChoiceSummary(
  choice: ReviewChoiceUiModel,
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
          text = choice.text,
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

private fun buildReviewQuestions(resultData: ExamViewModel.ExamResultData): List<ReviewQuestionUiModel> {
  val locale = resultData.attempt.locale.lowercase(Locale.US)
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
    )
  }
}

data class ReviewQuestionUiModel(
  val id: String,
  val taskId: String,
  val stem: String,
  val choices: List<ReviewChoiceUiModel>,
  val rationale: String?,
)

data class ReviewChoiceUiModel(
  val id: String,
  val label: String,
  val text: String,
  val isCorrect: Boolean,
  val isSelected: Boolean,
)
