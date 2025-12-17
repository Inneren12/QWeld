package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.repo.DefaultAnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.FakeUserPrefs
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.TestIntegrity
import com.qweld.app.feature.exam.fakes.FakeAnswerDao
import com.qweld.app.feature.exam.fakes.FakeQuestionReportRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Ignore("Pending ExamViewModel alignment")
class ExamViewModelAbortRestartTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  @Test
  fun abortAttemptMarksAttemptAndNavigates() = runTest {
    val attemptDao = RecordingAttemptDao()
    val ids = ArrayDeque(listOf("attempt-1", "attempt-2"))
    val viewModel = createViewModel(attemptDao, ids)
    val blueprint = practiceBlueprint(required = 1)

    val launched = viewModel.startAttempt(
      mode = ExamMode.PRACTICE,
      locale = "en",
      practiceConfig = PracticeConfig(size = 1, scope = PracticeScope(taskIds = setOf("A-1"))),
      blueprintOverride = blueprint,
    )
    assertTrue(launched)

    val navigate = async { viewModel.effects.first { it == ExamViewModel.ExamEffect.NavigateToMode } }

    viewModel.abortAttempt()

    assertEquals(listOf("attempt-1"), attemptDao.abortedIds)
    assertTrue(navigate.await() === ExamViewModel.ExamEffect.NavigateToMode)
    assertNull(viewModel.uiState.value.attempt)
  }

  @Test
  fun restartPracticeEmitsEffectWithStoredConfig() = runTest {
    val attemptDao = RecordingAttemptDao()
    val ids = ArrayDeque(listOf("practice-1", "practice-2"))
    val viewModel = createViewModel(attemptDao, ids)
    val config = PracticeConfig(size = 2, scope = PracticeScope(taskIds = setOf("A-1")))
    val blueprint = practiceBlueprint(required = 2)

    val launched = viewModel.startAttempt(
      mode = ExamMode.PRACTICE,
      locale = "en",
      practiceConfig = config,
      blueprintOverride = blueprint,
    )
    assertTrue(launched)

    val effect = async { viewModel.effects.first { it == ExamViewModel.ExamEffect.RestartWithSameConfig } }

    viewModel.restartAttempt()
    runCurrent()

    assertEquals(listOf("practice-1"), attemptDao.abortedIds)
    assertIs<ExamViewModel.ExamEffect.RestartWithSameConfig>(effect.await())
    assertNull(viewModel.uiState.value.attempt)
    assertEquals(
      config.copy(size = PracticeConfig.sanitizeSize(config.size)),
      viewModel.uiState.value.lastPracticeConfig,
    )
  }

  @Test
  fun restartIpMockCreatesNewAttempt() = runTest {
    val attemptDao = RecordingAttemptDao()
    val ids = ArrayDeque(listOf("ip-1", "ip-2"))
    val viewModel = createViewModel(attemptDao, ids)

    val launched = viewModel.startAttempt(ExamMode.IP_MOCK, locale = "en")
    assertTrue(launched)
    runCurrent()
    assertEquals("ip-1", viewModel.uiState.value.attempt?.attemptId)

    viewModel.restartAttempt()
    runCurrent()

    assertEquals(listOf("ip-1"), attemptDao.abortedIds)
    assertEquals(listOf("ip-1", "ip-2"), attemptDao.savedIds)
    assertEquals("ip-2", viewModel.uiState.value.attempt?.attemptId)
  }

  private fun createViewModel(
    attemptDao: RecordingAttemptDao,
    attemptIds: ArrayDeque<String>,
  ): ExamViewModel {
    val repository = repositoryWithTasks("A-1" to 3)
    val attemptsRepository = AttemptsRepository(attemptDao)
    val answersRepository = DefaultAnswersRepository(FakeAnswerDao())
    val questionReportRepository = FakeQuestionReportRepository()
    val dispatcher = dispatcherRule.dispatcher
    val blueprint = ipBlueprint(required = 1)
    val statsRepository = object : UserStatsRepository {
      override suspend fun getUserItemStats(
        userId: String,
        ids: List<String>,
      ): Outcome<Map<String, com.qweld.app.domain.exam.ItemStats>> = Outcome.Ok(emptyMap())
    }
    val blueprintResolver = createTestBlueprintResolver(blueprint)
    val resumeUseCase = createTestResumeUseCase(repository, statsRepository, blueprint, dispatcher)
    return ExamViewModel(
      repository = repository,
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      statsRepository = statsRepository,
      userPrefs = FakeUserPrefs(),
      questionReportRepository = questionReportRepository,
      appEnv = com.qweld.app.feature.exam.vm.fakes.FakeAppEnv(),
      blueprintResolver = blueprintResolver,
      resumeUseCase = resumeUseCase,
      seedProvider = { 1L },
      attemptIdProvider = { attemptIds.removeFirst() },
      nowProvider = { 1_000L },
      timerController = com.qweld.app.domain.exam.TimerController { },
      ioDispatcher = dispatcher,
      prewarmRunner =
        DefaultPrewarmController(
          repository = repository,
          prewarmUseCase =
            PrewarmUseCase(
              repository,
              flowOf(true),
              ioDispatcher = dispatcher,
              nowProvider = { 0L },
            ),
        ),
    )
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

  private fun practiceBlueprint(required: Int): ExamBlueprint {
    return ExamBlueprint(
      totalQuestions = required,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = required)),
    )
  }

  private fun ipBlueprint(required: Int): ExamBlueprint {
    return ExamBlueprint(
      totalQuestions = required,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = required)),
    )
  }

  private class RecordingAttemptDao : AttemptDao {
    private val attempts = mutableMapOf<String, AttemptEntity>()
    val savedIds = mutableListOf<String>()
    val abortedIds = mutableListOf<String>()

    override suspend fun insert(attempt: AttemptEntity) {
      attempts[attempt.id] = attempt
      savedIds += attempt.id
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

    override suspend fun updateRemainingTime(attemptId: String, remainingTimeMs: Long?) {
      // No-op for tests
    }

    override suspend fun markAborted(id: String, finishedAt: Long) {
      abortedIds += id
      val current = attempts[id]
      if (current != null) {
        attempts[id] =
          current.copy(
            finishedAt = finishedAt,
            durationSec = null,
            passThreshold = null,
            scorePct = null,
          )
      }
    }

    override suspend fun getById(id: String): AttemptEntity? = attempts[id]

    override suspend fun listRecent(limit: Int): List<AttemptEntity> = attempts.values.take(limit)

    override suspend fun getUnfinished(): AttemptEntity? = attempts.values.firstOrNull { it.finishedAt == null }

    override suspend fun getLastFinished(): AttemptEntity? = attempts.values.lastOrNull { it.finishedAt != null }

      override suspend fun getAttemptStats(): AttemptDao.AttemptStatsRow {
          val all = attempts.values
          val total = all.size
          val finished = all.count { it.finishedAt != null }
          val inProgress = all.count { it.finishedAt == null }
          val failed = all.count { entity ->
              val threshold = entity.passThreshold
              val score = entity.scorePct
              threshold != null && score != null && score < threshold.toDouble()
          }
          val lastFinishedAt = all.mapNotNull { it.finishedAt }.maxOrNull()

          return AttemptDao.AttemptStatsRow(
              totalCount = total,
              finishedCount = finished,
              inProgressCount = inProgress,
              failedCount = failed,
              lastFinishedAt = lastFinishedAt,
              )
      }

      override suspend fun getUserVersion(query: SupportSQLiteQuery): Int = 0

      override suspend fun clearAll() {
      attempts.clear()
      savedIds.clear()
      abortedIds.clear()
    }
  }
}
