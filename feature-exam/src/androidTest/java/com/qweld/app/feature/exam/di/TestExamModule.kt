package com.qweld.app.feature.exam.di

import com.qweld.app.common.di.IoDispatcher
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.vm.BlueprintResolver
import com.qweld.app.feature.exam.vm.PrewarmConfig
import com.qweld.app.feature.exam.vm.PrewarmController
import com.qweld.app.feature.exam.vm.PrewarmUseCase
import com.qweld.app.feature.exam.vm.ResumeUseCase
import com.qweld.app.feature.exam.vm.StaticBlueprintProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ExamModule::class])
object TestExamModule {
  @Provides
  @Singleton
  fun provideBlueprintResolver(): BlueprintResolver = BlueprintResolver(StaticBlueprintProvider())

  @Provides
  @Singleton
  fun provideTimerController(): TimerController = TimerController { }

  @Provides
  @Singleton
  fun providePrewarmUseCase(
    repository: AssetQuestionRepository,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
  ): PrewarmUseCase =
    PrewarmUseCase(
      repository = repository,
      prewarmDisabled = flowOf(true),
      ioDispatcher = ioDispatcher,
      config = PrewarmConfig(enabled = false),
    )

  @Provides
  @Singleton
  fun providePrewarmController(
    repository: AssetQuestionRepository,
    prewarmUseCase: PrewarmUseCase,
  ): PrewarmController = com.qweld.app.feature.exam.vm.DefaultPrewarmController(repository, prewarmUseCase)

  @Provides
  @Singleton
  fun provideResumeUseCase(
    repository: AssetQuestionRepository,
    statsRepository: UserStatsRepository,
    blueprintResolver: BlueprintResolver,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
  ): ResumeUseCase =
    ResumeUseCase(
      repository = repository,
      statsRepository = statsRepository,
      blueprintProvider = blueprintResolver::forMode,
      ioDispatcher = ioDispatcher,
    )
}
