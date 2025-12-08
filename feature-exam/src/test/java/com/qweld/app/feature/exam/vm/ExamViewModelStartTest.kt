package com.qweld.app.feature.exam.vm

import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.feature.exam.FakeUserPrefs
import com.qweld.app.feature.exam.fakes.FakeAnswerDao
import com.qweld.app.feature.exam.fakes.FakeAttemptDao
import com.qweld.app.feature.exam.fakes.FakeQuestionReportRepository
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

  @Test
  fun startAdaptiveEmitsNavigationEffectOnSuccess() = runTest {
    val repository = repositoryWithTasks("A-1" to 2, "B-1" to 2)
    val adaptiveBlueprint =
      ExamBlueprint(
        totalQuestions = 3,
        taskQuotas =
          listOf(
            TaskQuota("A-1", "A", 2),
            TaskQuota("B-1", "B", 1),
          ),
      )
    val viewModel = createViewModel(repository, adaptiveBlueprint)

    val effect = async { viewModel.effects.first() }

    val launched = viewModel.startAttempt(ExamMode.ADAPTIVE, locale = "en")

    assertTrue(launched)
    assertEquals(ExamViewModel.ExamEffect.NavigateToExam, effect.await())
    val attempt = viewModel.uiState.value.attempt
    assertNotNull(attempt)
    assertEquals(ExamMode.ADAPTIVE, attempt.mode)
    assertEquals(adaptiveBlueprint.totalQuestions, attempt.totalQuestions)
    val distinctTasks =
      attempt.questions
        .mapNotNull { question -> question.id.substringAfter("Q-", "").substringBeforeLast("-", "").takeIf { it.isNotBlank() } }
        .distinct()
    assertEquals(adaptiveBlueprint.taskQuotas.size, distinctTasks.size)
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
    val questionReportRepository = FakeQuestionReportRepository()
    val dispatcher = dispatcherRule.dispatcher
    return ExamViewModel(
      repository = repository,
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      statsRepository = statsRepository,
      userPrefs = FakeUserPrefs(),
      questionReportRepository = questionReportRepository,
      appEnv = com.qweld.app.feature.exam.vm.fakes.FakeAppEnv(),
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
