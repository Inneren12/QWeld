package com.qweld.app.feature.exam.vm

import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Creates a BlueprintResolver for ExamViewModel tests.
 * Returns a fixed blueprint for all modes.
 */
fun createTestBlueprintResolver(blueprint: ExamBlueprint): BlueprintResolver {
  return BlueprintResolver(
    provider = StaticBlueprintProvider(blueprint)
  )
}

/**
 * Creates a ResumeUseCase for ExamViewModel tests.
 * Uses the given repository and stats repository for reconstruction.
 */
fun createTestResumeUseCase(
  repository: AssetQuestionRepository,
  statsRepository: UserStatsRepository,
  blueprint: ExamBlueprint,
  ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
): ResumeUseCase {
  val blueprintProvider: (com.qweld.app.domain.exam.ExamMode, Int) -> ExamBlueprint = { _, _ -> blueprint }
  return ResumeUseCase(
    repository = repository,
    statsRepository = statsRepository,
    blueprintProvider = blueprintProvider,
    ioDispatcher = ioDispatcher
  )
}
