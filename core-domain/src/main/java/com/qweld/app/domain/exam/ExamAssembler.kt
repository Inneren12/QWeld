package com.qweld.app.domain.exam

import com.qweld.app.domain.exam.ItemStats
import com.qweld.app.domain.exam.errors.ExamAssemblyException
import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.domain.exam.util.ChoiceBalanceResult
import com.qweld.app.domain.exam.util.Hash64
import com.qweld.app.domain.exam.util.MutableQuestionState
import com.qweld.app.domain.exam.util.Pcg32
import com.qweld.app.domain.exam.util.WeightedEntry
import com.qweld.app.domain.exam.util.WeightedSampler
import com.qweld.app.domain.exam.util.balanceCorrectPositions
import com.qweld.app.domain.exam.util.computeWeight
import com.qweld.app.domain.exam.util.enforceAntiCluster
import com.qweld.app.domain.exam.util.fisherYatesShuffle
import com.qweld.app.domain.exam.util.formatWeightsStats
import java.time.Clock
import java.time.Instant

class ExamAssembler(
  private val questionRepository: QuestionRepository,
  private val statsRepository: UserStatsRepository,
  private val clock: Clock = Clock.systemUTC(),
  private val config: ExamAssemblyConfig = ExamAssemblyConfig(),
  private val logger: (String) -> Unit = ::println,
) {
  suspend fun assemble(
    userId: String,
    mode: ExamMode,
    locale: String,
    seed: AttemptSeed,
    blueprint: ExamBlueprint = ExamBlueprint.default(),
  ): ExamAttempt {
    logger("[exam_start] mode=${mode.name} seed=${seed.value} locale=$locale tasks=${blueprint.taskCount}")
    val timer = TimerController(clock, logger)
    timer.start()

    val allowFallback =
      when (mode) {
        ExamMode.IP_MOCK -> false
        ExamMode.PRACTICE,
        ExamMode.ADAPTIVE -> config.allowFallbackToEN
      }

    val now = clock.instant()
    val allSelected = mutableListOf<Question>()
    val deficits = mutableListOf<ExamAssemblyException.DeficitDetail>()
    for (quota in blueprint.taskQuotas) {
      val pool = questionRepository.listByTaskAndLocale(quota.taskId, locale, allowFallback)
      val stats = statsRepository.getUserItemStats(userId, pool.map { it.id })
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
        logger(
          "[deficit] taskId=${detail.taskId} need=${detail.need} have=${detail.have} " +
            "locale=${detail.locale} famDup=${detail.familyDuplicates} action=block"
        )
        deficits += detail
      } else {
        allSelected += selected
      }
    }

    if (deficits.isNotEmpty()) {
      throw ExamAssemblyException.Deficit(deficits)
    }

    val orderSeed = Hash64.hash(seed.value, "ORDER")
    val orderRng = Pcg32(orderSeed)
    val ordered = allSelected.toMutableList()
    fisherYatesShuffle(ordered, orderRng)
    val antiClusterRng = Pcg32(Hash64.hash(seed.value, "ANTI_CLUSTER"))
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
          val choiceRng = Pcg32(choiceSeed)
          val choices = question.choices.toMutableList()
          fisherYatesShuffle(choices, choiceRng)
          val correctIndex = choices.indexOfFirst { it.id == question.correctChoiceId }
          require(correctIndex >= 0) { "Correct choice not found for question ${question.id}" }
          MutableQuestionState(question, choices, correctIndex)
        }
        .toMutableList()

    val balanceResult: ChoiceBalanceResult =
      if (mutableStates.isNotEmpty()) {
        val balanceRng = Pcg32(Hash64.hash(seed.value, "CHOICE_BALANCE"))
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
    logger("[exam_finish] correct=0/${blueprint.totalQuestions} score=0 timeLeft=$formattedTimeLeft")

    return ExamAttempt(
      mode = mode,
      locale = locale,
      seed = seed,
      questions = assembledQuestions,
      blueprint = blueprint,
    )
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
          ExamAssemblyException.DeficitDetail(
            taskId = quota.taskId,
            need = quota.required,
            have = pool.size,
            missing = quota.required - pool.size,
            locale = locale,
            familyDuplicates = 0,
          ),
      )
    }
    val sampler = WeightedSampler(Pcg32(Hash64.hash(seed.value, "${quota.taskId}:UNIFORM")))
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
          ExamAssemblyException.DeficitDetail(
            taskId = quota.taskId,
            need = quota.required,
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
    val sampler = WeightedSampler(Pcg32(Hash64.hash(seed.value, "${quota.taskId}:WEIGHT")))
    var biasApplied = 0
    val entries: List<WeightedEntry<Question>> =
      sampler.order(pool) { question ->
        val statsForQuestion = stats[question.id]
        val bias =
          if (mode == ExamMode.PRACTICE && config.practiceWrongBiased && statsForQuestion?.lastAnswerCorrect == false) {
            biasApplied += 1
            config.weights.wrongBoost
          } else {
            1.0
          }
        computeWeight(statsForQuestion, config, now, bias)
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
          ExamAssemblyException.DeficitDetail(
            taskId = quota.taskId,
            need = quota.required,
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
    val deficitDetail: ExamAssemblyException.DeficitDetail?,
  )
}
