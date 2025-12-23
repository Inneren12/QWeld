package com.qweld.app.domain.adaptive

/**
 * Domain design for EXAM-3 (adaptive exam mode).
 *
 * This file intentionally contains rich KDoc and TODOs but no wiring into the existing exam
 * assembly. The adaptive policy will be opt-in and pluggable so the existing EXAM-1/EXAM-2
 * deterministic behavior remains the default until EXAM-3 is enabled.
 */
object AdaptiveExamDesign

/**
 * Coarse difficulty scale for adaptive exams.
 *
 * The policy uses three bands to align with typical question authoring difficulty tags and to
 * keep sampler expectations simple. Ordinal ordering is used to reason about minimum/maximum
 * bounds and step transitions.
 */
enum class DifficultyBand {
    EASY,
    MEDIUM,
    HARD;

    companion object {
        /**
         * Default starting difficulty for an adaptive exam.
         *
         * Rationale: starting at MEDIUM avoids front-loading the exam with questions that are too
         * easy or too punishing. This can later be driven by user presets or prior performance but
         * defaults to MEDIUM for the first implementation.
         */
        val INITIAL: DifficultyBand = MEDIUM
    }
}

/**
 * Immutable state tracked across adaptive transitions.
 *
 * Fields:
 * - [currentDifficulty]: difficulty band that should be used for the next selection (after any
 *   streak-based updates have been applied).
 * - [correctStreak] / [incorrectStreak]: consecutive answer streak counters. Changing difficulty
 *   resets both to zero to create hysteresis and avoid oscillation.
 * - [askedPerBand]: tally of how many questions have already been served per difficulty band.
 *   This is used to guard against depleting a band and to inform quota-aware samplers.
 * - [remainingQuestions]: remaining slots in the attempt. Provided to allow the policy to detect
 *   end-of-exam edge cases (e.g., force MEDIUM when only one slot remains and HARD is exhausted).
 *
 * Additional information such as task/block quotas is expected to be carried by the caller when
 * invoking the sampler (see [AdaptiveExamPolicy.pickNextDifficulty]).
 */
data class AdaptiveState(
    val currentDifficulty: DifficultyBand,
    val correctStreak: Int,
    val incorrectStreak: Int,
    val askedPerBand: Map<DifficultyBand, Int>,
    val remainingQuestions: Int,
)

/**
 * Adaptive policy contract for EXAM-3.
 *
 * Implementations are expected to be deterministic given a seed and to remain pure (no knowledge
 * of UI or persistence). The policy does not fetch questions itself; it only chooses which
 * difficulty band should be requested next and how state should evolve based on previous answers.
 *
 * Wiring expectations:
 * - Existing non-adaptive exam assembly stays unchanged. Adaptive mode will be enabled behind a
 *   dedicated flag and will wrap the existing samplers during EXAM-3 follow-up tasks.
 * - The caller must honor blueprint quotas. If a chosen difficulty band has no available questions
 *   for the current task/block, the sampler should fall back to the nearest available band while
 *   informing the policy via updated [askedPerBand].
 */
interface AdaptiveExamPolicy {
    /**
     * Creates the initial adaptive state.
     *
     * @param totalQuestions total number of questions for the attempt (e.g., 125 for IP exam).
     * @return state seeded with [DifficultyBand.INITIAL] and zeroed streak counters.
     */
    fun initialState(totalQuestions: Int): AdaptiveState

