package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.export.AttemptExporter
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.exam.AssembledQuestion
import com.qweld.app.domain.exam.AttemptSeed
import com.qweld.app.domain.exam.Choice
import com.qweld.app.domain.exam.ExamAttempt
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.Question
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.feature.exam.model.PassStatus
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultViewModelTest {

  private val dummyExporter: AttemptExporter =
    AttemptExporter(
      attemptsRepository = AttemptsRepository(object : AttemptDao {
        override suspend fun insert(attempt: AttemptEntity) = throw UnsupportedOperationException()

        override suspend fun updateFinish(
          attemptId: String,
          finishedAt: Long?,
          durationSec: Int?,
          passThreshold: Int?,
          scorePct: Double?,
        ) = throw UnsupportedOperationException()

        override suspend fun markAborted(id: String, finishedAt: Long) = throw UnsupportedOperationException()

        override suspend fun getById(id: String): AttemptEntity? = throw UnsupportedOperationException()

        override suspend fun listRecent(limit: Int): List<AttemptEntity> = emptyList()
      }) { },
      answersRepository = AnswersRepository(
        object : AnswerDao {
          override suspend fun insertAll(answers: List<AnswerEntity>) = Unit

          override suspend fun listByAttempt(attemptId: String): List<AnswerEntity> = emptyList()

          override suspend fun countByQuestion(questionId: String): AnswerDao.QuestionAggregate? = null

          override suspend fun bulkCountByQuestions(questionIds: List<String>): List<AnswerDao.QuestionAggregate> = emptyList()
        }
      ),
      versionProvider = { "" },
      errorLogger = { _, _ -> },
    )

  @Test
  fun `score percent reflects correct answers`() {
    val resultData = buildResultData(
      mode = ExamMode.IP_MOCK,
      questionConfigs = listOf(
        QuestionConfig("A-1", "A", true),
        QuestionConfig("A-2", "A", false),
        QuestionConfig("B-1", "B", true),
        QuestionConfig("B-2", "B", false),
      ),
    )
    val viewModel = ResultViewModel(resultData, dummyExporter)

    assertEquals(50.0, viewModel.uiState.value.scorePercent)
    assertEquals(2, viewModel.uiState.value.totalCorrect)
  }

  @Test
  fun `pass status only applies to IP mock`() {
    val ipMockData = buildResultData(
      mode = ExamMode.IP_MOCK,
      questionConfigs = List(10) { index -> QuestionConfig("A-${index}", "A", index < 7) },
    )
    val practiceData = buildResultData(
      mode = ExamMode.PRACTICE,
      questionConfigs = List(5) { index -> QuestionConfig("B-${index}", "B", index < 4) },
    )

    val ipMockVm = ResultViewModel(ipMockData, dummyExporter)
    val practiceVm = ResultViewModel(practiceData, dummyExporter)

    assertEquals(PassStatus.Passed, ipMockVm.uiState.value.passStatus)
    assertNull(practiceVm.uiState.value.passStatus)
  }

  @Test
  fun `summaries group by block and task`() {
    val resultData = buildResultData(
      mode = ExamMode.IP_MOCK,
      questionConfigs = listOf(
        QuestionConfig("A-1", "A", true),
        QuestionConfig("A-1", "A", false),
        QuestionConfig("B-2", "B", true),
        QuestionConfig("C-3", "C", false),
      ),
    )
    val viewModel = ResultViewModel(resultData, dummyExporter)

    val blockSummaries = viewModel.uiState.value.blockSummaries.associateBy { it.blockId }
    assertEquals(2, blockSummaries.getValue("A").total)
    assertEquals(1, blockSummaries.getValue("A").correct)
    assertEquals(1, blockSummaries.getValue("B").correct)
    assertEquals(0, blockSummaries.getValue("C").correct)

    val taskSummaries = viewModel.uiState.value.taskSummaries.associateBy { it.taskId }
    assertEquals(2, taskSummaries.getValue("A-1").total)
    assertEquals(1, taskSummaries.getValue("A-1").correct)
    assertEquals("B", taskSummaries.getValue("B-2").blockId)
  }

  private fun buildResultData(
    mode: ExamMode,
    questionConfigs: List<QuestionConfig>,
  ): ExamViewModel.ExamResultData {
    val questions = questionConfigs.mapIndexed { index, config ->
      val correctChoiceId = "choice_${index}_correct"
      val incorrectChoiceId = "choice_${index}_wrong"
      val questionId = "q${index}"
      val choices = listOf(
        Choice(id = correctChoiceId, text = mapOf("en" to "Correct")),
        Choice(id = incorrectChoiceId, text = mapOf("en" to "Wrong")),
      )
      val question = Question(
        id = questionId,
        taskId = config.taskId,
        blockId = config.blockId,
        locale = "en",
        stem = mapOf("en" to "Stem"),
        choices = choices,
        correctChoiceId = correctChoiceId,
      )
      val assembled = AssembledQuestion(
        question = question,
        choices = choices,
        correctIndex = 0,
      )
      assembled to if (config.isCorrect) correctChoiceId else incorrectChoiceId
    }

    val blueprint = ExamBlueprint(
      totalQuestions = questionConfigs.size,
      taskQuotas = questionConfigs
        .groupBy { it.taskId }
        .map { (taskId, configs) ->
          TaskQuota(taskId = taskId, blockId = configs.first().blockId, required = configs.size)
        },
    )

    val attempt = ExamAttempt(
      mode = mode,
      locale = "en",
      seed = AttemptSeed(0),
      questions = questions.map { it.first },
      blueprint = blueprint,
    )

    val answers = questions.associate { (assembled, choiceId) -> assembled.question.id to choiceId }

    val correctCount = questionConfigs.count { it.isCorrect }
    val total = questionConfigs.size
    val scorePercent = if (total == 0) 0.0 else (correctCount.toDouble() / total.toDouble()) * 100.0
    val passThreshold = if (mode == ExamMode.IP_MOCK) 70 else null

    return ExamViewModel.ExamResultData(
      attemptId = "test-${mode.name.lowercase()}",
      attempt = attempt,
      answers = answers,
      remaining = Duration.ZERO,
      rationales = emptyMap(),
      scorePercent = scorePercent,
      passThreshold = passThreshold,
    )
  }

  private data class QuestionConfig(
    val taskId: String,
    val blockId: String,
    val isCorrect: Boolean,
  )
}
