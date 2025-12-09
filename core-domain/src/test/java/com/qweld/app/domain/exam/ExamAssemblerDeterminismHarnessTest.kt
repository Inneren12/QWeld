package com.qweld.app.domain.exam

import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import java.time.Clock
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ExamAssemblerDeterminismHarnessTest {
  private val blueprint =
    ExamBlueprint(
      totalQuestions = 4,
      taskQuotas =
        listOf(
          TaskQuota("A-1", "A", 2),
          TaskQuota("B-1", "B", 2),
        ),
    )

  private val questions = generateQuestions("A-1", 8) + generateQuestions("B-1", 8)

  private val seed = AttemptSeed(424242L)

  @Test
  fun `dispatcher choice does not affect assembly`() {
    val fastLogs = mutableListOf<String>()
    val unconfinedLogs = mutableListOf<String>()
    val fastAssembler =
      assembler(
        dispatcher = Dispatchers.Default,
        logger = fastLogs::add,
      )
    val unconfinedAssembler =
      assembler(
        dispatcher = Dispatchers.Unconfined,
        logger = unconfinedLogs::add,
      )

    val fast = assembleOk(fastAssembler)
    val unconfined = assembleOk(unconfinedAssembler)

    assertAttemptsEqual(fast, unconfined)
    assertLogsEquivalent(fastLogs, unconfinedLogs)
    println("[determinism] status=equal seed=${seed.value} scenario=dispatcher")
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `timeouts do not change successful assembly`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val reference = runWithTimeout(5L) { logs -> assembler(dispatcher = dispatcher, logger = logs::add) }

    listOf(500L, 5000L)
      .map { timeout -> runWithTimeout(timeout) { logs -> assembler(dispatcher = dispatcher, logger = logs::add) } }
      .forEach { attemptInfo ->
        assertAttemptsEqual(reference.attempt, attemptInfo.attempt)
        assertLogsEquivalent(reference.logs, attemptInfo.logs)
      }
    println("[determinism] status=equal seed=${seed.value} scenario=timeout")
  }

  @Test
  fun `variable io latency does not affect assembly`() {
    val fastLogs = mutableListOf<String>()
    val slowLogs = mutableListOf<String>()
    val fast =
      assembler(
        questionRepository = SlowQuestionRepository(questions, 1L),
        statsRepository = SlowUserStatsRepository(delayMillis = 1L),
        logger = fastLogs::add,
      )
    val slow =
      assembler(
        questionRepository = SlowQuestionRepository(questions, 10L),
        statsRepository = SlowUserStatsRepository(delayMillis = 10L),
        logger = slowLogs::add,
      )

    val fastAttempt = assembleOk(fast)
    val slowAttempt = assembleOk(slow)

    assertAttemptsEqual(fastAttempt, slowAttempt)
    assertLogsEquivalent(fastLogs, slowLogs)
    println("[determinism] status=equal seed=${seed.value} scenario=io")
  }

  private fun assembler(
    dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    questionRepository: QuestionRepository = FakeQuestionRepository(questions),
    statsRepository: UserStatsRepository = FakeUserStatsRepository(),
    logger: (String) -> Unit = {},
  ): ExamAssembler =
    ExamAssembler(
      questionRepository = questionRepository,
      statsRepository = statsRepository,
      clock = Clock.systemUTC(),
      logger = logger,
      dispatcher = dispatcher,
    )

  private fun assembleOk(assembler: ExamAssembler): ExamAttempt {
    val result =
      runBlocking {
        assembler.assemble(
          userId = "user",
          mode = ExamMode.PRACTICE,
          locale = "EN",
          seed = seed,
          blueprint = blueprint,
        )
      }
    return when (result) {
      is Outcome.Ok -> result.value.exam
      is Outcome.Err.QuotaExceeded ->
        error("Unexpected quota deficit for task ${result.taskId}")
      is Outcome.Err -> error("Unexpected outcome ${result::class.simpleName}")
    }
  }

  private suspend inline fun runWithTimeout(
    timeoutMillis: Long,
    crossinline assemblerFactory: (MutableList<String>) -> ExamAssembler,
  ): TimeoutRun {
    val logs = mutableListOf<String>()
    val asm = assemblerFactory(logs)
    val result = withTimeout(timeoutMillis) {
      asm.assemble(
        userId = "user",
        mode = ExamMode.PRACTICE,
        locale = "EN",
        seed = seed,
        blueprint = blueprint,
      )
    }
    val attempt =
      when (result) {
        is Outcome.Ok -> result.value.exam
        is Outcome.Err.QuotaExceeded ->
          error("Unexpected quota deficit for task ${result.taskId}")
        is Outcome.Err -> error("Unexpected outcome ${result::class.simpleName}")
      }
    return TimeoutRun(attempt, logs.toList())
  }

  private data class TimeoutRun(
    val attempt: ExamAttempt,
    val logs: List<String>,
  )

  private fun assertAttemptsEqual(expected: ExamAttempt, actual: ExamAttempt) {
    assertEquals(
      expected.questions.map { it.question.id },
      actual.questions.map { it.question.id },
    )
    expected.questions.zip(actual.questions).forEach { (lhs, rhs) ->
      assertEquals(lhs.correctIndex, rhs.correctIndex)
      assertEquals(lhs.choices.map { it.id }, rhs.choices.map { it.id })
    }
  }

  private fun assertLogsEquivalent(expected: List<String>, actual: List<String>) {
      // Логи таймера [timer_tick] завязаны на конкретный момент тика и
      // могут отличаться на 1 секунду, при этом сама сборка экзамена
      // остаётся детерминированной. Для целей этого теста игнорируем
      // такие записи и сравниваем только "структурные" логи сборки.
      val filteredExpected = expected.filterNot { it.startsWith("[timer_tick]") }
      val filteredActual = actual.filterNot { it.startsWith("[timer_tick]") }

      assertEquals(filteredExpected.size, filteredActual.size)
      filteredExpected.zip(filteredActual).forEach { (lhs, rhs) ->
          assertEquals(normalizeLog(lhs), normalizeLog(rhs))
      }
  }

  private fun normalizeLog(entry: String): String =
    entry
      .replace(Regex("timeLeft=\\S+"), "timeLeft=?")
      // Значение remain зависит от точного момента тика таймера,
      // для детерминизма нам важен сам факт события, а не конкретное время.
      .replace(Regex("remain=\\S+"), "remain=?")
    // remain тоже зависит от точного момента тика;
    // оставляем только сам факт события.
      .replace(Regex("remain=\\S+"), "remain=?")
      .replace(Regex("startedAt=\\S+"), "startedAt=?")

  private class SlowQuestionRepository(
    questions: List<Question>,
    private val delayMillis: Long,
  ) : QuestionRepository {
    private val delegate = FakeQuestionRepository(questions)

    override fun listByTaskAndLocale(
      taskId: String,
      locale: String,
      allowFallbackToEnglish: Boolean,
    ): Outcome<List<Question>> {
      if (delayMillis > 0) {
        Thread.sleep(delayMillis)
      }
      return delegate.listByTaskAndLocale(taskId, locale, allowFallbackToEnglish)
    }
  }

  private class SlowUserStatsRepository(
    private val stats: Map<String, ItemStats> = emptyMap(),
    private val delayMillis: Long,
  ) : UserStatsRepository {
    override suspend fun getUserItemStats(
      userId: String,
      ids: List<String>,
    ): Outcome<Map<String, ItemStats>> {
      if (delayMillis > 0) {
        delay(delayMillis)
      }
      return Outcome.Ok(ids.mapNotNull { id -> stats[id]?.let { id to it } }.toMap())
    }
  }
}