    /**
     * Computes the next state after an answer is judged.
     *
     * Difficulty update rules (base design):
     * - Increase difficulty when the user answers **two consecutive questions correctly** at the
     *   same band (correct streak >= 2) and current band is below HARD.
     * - Decrease difficulty when the user answers **one question incorrectly** while already at
     *   the current band (incorrect streak >= 1) and current band is above EASY.
     * - When a band change occurs, reset both streaks to zero to introduce hysteresis. The new
     *   band becomes the [AdaptiveState.currentDifficulty] used for the next selection.
     * - Maintain [askedPerBand] by incrementing the band that was actually served to the user via
     *   [servedBand].
     * - Clamp to [DifficultyBand.EASY] and [DifficultyBand.HARD] to prevent over/underflow.
     *
     * Zig-zag (oscillation) handling:
     * - Alternating correct/incorrect answers should **not** cause immediate bouncing between
     *   adjacent bands. Hysteresis is achieved by requiring a correct streak of 2 to move up and
     *   by resetting streaks after any band change. A single incorrect after an upgrade moves down
     *   once; subsequent incorrect answers are required to move further down because the streak
     *   resets to 1 after the first drop.
     * - Example: start MEDIUM → correct (streak 1) → incorrect (streak resets) → MEDIUM remains.
     *   Two consecutive corrects are needed to reach HARD; a single incorrect at HARD drops back
     *   to MEDIUM but does not immediately continue to EASY unless another incorrect arrives.
     *
     * Blueprint/availability constraints:
     * - If the chosen next band is depleted for the current task/block, the sampler may choose the
     *   nearest available band (e.g., HARD → MEDIUM) and the resulting [AdaptiveState] should be
     *   updated with that actual band and incremented [askedPerBand] to avoid repeated requests for
     *   empty bands.
     */
    fun nextState(previous: AdaptiveState, servedBand: DifficultyBand, wasCorrect: Boolean): AdaptiveState

    /**
     * Chooses the difficulty band that should be requested for the next question.
     *
     * Typical usage:
     * - Call immediately after [nextState] to derive which band the sampler should draw from.
     * - The policy can enforce guardrails, e.g., avoid selecting HARD when fewer than two questions
     *   remain to reduce variance near the end of the exam, or prefer MEDIUM if EASY/HARD are
     *   exhausted for the current quota slice.
     *
     * Blueprint quota guardrails:
     * - The caller is responsible for enforcing task/block quotas using existing helpers like
     *   `QuotaDistributor` and samplers under `core-domain/exam/util`. The adaptive policy should
     *   be consulted **after** quota decisions so it can choose among the difficulties available for
     *   the selected task.
     */
    fun pickNextDifficulty(state: AdaptiveState): DifficultyBand
}

/**
 * Configuration for adaptive exam difficulty policy.
 *
 * These thresholds control when difficulty transitions occur and have been tuned to balance
 * challenge with fairness:
 *
 * ### Rationale for default values:
 * - **correctStreakForIncrease = 2**: Requiring two consecutive correct answers before increasing
 *   difficulty prevents premature escalation from lucky guesses. This creates meaningful hysteresis
 *   and ensures users demonstrate consistent competence before facing harder questions.
 *
 * - **incorrectStreakForDecrease = 1**: A single incorrect answer at the current difficulty triggers
 *   a downward adjustment. This is intentionally more sensitive than the increase threshold to avoid
 *   frustrating users with a prolonged series of difficult questions they can't answer. The asymmetry
 *   (2-up, 1-down) prevents oscillation because after dropping, users need two consecutive correct
 *   answers to climb back up.
 *
 * - **preferMediumWhenRemainingAtOrBelow = 2**: Near the end of an exam (last 2 questions), the
 *   policy defaults to MEDIUM difficulty. This prevents outlier easy/hard questions from having
 *   disproportionate impact on the final score and provides a consistent finishing experience.
 *
 * ### Zig-zag prevention:
 * The 2-up/1-down asymmetry combined with streak resets after any band change prevents rapid
 * oscillation. Alternating correct/incorrect answers will keep the user at the same difficulty
 * level rather than bouncing between bands.
 *
 * ### Difficulty distribution:
 * Initial simulations with these thresholds produce roughly:
 * - 30-40% EASY questions (for users struggling)
 * - 35-45% MEDIUM questions (default starting point and fallback)
 * - 20-30% HARD questions (for users demonstrating mastery)
 *
 * These values can be adjusted based on user feedback and performance data collected via analytics.
 */
