package com.qweld.app.feature.exam.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.export.AttemptExporter
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.prefs.UserPrefs
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.blueprint.AssetBlueprintProvider
import com.qweld.app.feature.exam.ui.ModeScreen
import com.qweld.app.feature.exam.ui.ExamScreen
import com.qweld.app.feature.exam.ui.ResultScreen
import com.qweld.app.feature.exam.ui.ReviewScreen
import com.qweld.app.feature.exam.vm.ExamViewModel
import com.qweld.app.feature.exam.vm.ExamViewModelFactory
import com.qweld.app.feature.exam.vm.BlueprintResolver
import com.qweld.app.feature.exam.vm.PracticeConfig
import com.qweld.app.feature.exam.vm.PracticeShortcuts
import com.qweld.app.feature.exam.vm.PracticeShortcutsFactory
import com.qweld.app.feature.exam.vm.PrewarmConfig
import com.qweld.app.feature.exam.vm.ResultViewModel
import com.qweld.app.feature.exam.vm.ResultViewModelFactory
import com.qweld.core.common.AppEnv
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

object ExamDestinations {
  const val MODE = "exam_mode"
  const val EXAM = "exam_take"
  const val RESULT = "exam_result"
  const val REVIEW = "exam_review"
  const val PRACTICE = "exam_practice"
}

