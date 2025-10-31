package com.qweld.app.feature.exam.model

import com.qweld.app.domain.exam.ExamMode

data class ExamUiState(
  val isLoading: Boolean = false,
  val attempt: ExamAttemptUiState? = null,
  val deficitDialog: DeficitDialogUiModel? = null,
  val errorMessage: String? = null,
)

data class ExamAttemptUiState(
  val mode: ExamMode,
  val locale: String,
  val questions: List<ExamQuestionUiModel>,
  val currentIndex: Int,
) {
  val totalQuestions: Int get() = questions.size

  fun currentQuestion(): ExamQuestionUiModel? = questions.getOrNull(currentIndex)

  fun canGoNext(): Boolean = currentIndex < questions.lastIndex

  fun canGoPrevious(): Boolean = mode != ExamMode.IP_MOCK && currentIndex > 0

  fun progressLabel(): String = if (questions.isEmpty()) "0 / 0" else "${currentIndex + 1} / ${questions.size}"
}

data class ExamQuestionUiModel(
  val id: String,
  val stem: String,
  val choices: List<ExamChoiceUiModel>,
  val selectedChoiceId: String?,
) {
  val isAnswered: Boolean get() = selectedChoiceId != null
}

data class ExamChoiceUiModel(
  val id: String,
  val label: String,
  val text: String,
  val isSelected: Boolean,
)

data class DeficitDialogUiModel(
  val details: List<DeficitDetailUiModel>,
)

data class DeficitDetailUiModel(
  val taskId: String,
  val need: Int,
  val have: Int,
  val missing: Int,
)
