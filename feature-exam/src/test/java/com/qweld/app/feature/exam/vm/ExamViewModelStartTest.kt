package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.TestIntegrity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelStartTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  @Test
  fun startIpMockEmitsNavigationEffectOnSuccess() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val viewModel = createViewModel(repository, blueprint)

    val effect = async { viewModel.effects.first() }

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    assertTrue(launched)
    assertEquals(ExamViewModel.ExamEffect.NavigateToExam, effect.await())
    val attempt = viewModel.uiState.value.attempt
    assertNotNull(attempt)
    assertEquals(2, attempt.totalQuestions)
    assertEquals(ExamMode.IP_MOCK, attempt.mode)
  }

  @Test
  fun startIpMockShowsDeficitWhenQuestionsMissing() = runTest {
    val repository = repositoryWithTasks("A-1" to 1)
    val blueprint = blueprint(required = 2)
    val viewModel = createViewModel(repository, blueprint)

    val effect = async { viewModel.effects.first() }

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    assertFalse(launched)
    val deficit = viewModel.uiState.value.deficitDialog
    assertNotNull(deficit)
    val detail = deficit.details.first()
    assertEquals("A-1", detail.taskId)
    assertEquals(2, detail.need)
    assertEquals(1, detail.have)
    val emitted = assertIs<ExamViewModel.ExamEffect.ShowDeficit>(effect.await())
    assertTrue(emitted.detail.contains("taskId=A-1"))
  }

  @Test
  fun startIpMockReportsAssemblyErrors() = runTest {
    val repository = repositoryWithTasks("A-1" to 2)
    val blueprint = blueprint(required = 2)
    val statsRepository =
      object : UserStatsRepository {
        override suspend fun getUserItemStats(userId: String, ids: List<String>) =
          Outcome.Err.IoFailure(IllegalStateException("stats failed"))
      }
    val viewModel = createViewModel(repository, blueprint, statsRepository)

    val effect = async { viewModel.effects.first() }

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")

    assertFalse(launched)
    val emitted = assertIs<ExamViewModel.ExamEffect.ShowError>(effect.await())
    assertEquals("stats failed", emitted.msg)
    assertEquals("stats failed", viewModel.uiState.value.errorMessage)
  }

  private fun repositoryWithTasks(
    vararg counts: Pair<String, Int>,
    locale: String = "en",
  ): AssetQuestionRepository {
    val payloads = counts.associate { (taskId, count) ->
      val json = questionArray(taskId, count, locale)
      "questions/$locale/tasks/$taskId.json" to json
    }
    val assets = TestIntegrity.addIndexes(payloads.mapValues { it.value.toByteArray() })
    return AssetQuestionRepository(
      assetReader = AssetQuestionRepository.AssetReader(opener = { path -> assets[path]?.inputStream() }),
      localeResolver = { locale },
      json = Json { ignoreUnknownKeys = true },
    )
  }

  private fun questionArray(taskId: String, count: Int, locale: String): String {
    return buildString {
      append('[')
      repeat(count) { index ->
        if (index > 0) append(',')
        val id = "Q-${taskId}-${index + 1}"
        val blockId = taskId.substringBefore('-', taskId)
        val choiceId = "${id}-A"
        append(
          """
          {
            \"id\": \"$id\",
            \"taskId\": \"$taskId\",
            \"blockId\": \"$blockId\",
            \"locale\": \"$locale\",
            \"stem\": { \"$locale\": \"Stem $id\" },
            \"choices\": [
              { \"id\": \"$choiceId\", \"text\": { \"$locale\": \"Option\" } }
            ],
            \"correctId\": \"$choiceId\"
          }
          """.trimIndent(),
        )
      }
      append(']')
    }
  }

  private fun blueprint(required: Int): ExamBlueprint {
    return ExamBlueprint(
      totalQuestions = required,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = required)),
    )
  }

  private fun createViewModel(
    repository: AssetQuestionRepository,
    blueprint: ExamBlueprint,
    statsRepository: UserStatsRepository = object : UserStatsRepository {
      override suspend fun getUserItemStats(
        userId: String,
        ids: List<String>,
      ): Outcome<Map<String, com.qweld.app.domain.exam.ItemStats>> = Outcome.Ok(emptyMap())
    },
  ): ExamViewModel {
    val attemptDao = FakeAttemptDao()
    val answerDao = FakeAnswerDao()
    val attemptsRepository = AttemptsRepository(attemptDao) { }
    val answersRepository = AnswersRepository(answerDao)
    val dispatcher = dispatcherRule.dispatcher
    return ExamViewModel(
      repository = repository,
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      statsRepository = statsRepository,
      blueprintProvider = { _, _ -> blueprint },
      seedProvider = { 1L },
      nowProvider = { 0L },
      timerController = com.qweld.app.domain.exam.TimerController { },
      ioDispatcher = dispatcher,
      prewarmController =
        PrewarmController(
          repository = repository,
          prewarmUseCase =
            PrewarmUseCase(
              repository,
              prewarmDisabled = flowOf(false),
              ioDispatcher = dispatcher,
              nowProvider = { 0L },
            ),
        ),
    )
  }
}

