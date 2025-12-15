package com.qweld.app.domain.exam

import com.qweld.app.domain.adaptive.AdaptiveExamAssembler
import com.qweld.app.domain.adaptive.AdaptiveExamPolicy
import com.qweld.app.domain.adaptive.DefaultAdaptiveExamPolicy
import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.domain.exam.util.RandomProvider
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Entry points for opt-in exam assembly strategies. Existing call sites can continue to construct
 * [ExamAssembler] directly; adaptive mode callers should fetch an [AdaptiveExamAssembler] here to
 * keep wiring explicit and avoid changing signatures in feature layers until EXAM-3 UI work lands.
 */
object ExamAssemblerFactory {
  fun standard(
    questionRepository: QuestionRepository,
    statsRepository: UserStatsRepository,
    clock: Clock = Clock.systemUTC(),
    config: ExamAssemblyConfig = ExamAssemblyConfig(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    randomProvider: RandomProvider = com.qweld.app.domain.exam.util.DefaultRandomProvider,
    logger: (String) -> Unit = {},
  ): ExamAssembler =
    ExamAssembler(
      questionRepository = questionRepository,
      statsRepository = statsRepository,
      clock = clock,
      config = config,
      logger = logger,
      dispatcher = dispatcher,
      randomProvider = randomProvider,
    )

  fun adaptive(
    questionRepository: QuestionRepository,
    statsRepository: UserStatsRepository,
    policy: AdaptiveExamPolicy = DefaultAdaptiveExamPolicy(),
    clock: Clock = Clock.systemUTC(),
    config: ExamAssemblyConfig = ExamAssemblyConfig(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    randomProvider: RandomProvider = com.qweld.app.domain.exam.util.DefaultRandomProvider,
    logger: (String) -> Unit = {},
  ): AdaptiveExamAssembler =
    AdaptiveExamAssembler(
      questionRepository = questionRepository,
      statsRepository = statsRepository,
      policy = policy,
      assemblyConfig = config,
      clock = clock,
      randomProvider = randomProvider,
      dispatcher = dispatcher,
      logger = logger,
    )
}
