package com.qweld.app.domain.exam

import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.domain.exam.util.computeWeight
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ExamPropertyContractsTest {
  private val propertyBlueprints: List<ExamBlueprint> =
    listOf(
      ExamBlueprint.default(),
      blueprintOf(
        TaskQuota("A-1", "A", 6),
        TaskQuota("A-2", "A", 6),
        TaskQuota("B-1", "B", 6),
        TaskQuota("C-1", "C", 6),
      ),
      blueprintOf(
        TaskQuota("A-3", "A", 4),
        TaskQuota("B-6", "B", 4),
        TaskQuota("C-8", "C", 4),
        TaskQuota("D-12", "D", 4),
      ),
      blueprintOf(
        TaskQuota("A-5", "A", 3),
        TaskQuota("B-7", "B", 3),
        TaskQuota("C-9", "C", 3),
        TaskQuota("D-14", "D", 3),
      ),
    )

  private val DEFAULT_SEED_RANGE: LongRange = 0L..499L

  companion object {
    private const val TASK_STREAK_LIMIT: Int = 3
    private const val BLOCK_STREAK_LIMIT: Int = 5
    private const val BALANCE_STD_DEV_FRACTION: Double = 0.1
  }

  @Test
  fun `anti cluster contract holds across blueprints`() {
    val taskRunLimit = TASK_STREAK_LIMIT
    val blockRunLimit = BLOCK_STREAK_LIMIT
    val seedRange = DEFAULT_SEED_RANGE
    propertyBlueprints.forEach { blueprint ->
      val questions = questionBankForBlueprint(blueprint, multiplier = 6)
      val assembler = createAssembler(questions)
      repeatSeeds(seedRange) { seed ->
        val exam = assembleExam(assembler, blueprint, seed, ExamMode.IP_MOCK)
        val taskRun = exam.questions.maxRunLengthBy { it.question.taskId }
        val blockRun = exam.questions.maxRunLengthBy { it.question.blockId }
        assertTrue(
          taskRun <= taskRunLimit,
          "task run $taskRun exceeds limit $taskRunLimit for blueprint ${blueprint.totalQuestions}",
        )
        assertTrue(
          blockRun <= blockRunLimit,
          "block run $blockRun exceeds limit $blockRunLimit for blueprint ${blueprint.totalQuestions}",
        )
      }
    }
  }

  @Test
  fun `answer balance contract holds across seeds`() {
    val seedRange = DEFAULT_SEED_RANGE
    propertyBlueprints.forEach { blueprint ->
      val questions = questionBankForBlueprint(blueprint, multiplier = 6)
      val assembler = createAssembler(questions)
      val aggregated = mutableMapOf("A" to 0L, "B" to 0L, "C" to 0L, "D" to 0L)
      repeatSeeds(seedRange) { seed ->
        val exam = assembleExam(assembler, blueprint, seed, ExamMode.IP_MOCK)
        val histogram = exam.questions.histogramOfCorrectPositions()
        histogram.forEach { (label, count) ->
          aggregated[label] = aggregated.getValue(label) + count
        }
      }
      val counts = aggregated.values.map { it.toDouble() }
      val mean = counts.average()
      val variance = counts.map { (it - mean).pow(2) }.average()
      val stdDev = sqrt(variance)
      val allowed = mean * BALANCE_STD_DEV_FRACTION
      assertTrue(
        stdDev <= allowed,
        "[tests] seeds=${seedRange.first}..${seedRange.last} reason=position stdev %.4f exceeds %.4f for blueprint %d"
          .format(stdDev, allowed, blueprint.totalQuestions),
      )
    }
  }

  @Test
  fun `determinism contract yields stable exams`() {
    val seedRange = DEFAULT_SEED_RANGE
    propertyBlueprints.forEach { blueprint ->
      val questions = questionBankForBlueprint(blueprint, multiplier = 6)
      repeatSeeds(seedRange) { seed ->
        val assembler1 = createAssembler(questions)
        val assembler2 = createAssembler(questions)
        val exam1 = assembleExam(assembler1, blueprint, seed, ExamMode.IP_MOCK)
        val exam2 = assembleExam(assembler2, blueprint, seed, ExamMode.IP_MOCK)
        assertEquals(
          exam1.questions.map { it.question.id },
          exam2.questions.map { it.question.id },
        )
        assertEquals(
          exam1.questions.map { it.correctIndex },
          exam2.questions.map { it.correctIndex },
        )
        assertEquals(exam1.toStableJson(), exam2.toStableJson())
      }
    }
  }

  @Test
  fun `weighted practice contract respects decay and novelty`() {
    val config = ExamAssemblyConfig()
    val practiceBlueprint =
      blueprintOf(
        TaskQuota("A-1", "A", 3),
        TaskQuota("A-2", "A", 3),
        TaskQuota("B-6", "B", 3),
        TaskQuota("C-8", "C", 3),
      )
    val trackedTaskId = "A-1"
    val questions = practiceQuestionBank(practiceBlueprint, trackedTaskId)
    val baseInstant = Instant.parse("2024-01-01T00:00:00Z")
    val clock = MutableClock(baseInstant)
    val statsMap = initialiseStats(questions, trackedTaskId, clock.instant())
    val statsRepository = MutableStatsRepository(statsMap)
    val assembler = createAssembler(questions, statsRepository, clock, config)
    val baselineNewRatio = computeBaselineNewRatio(questions, statsMap)
    val exposures = mutableListOf<Exposure>()
    val trackedQuestionId = questions.first { it.taskId == trackedTaskId }.id

    repeat(100) { session ->
      val exam = assembleExam(assembler, practiceBlueprint, session.toLong(), ExamMode.PRACTICE)
      exam.questions.forEach { assembled ->
        val statsBefore = statsMap[assembled.question.id]
        val wasNew = (statsBefore?.attempts ?: 0) == 0
        val weightBefore = computeWeight(statsBefore, config, clock.instant())
        exposures +=
          Exposure(
            questionId = assembled.question.id,
            wasNew = wasNew,
            weightBefore = weightBefore,
            correctBefore = statsBefore?.correct ?: 0,
            attemptsBefore = statsBefore?.attempts ?: 0,
          )
        val updated =
          (statsBefore
            ?: ItemStats(
              questionId = assembled.question.id,
              attempts = 0,
              correct = 0,
              lastAnsweredAt = null,
              lastAnswerCorrect = null,
            ))
            .copy(
              attempts = (statsBefore?.attempts ?: 0) + 1,
              correct = (statsBefore?.correct ?: 0) + 1,
              lastAnsweredAt = clock.instant(),
              lastAnswerCorrect = true,
            )
        statsMap[assembled.question.id] = updated
      }
      clock.advanceHours(6)
    }

    val minWeight = config.minWeight
    val maxWeight = config.maxWeight
    exposures.forEach { exposure ->
      assertTrue(
        exposure.weightBefore + 1e-9 >= minWeight && exposure.weightBefore <= maxWeight + 1e-9,
        "weight ${exposure.weightBefore} out of [$minWeight, $maxWeight] for question ${exposure.questionId}",
      )
    }

    val trackedWeights = exposures.filter { it.questionId == trackedQuestionId }
    val postNovelty = trackedWeights.filter { it.attemptsBefore > 0 }
    if (postNovelty.size >= 2) {
      val expectedRatio = 0.5.pow(1.0 / config.halfLifeCorrect)
      val relevantPairs =
        postNovelty.zipWithNext().filter { (current, next) ->
          current.weightBefore > minWeight + 1e-9 && next.weightBefore > minWeight + 1e-9
        }
      relevantPairs.forEach { (current, next) ->
        val ratio = next.weightBefore / current.weightBefore
        assertTrue(
          ratio.isCloseTo(expectedRatio, 0.1),
          "decay ratio $ratio deviates from $expectedRatio for ${current.questionId}",
        )
      }
      assertTrue(
        relevantPairs.isNotEmpty(),
        "[tests] seed=0 reason=insufficient decay samples before hitting min weight",
      )
    } else {
      error("tracked question did not repeat enough to evaluate decay")
    }

    val firstN = exposures.take(20)
    val observedNewRatio = firstN.count { it.wasNew }.toDouble() / firstN.size
    assertTrue(
      observedNewRatio > baselineNewRatio,
      "[tests] seed=0 reason=new ratio $observedNewRatio not greater than baseline $baselineNewRatio",
    )
  }

  private fun assembleExam(
    assembler: ExamAssembler,
    blueprint: ExamBlueprint,
    seed: Long,
    mode: ExamMode,
  ): ExamAttempt {
    val result =
      runBlocking {
        assembler.assemble(
          userId = "user",
          mode = mode,
          locale = "EN",
          seed = AttemptSeed(seed),
          blueprint = blueprint,
        )
      }
    return when (result) {
      is Outcome.Ok -> result.value.exam
      is Outcome.Err.QuotaExceeded ->
        error(
          "[tests] seed=$seed reason=deficit task=${result.taskId} missing=${result.required - result.have}"
        )
      is Outcome.Err ->
        error("[tests] seed=$seed reason=unexpected outcome ${result::class.simpleName}")
    }
  }

  private fun blueprintOf(vararg quotas: TaskQuota): ExamBlueprint {
    val list = quotas.toList()
    return ExamBlueprint(totalQuestions = list.sumOf { it.required }, taskQuotas = list)
  }

  private fun questionBankForBlueprint(
    blueprint: ExamBlueprint,
    multiplier: Int,
  ): List<Question> {
    return blueprint.taskQuotas.flatMap { quota ->
      generateQuestions(
        taskId = quota.taskId,
        count = quota.required * multiplier,
        blockId = quota.blockId,
        familyIdProvider = { index -> "${quota.taskId}-fam-$index" },
      )
    }
  }

  private fun practiceQuestionBank(
    blueprint: ExamBlueprint,
    trackedTaskId: String,
  ): List<Question> {
    return blueprint.taskQuotas.flatMap { quota ->
      val multiplier = if (quota.taskId == trackedTaskId) 1 else 4
      generateQuestions(
        taskId = quota.taskId,
        count = quota.required * multiplier,
        blockId = quota.blockId,
        familyIdProvider = { index -> "${quota.taskId}-fam-$index" },
      )
    }
  }

  private fun initialiseStats(
    questions: List<Question>,
    trackedTaskId: String,
    now: Instant,
  ): MutableMap<String, ItemStats> {
    val stats = mutableMapOf<String, ItemStats>()
    questions.forEachIndexed { index, question ->
      if (question.taskId == trackedTaskId) {
        return@forEachIndexed
      }
      val isNew = index % 3 == 0
      if (!isNew) {
        stats[question.id] =
          ItemStats(
            questionId = question.id,
            attempts = 6,
            correct = 4,
            lastAnsweredAt = now.minusSeconds(12 * 3600),
            lastAnswerCorrect = true,
          )
      }
    }
    return stats
  }

  private fun computeBaselineNewRatio(
    questions: List<Question>,
    stats: Map<String, ItemStats>,
  ): Double {
    val newCount = questions.count { stats[it.id]?.attempts ?: 0 == 0 }
    return newCount.toDouble() / questions.size
  }

  private fun createAssembler(
    questions: List<Question>,
    statsRepository: UserStatsRepository = FakeUserStatsRepository(),
    clock: Clock = fixedClock(),
    config: ExamAssemblyConfig = ExamAssemblyConfig(),
  ): ExamAssembler {
    return ExamAssembler(
      questionRepository = FakeQuestionRepository(questions),
      statsRepository = statsRepository,
      clock = clock,
      config = config,
      logger = {},
    )
  }

  private fun ExamAttempt.toStableJson(): String {
    return buildString {
      append('{')
      append("\"mode\":\"${mode.name}\",")
      append("\"locale\":\"$locale\",")
      append("\"seed\":${seed.value},")
      append("\"blueprint\":${blueprint.toStableJson()},")
      append("\"questions\":[")
      questions.forEachIndexed { index, question ->
        if (index > 0) append(',')
        append(question.toStableJson())
      }
      append(']')
      append('}')
    }
  }

  private fun ExamBlueprint.toStableJson(): String {
    return buildString {
      append('{')
      append("\"total\":$totalQuestions,")
      append("\"quotas\":[")
      taskQuotas.forEachIndexed { index, quota ->
        if (index > 0) append(',')
        append(quota.toStableJson())
      }
      append(']')
      append('}')
    }
  }

  private fun TaskQuota.toStableJson(): String {
    return "{\"taskId\":\"$taskId\",\"blockId\":\"$blockId\",\"required\":$required}"
  }

  private fun AssembledQuestion.toStableJson(): String {
    return buildString {
      append('{')
      append("\"question\":${question.toStableJson()},")
      append("\"choices\":[")
      choices.forEachIndexed { index, choice ->
        if (index > 0) append(',')
        append(choice.toStableJson())
      }
      append(']')
      append(",\"correctIndex\":$correctIndex")
      append('}')
    }
  }

  private fun Question.toStableJson(): String {
    return buildString {
      append('{')
      append("\"id\":${id.toJsonString()},")
      append("\"taskId\":${taskId.toJsonString()},")
      append("\"blockId\":${blockId.toJsonString()},")
      append("\"locale\":${locale.toJsonString()},")
      append("\"familyId\":${familyId.toJsonValue()},")
      append("\"stem\":${stem.toStableJson()},")
      append("\"correctChoiceId\":${correctChoiceId.toJsonString()}")
      append('}')
    }
  }

  private fun Choice.toStableJson(): String {
    return buildString {
      append('{')
      append("\"id\":${id.toJsonString()},")
      append("\"text\":${text.toStableJson()}")
      append('}')
    }
  }

  private fun Map<String, String>.toStableJson(): String {
    if (isEmpty()) return "{}"
    return entries
      .sortedBy { it.key }
      .joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "\"$key\":${value.toJsonString()}"
      }
  }

  private fun String?.toJsonValue(): String = this?.toJsonString() ?: "null"

  private fun String.toJsonString(): String {
    val escaped =
      buildString {
        for (char in this@toJsonString) {
          when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
          }
        }
      }
    return "\"$escaped\""
  }

  private data class Exposure(
    val questionId: String,
    val wasNew: Boolean,
    val weightBefore: Double,
    val correctBefore: Int,
    val attemptsBefore: Int,
  )

  private class MutableClock(initial: Instant) : Clock() {
    private var current: Instant = initial

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current

    fun advanceHours(hours: Long) {
      current = current.plusSeconds(hours * 3600)
    }
  }

  private class MutableStatsRepository(
    private val stats: MutableMap<String, ItemStats>,
  ) : UserStatsRepository {
    override suspend fun getUserItemStats(
      userId: String,
      ids: List<String>,
    ): Outcome<Map<String, ItemStats>> {
      return Outcome.Ok(ids.mapNotNull { id -> stats[id]?.let { id to it } }.toMap())
    }
  }
}
