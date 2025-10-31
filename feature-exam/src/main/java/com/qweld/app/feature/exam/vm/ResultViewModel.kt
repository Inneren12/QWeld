package com.qweld.app.feature.exam.vm

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.mapTaskToBlock
import com.qweld.app.feature.exam.model.BlockSummaryUiModel
import com.qweld.app.feature.exam.model.PassStatus
import com.qweld.app.feature.exam.model.ResultUiState
import com.qweld.app.feature.exam.model.TaskSummaryUiModel
import java.util.Locale

class ResultViewModel(
  resultData: ExamViewModel.ExamResultData,
  private val taskToBlock: (String) -> String? = ::mapTaskToBlock,
) : ViewModel() {

  private val _uiState = mutableStateOf(createState(resultData))
  val uiState: State<ResultUiState> = _uiState

  private fun createState(resultData: ExamViewModel.ExamResultData): ResultUiState {
    val attempt = resultData.attempt
    val answers = resultData.answers
    val questionResults = attempt.questions.map { assembled ->
      val taskId = assembled.question.taskId
      val blockId = taskToBlock(taskId) ?: assembled.question.blockId
      val answered = answers[assembled.question.id]
      QuestionResult(
        questionId = assembled.question.id,
        taskId = taskId,
        blockId = blockId,
        isCorrect = answered != null && answered == assembled.question.correctChoiceId,
      )
    }
    val totalCorrect = questionResults.count { it.isCorrect }
    val totalQuestions = questionResults.size
    val scorePercent = percentage(totalCorrect, totalQuestions)
    val threshold = resultData.passThreshold?.toDouble() ?: PASS_THRESHOLD
    val passStatus = when (attempt.mode) {
      ExamMode.IP_MOCK -> if (scorePercent >= threshold) PassStatus.Passed else PassStatus.Failed
      else -> null
    }
    val blockSummaries = questionResults
      .groupBy { it.blockId }
      .map { (blockId, results) ->
        BlockSummaryUiModel(
          blockId = blockId,
          correct = results.count { it.isCorrect },
          total = results.size,
          scorePercent = percentage(results.count { it.isCorrect }, results.size),
        )
      }
      .sortedBy { it.blockId }
    val taskSummaries = questionResults
      .groupBy { it.taskId }
      .map { (taskId, results) ->
        val blockId = taskToBlock(taskId) ?: results.first().blockId
        TaskSummaryUiModel(
          taskId = taskId,
          blockId = blockId,
          correct = results.count { it.isCorrect },
          total = results.size,
          scorePercent = percentage(results.count { it.isCorrect }, results.size),
        )
      }
      .sortedBy { it.taskId }
    return ResultUiState(
      mode = attempt.mode,
      totalCorrect = totalCorrect,
      totalQuestions = totalQuestions,
      scorePercent = scorePercent,
      passStatus = passStatus,
      blockSummaries = blockSummaries,
      taskSummaries = taskSummaries,
      timeLeftLabel = resultData.remaining?.let { TimerController.formatDuration(it) },
    )
  }

  private fun percentage(correct: Int, total: Int): Double {
    if (total == 0) return 0.0
    return (correct.toDouble() / total.toDouble()) * 100.0
  }

  fun scoreLabel(): String {
    val state = _uiState.value
    return String.format(Locale.US, "%.1f%%", state.scorePercent)
  }

  private data class QuestionResult(
    val questionId: String,
    val taskId: String,
    val blockId: String,
    val isCorrect: Boolean,
  )

  companion object {
    private const val PASS_THRESHOLD = 70.0
  }
}

class ResultViewModelFactory(
  private val resultDataProvider: () -> ExamViewModel.ExamResultData,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(ResultViewModel::class.java)) {
      return ResultViewModel(resultDataProvider()) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
  }
}