private class FakeAttemptDao : AttemptDao {
  private val attempts = mutableMapOf<String, AttemptEntity>()

  override suspend fun insert(attempt: AttemptEntity) {
    attempts[attempt.id] = attempt
  }

  override suspend fun updateFinish(
    attemptId: String,
    finishedAt: Long?,
    durationSec: Int?,
    passThreshold: Int?,
    scorePct: Double?,
  ) {
    val current = attempts[attemptId] ?: return
    attempts[attemptId] =
      current.copy(
        finishedAt = finishedAt,
        durationSec = durationSec,
        passThreshold = passThreshold,
        scorePct = scorePct,
      )
  }

  override suspend fun markAborted(id: String, finishedAt: Long) {
    val current = attempts[id] ?: return
    attempts[id] =
      current.copy(
        finishedAt = finishedAt,
        durationSec = null,
        passThreshold = null,
        scorePct = null,
      )
  }

  override suspend fun getById(id: String): AttemptEntity? = attempts[id]

  override suspend fun listRecent(limit: Int): List<AttemptEntity> {
    return attempts.values.sortedByDescending { it.startedAt }.take(limit)
  }

  override suspend fun getUnfinished(): AttemptEntity? {
    return attempts.values.filter { it.finishedAt == null }.maxByOrNull { it.startedAt }
  }
}

private class FakeAnswerDao : AnswerDao {
  private val answers = mutableListOf<AnswerEntity>()

  override suspend fun insertAll(answers: List<AnswerEntity>) {
    this.answers += answers
  }

  override suspend fun listByAttempt(attemptId: String): List<AnswerEntity> {
    return answers.filter { it.attemptId == attemptId }.sortedBy { it.displayIndex }
  }

  override suspend fun countByQuestion(questionId: String): AnswerDao.QuestionAggregate? {
    val relevant = answers.filter { it.questionId == questionId }
    if (relevant.isEmpty()) return null
    return AnswerDao.QuestionAggregate(
      questionId = questionId,
      attempts = relevant.size,
      correct = relevant.count { it.isCorrect },
      lastAnsweredAt = relevant.maxOfOrNull { it.answeredAt },
    )
  }

  override suspend fun bulkCountByQuestions(questionIds: List<String>): List<AnswerDao.QuestionAggregate> {
    val interested = questionIds.toSet()
    return answers
      .filter { it.questionId in interested }
      .groupBy { it.questionId }
      .map { (questionId, entries) ->
        AnswerDao.QuestionAggregate(
          questionId = questionId,
          attempts = entries.size,
          correct = entries.count { it.isCorrect },
          lastAnsweredAt = entries.maxOfOrNull { it.answeredAt },
        )
      }
  }
}