@Composable
fun ExamNavGraph(
    modifier: Modifier = Modifier,
  navController: NavHostController,
  repository: AssetQuestionRepository,
  explanationRepository: AssetExplanationRepository,
  attemptsRepository: AttemptsRepository,
  answersRepository: AnswersRepository,
  statsRepository: UserStatsRepository,
  questionReportRepository: com.qweld.app.data.reports.QuestionReportRepository,
  appEnv: AppEnv,
  appVersion: String,
  analytics: Analytics,
  userPrefs: UserPrefs,
  prewarmConfig: PrewarmConfig = PrewarmConfig(),
  appLocaleTag: String,
) {
  val context = LocalContext.current.applicationContext
  val blueprintProvider = remember(context) { AssetBlueprintProvider(context) }
  val blueprintResolver = remember(blueprintProvider) { BlueprintResolver(blueprintProvider) }
  val examViewModelFactory =
    remember(
      repository,
      attemptsRepository,
      answersRepository,
      statsRepository,
      questionReportRepository,
      appEnv,
      userPrefs,
      blueprintProvider,
      blueprintResolver,
      prewarmConfig,
    ) {
      ExamViewModelFactory(
        repository = repository,
        attemptsRepository = attemptsRepository,
        answersRepository = answersRepository,
        statsRepository = statsRepository,
        userPrefs = userPrefs,
        questionReportRepository = questionReportRepository,
        appEnv = appEnv,
        blueprintProvider = blueprintProvider,
        blueprintResolver = blueprintResolver,
        prewarmConfig = prewarmConfig,
      )
    }
  val attemptExporter =
    remember(attemptsRepository, answersRepository, appVersion) {
      AttemptExporter(
        attemptsRepository = attemptsRepository,
        answersRepository = answersRepository,
        versionProvider = { appVersion },
      )
    }
  NavHost(
    navController = navController,
    startDestination = ExamDestinations.MODE,
    modifier = modifier,
  ) {
    composable(route = ExamDestinations.MODE) {
      val examViewModel: ExamViewModel =
        viewModel(
          factory = examViewModelFactory,
        )
      val practiceShortcuts: PracticeShortcuts =
        viewModel(
          factory =
            PracticeShortcutsFactory(
              attemptsRepository = attemptsRepository,
              answersRepository = answersRepository,
              userPrefs = userPrefs,
            ),
        )
      val coroutineScope = rememberCoroutineScope()
      val practiceSize by
        userPrefs.practiceSizeFlow().collectAsState(
          initial = UserPrefsDataStore.DEFAULT_PRACTICE_SIZE,
        )
      val wrongBiased by
        userPrefs.wrongBiased.collectAsState(
          initial = UserPrefsDataStore.DEFAULT_WRONG_BIASED,
        )
      val lastPracticeConfig by examViewModel.lastPracticeConfig.collectAsState(initial = null)
      val practiceConfig =
        remember(practiceSize, wrongBiased, lastPracticeConfig) {
          lastPracticeConfig
            ?: PracticeConfig(
              size = PracticeConfig.sanitizeSize(practiceSize),
              wrongBiased = wrongBiased,
            )
        }
      ModeScreen(
        repository = repository,
        viewModel = examViewModel,
        practiceShortcuts = practiceShortcuts,
        practiceConfig = practiceConfig,
        navController = navController,
        appLocaleTag = appLocaleTag,
        onPracticeSizeCommit = { size ->
          coroutineScope.launch { userPrefs.setPracticeSize(size) }
        },
        onRepeatMistakes = { locale, blueprint, config ->
          val launched = examViewModel.startAttempt(
            mode = ExamMode.PRACTICE,
            locale = locale,
            practiceConfig = config,
            blueprintOverride = blueprint,
          )
          if (launched) {
            navController.navigate(ExamDestinations.EXAM) { launchSingleTop = true }
          }
        },
        onResumeAttempt = {
          navController.navigate(ExamDestinations.EXAM) { launchSingleTop = true }
        },
      )
    }
    composable(route = ExamDestinations.EXAM) { backStackEntry ->
      val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(ExamDestinations.MODE) }
      val examViewModel: ExamViewModel =
        viewModel(
          parentEntry,
          factory = examViewModelFactory,
        )
      LaunchedEffect(examViewModel, navController) {
        examViewModel.effects.collectLatest { effect ->
          when (effect) {
            ExamViewModel.ExamEffect.NavigateToMode -> {
              Timber.i("[ui_nav] screen=Mode")
              val popped = navController.popBackStack(ExamDestinations.MODE, inclusive = false)
              if (!popped) {
                navController.navigate(ExamDestinations.MODE) {
                  popUpTo(navController.graph.startDestinationId) { inclusive = false }
                  launchSingleTop = true
                }
              }
            }
            ExamViewModel.ExamEffect.RestartWithSameConfig -> {
              val config = examViewModel.uiState.value.lastPracticeConfig
              val locale = examViewModel.uiState.value.lastLocale
                ?: Locale.getDefault().language.lowercase(Locale.US)
              if (config == null) {
                examViewModel.notifyRestartFailure("Missing practice configuration.")
                return@collectLatest
              }
              val launched = examViewModel.startPractice(locale, config)
              if (!launched) {
                examViewModel.notifyRestartFailure("Unable to restart practice.")
              }
            }
            else -> Unit
          }
        }
      }
      ExamScreen(
        viewModel = examViewModel,
        onNavigateToResult = {
          Timber.i("[ui_nav] screen=Result")
          navController.navigate(ExamDestinations.RESULT) { launchSingleTop = true }
        },
        analytics = analytics,
        userPrefs = userPrefs,
      )
    }
    composable(route = ExamDestinations.RESULT) { backStackEntry ->
      val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(ExamDestinations.MODE) }
      val examViewModel: ExamViewModel =
        viewModel(
          parentEntry,
          factory = examViewModelFactory,
        )
      val resultViewModel: ResultViewModel =
        viewModel(
          backStackEntry,
          factory =
            ResultViewModelFactory(
              resultDataProvider = { examViewModel.requireLatestResult() },
              attemptExporter = attemptExporter,
            ),
        )
      ResultScreen(
        viewModel = resultViewModel,
        onReview = {
          Timber.i("[ui_nav] screen=Review")
          navController.navigate(ExamDestinations.REVIEW) { launchSingleTop = true }
        },
        onExit = {
          Timber.i("[ui_nav] screen=Mode (from Result)")
          navController.navigate(ExamDestinations.MODE) {
            popUpTo(ExamDestinations.MODE) { inclusive = false }
            launchSingleTop = true
          }
        },
      )
    }
    composable(route = ExamDestinations.REVIEW) { backStackEntry ->
      val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(ExamDestinations.MODE) }
      val examViewModel: ExamViewModel =
        viewModel(
          parentEntry,
          factory = examViewModelFactory,
        )
      val resultViewModel: ResultViewModel =
        viewModel(
          backStackEntry,
          factory =
            ResultViewModelFactory(
              resultDataProvider = { examViewModel.requireLatestResult() },
              attemptExporter = attemptExporter,
            ),
        )
      ReviewScreen(
        resultData = examViewModel.requireLatestResult(),
        explanationRepository = explanationRepository,
        onBack = { navController.popBackStack() },
        resultViewModel = resultViewModel,
        analytics = analytics,
      )
    }
  }
}
