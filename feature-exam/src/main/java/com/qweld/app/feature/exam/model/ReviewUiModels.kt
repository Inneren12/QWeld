package com.qweld.app.feature.exam.model

import androidx.compose.runtime.Immutable

@Immutable
data class ReviewChoiceUiModel(
  val id: String,
  val label: String,
  val text: String,
  val isCorrect: Boolean,
  val isSelected: Boolean,
)

@Immutable
data class ReviewQuestionUiModel(
  val id: String,
  val taskId: String,
  val stem: String,
  val choices: List<ReviewChoiceUiModel>,
  val rationale: String?,
  val isCorrect: Boolean,
  val isFlagged: Boolean,
)
