package com.qweld.app.domain.exam

import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.domain.exam.util.ChoiceBalanceResult
import com.qweld.app.domain.exam.util.Hash64
import com.qweld.app.domain.exam.util.DefaultRandomProvider
import com.qweld.app.domain.exam.util.MutableQuestionState
import com.qweld.app.domain.exam.util.RandomProvider
import com.qweld.app.domain.exam.util.WeightedEntry
import com.qweld.app.domain.exam.util.WeightedSampler
import com.qweld.app.domain.exam.util.balanceCorrectPositions
import com.qweld.app.domain.exam.util.computeWeight
import com.qweld.app.domain.exam.util.enforceAntiCluster
import com.qweld.app.domain.exam.util.fisherYatesShuffle
import com.qweld.app.domain.exam.util.formatWeightsStats
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExamAssembler(
  private val questionRepository: QuestionRepository,
  private val statsRepository: UserStatsRepository,
  private val clock: Clock = Clock.systemUTC(),
  private val config: ExamAssemblyConfig = ExamAssemblyConfig(),
  private val logger: (String) -> Unit = ::println,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val randomProvider: RandomProvider = DefaultRandomProvider,
) {
  data class Assembly(
    val exam: ExamAttempt,
    val seed: Long,
  )

  suspend fun assemble(
    userId: String,
    mode: ExamMode,
    locale: String,
    seed: AttemptSeed,
    blueprint: ExamBlueprint = ExamBlueprint.default(),
  ): Outcome<Assembly> =
    withContext(dispatcher) {
      logger(
        "[blueprint_load] source type=${if (blueprint == ExamBlueprint.default()) "default" else "external"} " +
          "tasks=${blueprint.taskCount}"
      )
      val allowFallback =
        when (mode) {
          ExamMode.IP_MOCK -> false
          ExamMode.PRACTICE,
          ExamMode.ADAPTIVE -> config.allowFallbackToEN
        }

      val questionSource = if (allowFallback) "fallback" else "asset"
      logger(
        "[exam_start] mode=${mode.name} seed=${seed.value} locale=$locale " +
          "blueprintTasks=${blueprint.taskCount} source=$questionSource",
      )
      val timer = TimerController(clock, logger)
      timer.start()

      val now = clock.instant()
      val selectionOutcome =
        when (mode) {
          ExamMode.ADAPTIVE -> selectAdaptive(blueprint, userId, locale, allowFallback, seed, now)
          else -> selectNonAdaptive(blueprint, userId, locale, allowFallback, seed, now, mode)
        }
      val selection =
        when (selectionOutcome) {
          is Outcome.Err -> return@withContext selectionOutcome
          is Outcome.Ok -> selectionOutcome.value
        }
      val allSelected = selection.questions
      val effectiveBlueprint = selection.blueprint

      val orderSeed = Hash64.hash(seed.value, "ORDER")
      val orderRng = randomProvider.pcg32(orderSeed)
      val ordered = allSelected.toMutableList()
      fisherYatesShuffle(ordered, orderRng)
      val antiClusterRng = randomProvider.pcg32(Hash64.hash(seed.value, "ANTI_CLUSTER"))
      val swaps =
        enforceAntiCluster(
          questions = ordered,
          maxTaskRun = 3,
          maxBlockRun = 5,
          rng = antiClusterRng,
          maxSwaps = config.antiClusterSwaps,
        )
      logger("[order_shuffle] antiCluster_swaps=$swaps")

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
      val histogramJson =
        if (balanceResult.histogram.isEmpty()) {
          "{}"
        } else {
          balanceResult.histogram.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "\"$k\":$v"
          }
        }
      logger("[choice_shuffle] posDist=$histogramJson adjusted=${balanceResult.adjusted}")

      val assembledQuestions =
        mutableStates.map { state ->
          AssembledQuestion(state.question, state.choices.toList(), state.correctIndex)
        }

      val timeLeftDuration = timer.remaining()
      val formattedTimeLeft = TimerController.formatDuration(timeLeftDuration)
      logger("[exam_finish] correct=0/${effectiveBlueprint.totalQuestions} score=0 timeLeft=$formattedTimeLeft")

      Outcome.Ok(
        Assembly(
          exam =
            ExamAttempt(
              mode = mode,
              locale = locale,
              seed = seed,
              questions = assembledQuestions,
              blueprint = effectiveBlueprint,
            ),
          seed = seed.value,
        )
      )
    }

  private suspend fun selectNonAdaptive(
    blueprint: ExamBlueprint,
    userId: String,
    locale: String,
    allowFallback: Boolean,
    seed: AttemptSeed,
    now: Instant,
    mode: ExamMode,
  ): Outcome<SelectionBundle> {
    val allSelected = mutableListOf<Question>()
    for (quota in blueprint.taskQuotas) {
      val pool =
        when (val questions = questionRepository.listByTaskAndLocale(quota.taskId, locale, allowFallback)) {
          is Outcome.Ok -> questions.value
          is Outcome.Err -> return questions
        }
      val stats =
        when (val statsOutcome = statsRepository.getUserItemStats(userId, pool.map { it.id })) {
          is Outcome.Ok -> statsOutcome.value
          is Outcome.Err -> return statsOutcome
        }
      val selectionResult =
        when (mode) {
          ExamMode.IP_MOCK -> selectUniform(pool, stats, quota, seed, now, locale)
          ExamMode.PRACTICE,
          ExamMode.ADAPTIVE -> selectWeighted(pool, stats, quota, seed, now, locale, mode)
        }
      val selected = selectionResult.questions
      logger(
        "[exam_pick] taskId=${quota.taskId} need=${quota.required} pool=${pool.size} " +
          "selected=${selected.size} fresh=${selectionResult.fresh} reused=${selectionResult.reused}"
      )
      if (selectionResult.deficitDetail != null) {
        val detail = selectionResult.deficitDetail
        logger("[deficit] taskId=${detail.taskId} required=${detail.required} have=${detail.have} seed=${seed.value}")
        return Outcome.Err.QuotaExceeded(taskId = detail.taskId, required = detail.required, have = detail.have)
      }
      allSelected += selected
    }
    return Outcome.Ok(SelectionBundle(allSelected, blueprint))
  }

  private suspend fun selectAdaptive(
    blueprint: ExamBlueprint,
    userId: String,
    locale: String,
    allowFallback: Boolean,
    seed: AttemptSeed,
    now: Instant,
  ): Outcome<SelectionBundle> {
    val contexts = mutableListOf<TaskContext>()
    for (quota in blueprint.taskQuotas) {
      val pool =
        when (val questions = questionRepository.listByTaskAndLocale(quota.taskId, locale, allowFallback)) {
          is Outcome.Ok -> questions.value
          is Outcome.Err -> return questions
        }
      val stats =
        when (val statsOutcome = statsRepository.getUserItemStats(userId, pool.map { it.id })) {
          is Outcome.Ok -> statsOutcome.value
          is Outcome.Err -> return statsOutcome
        }
      contexts += TaskContext(quota = quota, pool = pool, stats = stats)
    }
    val allocator = AdaptiveQuotaAllocator(randomProvider)
    val snapshots =
      contexts.map { context ->
        AdaptiveQuotaAllocator.TaskSnapshot(
          quota = context.quota,
          available = context.pool.size,
          weakness = allocator.weakness(context.stats),
        )
      }
    val allocatedBlueprint =
      when (val allocation = allocator.allocate(blueprint, snapshots, seed)) {
        is AdaptiveQuotaAllocator.AllocationResult.Ok -> allocation.blueprint
        is AdaptiveQuotaAllocator.AllocationResult.Err ->
          return Outcome.Err.QuotaExceeded(
            taskId = allocation.deficit.taskId,
            required = allocation.deficit.required,
            have = allocation.deficit.available,
          )
      }
    val contextByTask = contexts.associateBy { it.quota.taskId }
    val allSelected = mutableListOf<Question>()
    for (quota in allocatedBlueprint.taskQuotas) {
      val context = contextByTask[quota.taskId] ?: continue
      val selectionResult = selectWeighted(context.pool, context.stats, quota, seed, now, locale, ExamMode.ADAPTIVE)
      logger(
        "[exam_pick] taskId=${quota.taskId} need=${quota.required} pool=${context.pool.size} " +
          "selected=${selectionResult.questions.size} fresh=${selectionResult.fresh} reused=${selectionResult.reused}"
      )
      if (selectionResult.deficitDetail != null) {
        val detail = selectionResult.deficitDetail
        logger("[deficit] taskId=${detail.taskId} required=${detail.required} have=${detail.have} seed=${seed.value}")
        return Outcome.Err.QuotaExceeded(taskId = detail.taskId, required = detail.required, have = detail.have)
      }
      allSelected += selectionResult.questions
    }
    return Outcome.Ok(SelectionBundle(allSelected, allocatedBlueprint))
  }

  private fun selectUniform(
    pool: List<Question>,
    stats: Map<String, ItemStats>,
    quota: TaskQuota,
    seed: AttemptSeed,
    now: Instant,
    locale: String,
  ): TaskSelectionResult {
    if (pool.size < quota.required) {
      return TaskSelectionResult(
        questions = emptyList(),
        fresh = 0,
        reused = 0,
        deficitDetail =
          DeficitDetail(
            taskId = quota.taskId,
            required = quota.required,
            have = pool.size,
            missing = quota.required - pool.size,
            locale = locale,
            familyDuplicates = 0,
          ),
      )
    }
    val sampler = WeightedSampler(randomProvider.pcg32(Hash64.hash(seed.value, "${quota.taskId}:UNIFORM")))
    val ordered = sampler.order(pool) { 1.0 }.map { it.item }
    return selectByFamily(ordered, stats, quota, now, locale)
  }

  private fun selectWeighted(
    pool: List<Question>,
    stats: Map<String, ItemStats>,
    quota: TaskQuota,
    seed: AttemptSeed,
    now: Instant,
    locale: String,
    mode: ExamMode,
  ): TaskSelectionResult {
    if (pool.size < quota.required) {
      return TaskSelectionResult(
        questions = emptyList(),
        fresh = 0,
        reused = 0,
        deficitDetail =
          DeficitDetail(
            taskId = quota.taskId,
            required = quota.required,
            have = pool.size,
            missing = quota.required - pool.size,
            locale = locale,
            familyDuplicates = 0,
          ),
      )
    }
    logger(
      "[weights] taskId=${quota.taskId} H=${config.halfLifeCorrect} w_min=${config.minWeight} w_max=${config.maxWeight}"
    )
    val sampler = WeightedSampler(randomProvider.pcg32(Hash64.hash(seed.value, "${quota.taskId}:WEIGHT")))
    var biasApplied = 0
    val entries: List<WeightedEntry<Question>> =
      sampler.order(pool) { question ->
        val statsForQuestion = stats[question.id]
        val practiceBias =
          if (mode == ExamMode.PRACTICE && config.practiceWrongBiased && statsForQuestion?.lastAnswerCorrect == false) {
            biasApplied += 1
            config.weights.wrongBoost
          } else {
            1.0
          }
        val adaptiveBias =
          if (mode == ExamMode.ADAPTIVE) {
            val accuracy =
              statsForQuestion?.takeIf { it.attempts > 0 }?.let { it.correct.toDouble() / it.attempts.toDouble() }
            1.0 + (1.0 - (accuracy ?: 0.0))
          } else {
            1.0
          }
        computeWeight(statsForQuestion, config, now, practiceBias * adaptiveBias)
      }
    if (mode == ExamMode.PRACTICE && config.practiceWrongBiased) {
      logger("[weights.bias] wrongBoost=${config.weights.wrongBoost} applied=$biasApplied")
    }
    val weights = entries.map { it.weight }
    logger("[weights.stats] ${formatWeightsStats(weights)}")
    val freshLookup =
      pool.associate { question ->
        val fresh = stats[question.id]?.isFresh(now, config.freshDays) ?: true
        question.id to fresh
      }
    val ordered =
      entries
        .sortedWith(
          compareByDescending<WeightedEntry<Question>> { if (freshLookup[it.item.id] == true) 1 else 0 }
            .thenBy { it.key }
            .thenBy { it.item.id }
        )
        .map { it.item }
    return selectByFamily(ordered, stats, quota, now, locale)
  }

  private fun selectByFamily(
    ordered: List<Question>,
    stats: Map<String, ItemStats>,
    quota: TaskQuota,
    now: Instant,
    locale: String,
  ): TaskSelectionResult {
    val selected = mutableListOf<Question>()
    val usedFamilies = mutableSetOf<String>()
    var fresh = 0
    var reused = 0
    var duplicateFamilies = 0
    for (question in ordered) {
      val family = question.familyId
      if (family != null && !usedFamilies.add(family)) {
        duplicateFamilies++
        continue
      }
      selected += question
      val itemStats = stats[question.id]
      if (itemStats == null || itemStats.isFresh(now, config.freshDays)) {
        fresh++
      } else {
        reused++
      }
      if (selected.size == quota.required) {
        break
      }
    }
    return if (selected.size == quota.required) {
      TaskSelectionResult(
        questions = selected.toList(),
        fresh = fresh,
        reused = reused,
        deficitDetail = null,
      )
    } else {
      TaskSelectionResult(
        questions = selected.toList(),
        fresh = fresh,
        reused = reused,
        deficitDetail =
          DeficitDetail(
            taskId = quota.taskId,
            required = quota.required,
            have = selected.size,
            missing = quota.required - selected.size,
            locale = locale,
            familyDuplicates = duplicateFamilies,
          ),
      )
    }
  }

  private data class TaskSelectionResult(
    val questions: List<Question>,
    val fresh: Int,
    val reused: Int,
    val deficitDetail: DeficitDetail?,
  )

  private data class TaskContext(
    val quota: TaskQuota,
    val pool: List<Question>,
    val stats: Map<String, ItemStats>,
  )

  private data class SelectionBundle(
    val questions: List<Question>,
    val blueprint: ExamBlueprint,
  )

  private data class DeficitDetail(
    val taskId: String,
    val required: Int,
    val have: Int,
    val missing: Int,
    val locale: String,
    val familyDuplicates: Int,
  )
}
