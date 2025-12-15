package com.qweld.app.domain.adaptive

import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.AssembledQuestion
import com.qweld.app.domain.exam.AttemptSeed
import com.qweld.app.domain.exam.ExamAssemblyConfig
import com.qweld.app.domain.exam.ExamAttempt
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.Question
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.domain.exam.util.ChoiceBalanceResult
import com.qweld.app.domain.exam.util.DefaultRandomProvider
import com.qweld.app.domain.exam.util.Hash64
import com.qweld.app.domain.exam.util.MutableQuestionState
import com.qweld.app.domain.exam.util.RandomProvider
import com.qweld.app.domain.exam.util.WeightedEntry
import com.qweld.app.domain.exam.util.WeightedSampler
import com.qweld.app.domain.exam.util.balanceCorrectPositions
import com.qweld.app.domain.exam.util.computeWeight
import com.qweld.app.domain.exam.util.enforceAntiCluster
import com.qweld.app.domain.exam.util.fisherYatesShuffle
import java.time.Clock
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adaptive sampler that walks through blueprint quotas while adjusting difficulty bands using
 * [AdaptiveExamPolicy].
 *
 * This class intentionally mirrors the structure of [com.qweld.app.domain.exam.ExamAssembler] but
 * keeps adaptive behavior isolated. UI wiring remains out of scope for EXAM-3; call sites can pick
 * this assembler explicitly without changing the non-adaptive pipeline.
 */
