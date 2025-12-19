package com.qweld.app.feature.exam.di

import android.content.Context
import com.qweld.app.common.di.IoDispatcher
import com.qweld.app.common.di.PrewarmDisabled
import com.qweld.app.common.di.TimberLogger
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.blueprint.AssetBlueprintProvider
import com.qweld.app.feature.exam.data.blueprint.BlueprintProvider
import com.qweld.app.feature.exam.vm.BlueprintResolver
import com.qweld.app.feature.exam.vm.PrewarmConfig
import com.qweld.app.feature.exam.vm.PrewarmController
import com.qweld.app.feature.exam.vm.PrewarmUseCase
import com.qweld.app.feature.exam.vm.ResumeUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object ExamModule {
  @Provides
  @Singleton
  fun provideBlueprintProvider(@ApplicationContext context: Context): BlueprintProvider =
    AssetBlueprintProvider(context)

  @Provides
  @Singleton
  fun provideBlueprintResolver(provider: BlueprintProvider): BlueprintResolver = BlueprintResolver(provider)

  @Provides
  @Singleton
  fun provideTimerController(): TimerController = TimerController { message -> Timber.i(message) }

  @Provides
  @Singleton
  fun providePrewarmUseCase(
    repository: AssetQuestionRepository,
    @PrewarmDisabled prewarmDisabled: Flow<Boolean>,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    prewarmConfig: PrewarmConfig,
  ): PrewarmUseCase =
    PrewarmUseCase(
      repository = repository,
      prewarmDisabled = prewarmDisabled,
      ioDispatcher = ioDispatcher,
      config = prewarmConfig,
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

  @Provides
  @TimberLogger
  fun provideTimberLogger(): @JvmSuppressWildcards (String) -> Unit = { message -> Timber.i(message) }
}
