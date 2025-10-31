package com.qweld.app.feature.exam.vm

import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class ExamViewModelTest {

  @Test
  fun startIpMockCreatesAttempt() {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel = createViewModel(repository)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    assertTrue(launched)
    val attempt = viewModel.uiState.value.attempt
    assertNotNull(attempt)
    assertEquals(ExamMode.IP_MOCK, attempt.mode)
    assertEquals(2, attempt.totalQuestions)
    assertEquals(0, attempt.currentIndex)
  }

  @Test
  fun practiceModeAllowsPreviousNavigation() {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel = createViewModel(repository)

    val launched = viewModel.startAttempt(ExamMode.PRACTICE, locale = "en")

    assertTrue(launched)
    viewModel.nextQuestion()
    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex)
    viewModel.previousQuestion()
    assertEquals(0, viewModel.uiState.value.attempt?.currentIndex)
  }

  @Test
  fun ipMockDisablesPreviousNavigation() {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel = createViewModel(repository)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    assertTrue(launched)
    viewModel.nextQuestion()
    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex)
    viewModel.previousQuestion()
    assertEquals(1, viewModel.uiState.value.attempt?.currentIndex)
  }

  @Test
  fun submitAnswerLocksChoice() {
    val repository = repositoryWithQuestions(count = 2)
    val viewModel = createViewModel(repository)

    val launched = viewModel.startAttempt(ExamMode.PRACTICE, locale = "en")

    assertTrue(launched)
    val firstQuestion = viewModel.uiState.value.attempt?.currentQuestion()
    val initialChoice = firstQuestion?.choices?.firstOrNull()
    requireNotNull(initialChoice)

    viewModel.submitAnswer(initialChoice.id)
    var updatedQuestion = viewModel.uiState.value.attempt?.currentQuestion()
    assertTrue(updatedQuestion?.isAnswered == true)
    assertEquals(initialChoice.id, updatedQuestion?.selectedChoiceId)

    val alternativeChoice = updatedQuestion?.choices?.getOrNull(1)
    if (alternativeChoice != null) {
      viewModel.submitAnswer(alternativeChoice.id)
    }
    updatedQuestion = viewModel.uiState.value.attempt?.currentQuestion()
    assertEquals(initialChoice.id, updatedQuestion?.selectedChoiceId)
  }

  @Test
  fun startAttemptShowsDeficitDialogWhenBankSmall() {
    val repository = repositoryWithQuestions(count = 1)
    val viewModel = createViewModel(repository)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    assertFalse(launched)
    assertNull(viewModel.uiState.value.attempt)
    val dialog = viewModel.uiState.value.deficitDialog
    assertNotNull(dialog)
    val detail = dialog.details.first()
    assertEquals("A-1", detail.taskId)
    assertEquals(2, detail.need)
    assertEquals(1, detail.have)
  }

  private fun createViewModel(repository: AssetQuestionRepository): ExamViewModel {
    val blueprint = ExamBlueprint(
      totalQuestions = 2,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = 2)),
    )
    return ExamViewModel(
      repository = repository,
      blueprintProvider = { _, _ -> blueprint },
      seedProvider = { 1L },
    )
  }

  private fun repositoryWithQuestions(count: Int): AssetQuestionRepository {
    val questions = buildString {
      append("[")
      repeat(count) { index ->
        if (index > 0) append(",")
        val qId = "Q${index + 1}"
        val choiceIds = listOf("A", "B", "C", "D").map { suffix -> "${qId}-$suffix" }
        append(
          """
          {
            \"id\": \"$qId\",
            \"taskId\": \"A-1\",
            \"blockId\": \"A\",
            \"locale\": \"en\",
            \"stem\": { \"en\": \"Stem $qId\" },
            \"choices\": [
              { \"id\": \"${choiceIds[0]}\", \"text\": { \"en\": \"Choice 1\" } },
              { \"id\": \"${choiceIds[1]}\", \"text\": { \"en\": \"Choice 2\" } },
              { \"id\": \"${choiceIds[2]}\", \"text\": { \"en\": \"Choice 3\" } },
              { \"id\": \"${choiceIds[3]}\", \"text\": { \"en\": \"Choice 4\" } }
            ],
            \"correctId\": \"${choiceIds[0]}\"
          }
          """.trimIndent(),
        )
      }
      append("]")
    }
    return AssetQuestionRepository(
      assetReader = AssetQuestionRepository.AssetReader { questions.byteInputStream() },
      localeResolver = { "en" },
      json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
    )
  }
}