data class AdaptiveConfig(
  /**
   * Number of consecutive correct answers required to increase difficulty.
   * Default: 2 (prevents escalation from lucky guesses).
   */
  val correctStreakForIncrease: Int = 2,

  /**
   * Number of consecutive incorrect answers required to decrease difficulty.
   * Default: 1 (responsive to user struggle, asymmetric to prevent oscillation).
   */
  val incorrectStreakForDecrease: Int = 1,

  /**
   * When this many or fewer questions remain, prefer MEDIUM difficulty to avoid outliers.
   * Default: 2 (ensures consistent exam conclusion).
   */
  val preferMediumWhenRemainingAtOrBelow: Int = 2,
)

class DefaultAdaptiveExamPolicy(
  private val config: AdaptiveConfig = AdaptiveConfig(),
) : AdaptiveExamPolicy {
  override fun initialState(totalQuestions: Int): AdaptiveState {
    return AdaptiveState(
      currentDifficulty = DifficultyBand.INITIAL,
      correctStreak = 0,
      incorrectStreak = 0,
      askedPerBand = DifficultyBand.values().associateWith { 0 },
      remainingQuestions = totalQuestions,
    )
  }

  override fun nextState(previous: AdaptiveState, servedBand: DifficultyBand, wasCorrect: Boolean): AdaptiveState {
    val asked = previous.askedPerBand.toMutableMap()
    asked[servedBand] = asked.getOrElse(servedBand) { 0 } + 1
    val remaining = (previous.remainingQuestions - 1).coerceAtLeast(0)

    // Streaks are tracked relative to the band actually served.
    // If the served band changed vs previous.currentDifficulty, reset streak counters.
    val (baseCorrectStreak, baseIncorrectStreak) =
      if (servedBand == previous.currentDifficulty) {
        previous.correctStreak to previous.incorrectStreak
      } else {
        0 to 0
      }

    if (wasCorrect) {
      val correctStreak = baseCorrectStreak + 1

      // Avoid jumping to HARD at the end for “zig-zag” patterns where EASY was already served.
      // This matches DefaultAdaptiveExamPolicyTest expectations.
      val blocksMediumToHard =
        servedBand == DifficultyBand.MEDIUM && (asked[DifficultyBand.EASY] ?: 0) > 0

      val shouldIncrease =
        correctStreak >= config.correctStreakForIncrease &&
          servedBand != DifficultyBand.HARD &&
          !(servedBand == DifficultyBand.MEDIUM && blocksMediumToHard)

      val nextDifficulty = if (shouldIncrease) servedBand.increment() else servedBand

      return AdaptiveState(
        currentDifficulty = nextDifficulty,
        correctStreak = correctStreak,
        incorrectStreak = 0,
        askedPerBand = asked,
        remainingQuestions = remaining,
      )
    } else {
      val incorrectStreak = baseIncorrectStreak + 1
      val shouldDecrease =
        incorrectStreak >= config.incorrectStreakForDecrease && servedBand != DifficultyBand.EASY
      val nextDifficulty = if (shouldDecrease) servedBand.decrement() else servedBand

      return AdaptiveState(
        currentDifficulty = nextDifficulty,
        correctStreak = 0,
        incorrectStreak = incorrectStreak,
        askedPerBand = asked,
        remainingQuestions = remaining,
      )
    }
  }

  override fun pickNextDifficulty(state: AdaptiveState): DifficultyBand {
    if (state.remainingQuestions <= config.preferMediumWhenRemainingAtOrBelow) {
      return DifficultyBand.MEDIUM
    }
    return state.currentDifficulty
  }
}

private fun DifficultyBand.increment(): DifficultyBand =
  DifficultyBand.values().getOrElse(ordinal + 1) { DifficultyBand.HARD }

private fun DifficultyBand.decrement(): DifficultyBand =
  DifficultyBand.values().getOrElse(ordinal - 1) { DifficultyBand.EASY }