class AdaptiveExamAssembler(
  private val questionRepository: QuestionRepository,
  private val statsRepository: UserStatsRepository,
  private val policy: AdaptiveExamPolicy,
  private val assemblyConfig: ExamAssemblyConfig = ExamAssemblyConfig(),
  private val clock: Clock = Clock.systemUTC(),
  private val randomProvider: RandomProvider = DefaultRandomProvider,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val logger: (String) -> Unit = {},
) {

  data class Assembly(val exam: ExamAttempt, val seed: Long)

  suspend fun assemble(
    userId: String,
    locale: String,
    seed: AttemptSeed,
    blueprint: ExamBlueprint,
  ): Outcome<Assembly> =
    withContext(dispatcher) {
      val timer = TimerController(clock, logger)
      timer.start()

      val contextsOutcome = loadTaskContexts(userId, locale, blueprint, seed)
      val contexts =
        when (contextsOutcome) {
          is Outcome.Err -> return@withContext contextsOutcome
          is Outcome.Ok -> contextsOutcome.value
        }

      val selectionOutcome = selectQuestions(contexts, blueprint, seed)
      val selected =
        when (selectionOutcome) {
          is Outcome.Err -> return@withContext selectionOutcome
          is Outcome.Ok -> selectionOutcome.value
        }

      val assembledQuestions = finalizeSelection(selected, seed)
      val timeLeftDuration = timer.remaining()
      val formattedTimeLeft = TimerController.formatDuration(timeLeftDuration)
      logger(
        "[exam_finish_adaptive] correct=0/${blueprint.totalQuestions} " +
          "score=0 timeLeft=$formattedTimeLeft",
      )

      Outcome.Ok(
        Assembly(
          exam =
            ExamAttempt(
              mode = ExamMode.ADAPTIVE,
              locale = locale,
              seed = seed,
              questions = assembledQuestions,
              blueprint = blueprint,
            ),
          seed = seed.value,
        )
      )
    }

  private suspend fun loadTaskContexts(
    userId: String,
    locale: String,
    blueprint: ExamBlueprint,
    seed: AttemptSeed,
  ): Outcome<List<TaskContext>> {
    val contexts = mutableListOf<TaskContext>()
    for (quota in blueprint.taskQuotas) {
      val poolOutcome = questionRepository.listByTaskAndLocale(quota.taskId, locale, assemblyConfig.allowFallbackToEN)
      val pool =
        when (poolOutcome) {
          is Outcome.Err -> return poolOutcome
          is Outcome.Ok -> poolOutcome.value
        }
      val statsOutcome = statsRepository.getUserItemStats(userId, pool.map { it.id })
      val stats =
        when (statsOutcome) {
          is Outcome.Err -> return statsOutcome
          is Outcome.Ok -> statsOutcome.value
        }
      contexts += TaskContext(quota, pool, stats)
    }
    return Outcome.Ok(contexts)
  }

  private fun selectQuestions(
    contexts: List<TaskContext>,
    blueprint: ExamBlueprint,
    seed: AttemptSeed,
  ): Outcome<List<Question>> {
    val usedIds = mutableSetOf<String>()
    val usedFamilies = mutableMapOf<String, MutableSet<String>>()
    val taskSchedule = buildTaskSchedule(blueprint, seed)
    val adaptiveState = policy.initialState(blueprint.totalQuestions)
    var state = adaptiveState
    val selected = mutableListOf<Question>()
    var pickIndex = 0

    for (taskId in taskSchedule) {
      val context = contexts.first { it.quota.taskId == taskId }
      val desired = policy.pickNextDifficulty(state)
      val orderedBands = fallbackBands(desired)
      val pickOutcome =
        pickQuestion(
          context = context,
          usedIds = usedIds,
          usedFamilies = usedFamilies,
          desiredBandOrder = orderedBands,
          pickIndex = pickIndex,
          seed = seed,
        )
      val (question, band) = pickOutcome ?: return Outcome.Err.QuotaExceeded(taskId, context.quota.required, context.pool.size)

      selected += question
      usedIds += question.id

      // Update adaptive state exactly once using the band actually served.
      val wasCorrect = simulateAnswer(question.id, context.stats, seed, pickIndex)
      state = policy.nextState(state, band, wasCorrect)
      pickIndex += 1
    }

    return Outcome.Ok(selected)
  }

  private fun pickQuestion(
    context: TaskContext,
    usedIds: Set<String>,
    usedFamilies: MutableMap<String, MutableSet<String>>,
    desiredBandOrder: List<DifficultyBand>,
    pickIndex: Int,
    seed: AttemptSeed,
  ): Pair<Question, DifficultyBand>? {
    val remaining = context.pool.filterNot { usedIds.contains(it.id) }
    val bucketed = remaining.groupBy { it.difficulty ?: DifficultyBand.MEDIUM }
    val rngSeed = Hash64.hash(seed.value, "${context.quota.taskId}:ADAPTIVE_PICK:$pickIndex")
    val sampler = WeightedSampler(randomProvider.pcg32(rngSeed))
    for (band in desiredBandOrder) {
      val candidates = bucketed[band].orEmpty()
      if (candidates.isEmpty()) continue
      val ordered = orderCandidates(candidates, context.stats, sampler)
      val familyBucket = usedFamilies.getOrPut(context.quota.taskId) { mutableSetOf() }
      val chosen = ordered.firstOrNull { question ->
        val family = question.familyId
        family == null || familyBucket.add(family)
      }
      if (chosen != null) {
        return chosen to band
      }
    }
    return null
  }

  private fun orderCandidates(
    candidates: List<Question>,
    stats: Map<String, com.qweld.app.domain.exam.ItemStats>,
    sampler: WeightedSampler,
  ): List<Question> {
    val now = clock.instant()
    val entries: List<WeightedEntry<Question>> =
      sampler.order(candidates) { question ->
        val statsForQuestion = stats[question.id]
        val adaptiveBias =
          statsForQuestion?.takeIf { it.attempts > 0 }?.let { 1.0 + (1.0 - (it.correct.toDouble() / it.attempts.toDouble())) }
            ?: 1.0
        computeWeight(statsForQuestion, assemblyConfig, now, adaptiveBias)
      }
    val freshLookup =
      candidates.associate { question ->
        val fresh = stats[question.id]?.isFresh(now, assemblyConfig.freshDays) ?: true
        question.id to fresh
      }
    return entries
      .sortedWith(
        compareByDescending<WeightedEntry<Question>> { if (freshLookup[it.item.id] == true) 1 else 0 }
          .thenBy { it.key }
          .thenBy { it.item.id }
      )
      .map { it.item }
  }

  private fun simulateAnswer(
    questionId: String,
    stats: Map<String, com.qweld.app.domain.exam.ItemStats>,
    seed: AttemptSeed,
    pickIndex: Int,
  ): Boolean {
    val probability =
      stats[questionId]?.takeIf { it.attempts > 0 }?.let { it.correct.toDouble() / it.attempts.toDouble() }
        ?: 0.5
    val rng = randomProvider.pcg32(Hash64.hash(seed.value, "ANSWER_SIM:$pickIndex"))
    return rng.nextDouble() < probability
  }

  private fun buildTaskSchedule(blueprint: ExamBlueprint, seed: AttemptSeed): List<String> {
    val schedule = blueprint.taskQuotas.flatMap { quota -> List(quota.required) { quota.taskId } }.toMutableList()
    val rng = randomProvider.pcg32(Hash64.hash(seed.value, "ADAPTIVE_TASK_ORDER"))
    fisherYatesShuffle(schedule, rng)
    return schedule
  }

  private fun finalizeSelection(
    selected: List<Question>,
    seed: AttemptSeed,
  ): List<AssembledQuestion> {
    val orderSeed = Hash64.hash(seed.value, "ORDER")
    val orderRng = randomProvider.pcg32(orderSeed)
    val ordered = selected.toMutableList()
    fisherYatesShuffle(ordered, orderRng)
    val antiClusterRng = randomProvider.pcg32(Hash64.hash(seed.value, "ANTI_CLUSTER"))
    enforceAntiCluster(
      questions = ordered,
      maxTaskRun = 3,
      maxBlockRun = 5,
      rng = antiClusterRng,
      maxSwaps = assemblyConfig.antiClusterSwaps,
    )

    val mutableStates =
      ordered
        .map { question ->
          val choiceSeed = Hash64.hash(seed.value, question.id)
          val choiceRng = randomProvider.pcg32(choiceSeed)
          val choices = question.choices.toMutableList()
          fisherYatesShuffle(choices, choiceRng)
          val correctIndex = choices.indexOfFirst { it.id == question.correctChoiceId }
          require(correctIndex >= 0) { "Correct choice not found for question ${question.id}" }
          MutableQuestionState(question, choices, correctIndex)
        }
        .toMutableList()

    val balanceResult: ChoiceBalanceResult =
      if (mutableStates.isNotEmpty()) {
        val balanceRng = randomProvider.pcg32(Hash64.hash(seed.value, "CHOICE_BALANCE"))
        balanceCorrectPositions(mutableStates, balanceRng)
      } else {
        ChoiceBalanceResult(adjusted = false, histogram = emptyMap())
      }
    if (balanceResult.histogram.isNotEmpty()) {
      val histogramJson =
        balanceResult.histogram.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
          "\"$k\":$v"
        }
      logger("[choice_shuffle_adaptive] posDist=$histogramJson adjusted=${balanceResult.adjusted}")
    }

    return mutableStates.map { state -> AssembledQuestion(state.question, state.choices.toList(), state.correctIndex) }
  }

  private fun fallbackBands(desired: DifficultyBand): List<DifficultyBand> =
    DifficultyBand.values().sortedBy { (it.ordinal - desired.ordinal).absoluteValue }

  private data class TaskContext(
    val quota: TaskQuota,
    val pool: List<Question>,
    val stats: Map<String, com.qweld.app.domain.exam.ItemStats>,
  )
}
