package com.qweld.app.feature.exam.model

import com.qweld.app.domain.exam.ExamMode

data class ResultUiState(
  val mode: ExamMode,
  val totalCorrect: Int,
  val totalQuestions: Int,
  val scorePercent: Double,
  val passStatus: PassStatus?,
  val blockSummaries: List<BlockSummaryUiModel>,
  val taskSummaries: List<TaskSummaryUiModel>,
  val timeLeftLabel: String?,
)

sealed interface PassStatus {
  data object Passed : PassStatus
  data object Failed : PassStatus
}

data class BlockSummaryUiModel(
  val blockId: String,
  val correct: Int,
  val total: Int,
  val scorePercent: Double,
)

data class TaskSummaryUiModel(
  val taskId: String,
  val blockId: String,
  val correct: Int,
  val total: Int,
  val scorePercent: Double,
)
