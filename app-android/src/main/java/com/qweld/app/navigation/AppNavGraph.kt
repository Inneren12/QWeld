package com.qweld.app.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qweld.app.BuildConfig
import com.qweld.app.admin.AdminDashboardRoute
import com.qweld.app.admin.AdminDashboardViewModel
import com.qweld.app.common.error.AppError
import com.qweld.app.common.error.AppErrorEvent
import com.qweld.app.common.error.AppErrorHandler
import com.qweld.app.common.error.AppErrorReportResult
import com.qweld.app.common.error.ErrorContext
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.content.ContentIndexReader
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.logging.LogExportFormat
import com.qweld.app.data.logging.writeTo
import com.qweld.app.data.prefs.UserPrefs
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.auth.AuthService
import com.qweld.app.feature.auth.DefaultGoogleCredentialSignInManager
import com.qweld.app.feature.auth.GoogleCredentialSignInManager
import com.qweld.app.feature.auth.GoogleSignInCancelledException
import com.qweld.app.feature.auth.R
import com.qweld.app.feature.auth.ui.LinkAccountScreen
import com.qweld.app.feature.auth.ui.SignInScreen
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.navigation.ExamNavGraph
import com.qweld.app.i18n.LocaleController
import com.qweld.app.ui.AppErrorReportDialog
import com.qweld.app.ui.AboutScreen
import com.qweld.app.ui.SettingsScreen
import com.qweld.app.ui.TopBarMenus
import com.qweld.core.common.AppEnv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun AppNavGraph(
  authService: AuthService,
  questionRepository: AssetQuestionRepository,
  explanationRepository: AssetExplanationRepository,
  attemptsRepository: AttemptsRepository,
  answersRepository: AnswersRepository,
  statsRepository: UserStatsRepository,
  questionReportRepository: com.qweld.app.data.reports.QuestionReportRepository,
  analytics: Analytics,
  logCollector: LogCollector?,
  userPrefs: UserPrefs,
  contentIndexReader: ContentIndexReader,
  appEnv: AppEnv,
  appErrorHandler: AppErrorHandler,
  errorReportingEnabled: Boolean,
  appLocaleTag: String,
  modifier: Modifier = Modifier,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route
  val user by authService.currentUser.collectAsStateWithLifecycle(initialValue = null)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val genericErrorText = stringResource(id = R.string.auth_error_generic)
  val googleCancelledText = stringResource(id = R.string.auth_error_google_sign_in)
  val missingTokenText = stringResource(id = R.string.auth_error_missing_token)
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }
  var pendingErrorEvent by remember { mutableStateOf<AppErrorEvent?>(null) }
  var isSubmittingError by remember { mutableStateOf(false) }
  val errorReportingDisabledMessage = stringResource(id = com.qweld.app.R.string.app_error_report_disabled)
  val errorReportingSubmittedMessage = stringResource(id = com.qweld.app.R.string.app_error_report_submitted)
  val errorReportingFailedMessage = stringResource(id = com.qweld.app.R.string.app_error_report_failed)

  // Initialize Google Credential Sign-In Manager
  val googleCredentialSignInManager: GoogleCredentialSignInManager = remember {
    DefaultGoogleCredentialSignInManager(
      serverClientId = context.getString(com.qweld.app.R.string.default_web_client_id)
    )
  }

  val appLocale by userPrefs.appLocaleFlow().collectAsState(initial = UserPrefsDataStore.DEFAULT_APP_LOCALE)
  val currentLocale = appLocale
  val handleLocaleSelection =
    remember(userPrefs, scope, currentLocale) {
      { tag: String, source: String ->
        Timber.i("[settings_locale] select tag=%s (source=%s)", tag, source)
        LocaleController.apply(tag)
        if (tag != currentLocale) {
          scope.launch { userPrefs.setAppLocale(tag) }
        }
      }
    }

  LaunchedEffect(appErrorHandler, errorReportingEnabled) {
    appErrorHandler.uiEvents.collect { event ->
      if (errorReportingEnabled) {
        if (pendingErrorEvent == null) {
          pendingErrorEvent = event.event
        } else {
          Timber.w("[app_error_dialog] skipped=true reason=dialog_active")
        }
      } else {
        snackbarHostState.showSnackbar(message = errorReportingDisabledMessage)
      }
    }
  }

  val navigateToAuth: () -> Unit = {
    navController.navigate(Routes.AUTH) {
      popUpTo(navController.graph.startDestinationId) { inclusive = true }
      launchSingleTop = true
    }
  }

  fun startGoogle(action: GoogleAction) {
    errorMessage = null
    isLoading = true
    val errorContext = ErrorContext(screen = "auth", action = "google_${action.name.lowercase()}")
    scope.launch {
      try {
        val result = googleCredentialSignInManager.signInWithGoogleIdToken(context)

        result.fold(
          onSuccess = { idToken ->
            val token = idToken.takeIf { it.isNotBlank() }
            if (token == null) {
              Timber.e("[auth] Missing idToken from Google sign-in result")
              appErrorHandler.handle(
                AppError.Unexpected(
                  cause = IllegalStateException("missing_google_token"),
                  context = errorContext,
                )
              )
              errorMessage = missingTokenText
            } else {
              try {
                when (action) {
                  GoogleAction.SignIn -> authService.signInWithGoogle(token)
                  GoogleAction.Link -> authService.linkAnonymousToGoogle(token)
                }
                errorMessage = null
              } catch (exception: Exception) {
                Timber.e(exception, "Firebase auth failed")
                appErrorHandler.handle(
                  AppError.Unexpected(
                    cause = exception,
                    context = errorContext,
                  )
                )
                errorMessage =
                  exception.localizedMessage?.takeIf { it.isNotBlank() } ?: genericErrorText
              }
            }
          },
          onFailure = { exception ->
            when (exception) {
              is GoogleSignInCancelledException -> {
                Timber.d("User cancelled Google Sign-In")
                errorMessage = googleCancelledText
              }
              else -> {
                Timber.e(exception, "Google Sign-In failed")
                appErrorHandler.handle(
                  AppError.Unexpected(
                    cause = exception,
                    context = errorContext,
                  )
                )
                errorMessage =
                  exception.localizedMessage?.takeIf { it.isNotBlank() } ?: genericErrorText
              }
            }
          },
        )
      } finally {
        isLoading = false
      }
    }
  }

    LaunchedEffect(user) {
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    val u = user
    if (u == null) {
      navigateToAuth()
    } else if (u.isAnonymous) {
      navigateToAuth()
    } else if (currentRoute == Routes.AUTH) {
      navController.navigate(Routes.EXAM) {
        popUpTo(navController.graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
      }
    }
  }

  val logExportActions = rememberLogExportActions(logCollector)
  var showLogDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      val hideTopBarRoutes = setOf(Routes.AUTH)
      if (currentRoute !in hideTopBarRoutes) {
        TopBarMenus(
          user = user,
          onNavigateToSync = {
            Timber.i("[ui_nav] screen=Sync")
            navController.navigate(Routes.SYNC) { launchSingleTop = true }
          },
          onNavigateToSettings = {
            Timber.i("[ui_nav] screen=Settings")
            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
          },
          onNavigateToAbout = {
            Timber.i("[ui_nav] screen=About")
            navController.navigate(Routes.ABOUT) { launchSingleTop = true }
          },
          onSignOut = {
            scope.launch {
              runCatching { authService.signOut() }
                .onSuccess { navigateToAuth() }
                .onFailure { Timber.e(it, "Sign out failed") }
            }
          },
          onExportLogs =
            if (logExportActions != null) {
              {
                showLogDialog = true
              }
            } else {
              null
            },
          onNavigateToAdminReports = if (BuildConfig.DEBUG) {
            {
              Timber.i("[ui_nav] screen=AdminReports")
              navController.navigate(Routes.ADMIN_REPORTS) { launchSingleTop = true }
            }
          } else {
            null
          },
          currentLocaleTag = appLocale,
          onLocaleSelected = { tag -> handleLocaleSelection(tag, "topbar") },
        )
      }
    },
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = Routes.AUTH,
      modifier = Modifier.padding(innerPadding),
    ) {
      composable(Routes.AUTH) {
        when {
          user == null ->
            SignInScreen(
              isLoading = isLoading,
              errorMessage = errorMessage,
              onContinueAsGuest = {
                runAuthAction(
                  scope = scope,
                  setLoading = { isLoading = it },
                  setError = { errorMessage = it },
                  defaultError = genericErrorText,
                  actionLabel = "sign_in_guest",
                  appErrorHandler = appErrorHandler,
                ) {
                  authService.signInAnonymously()
                }
              },
              onSignInWithGoogle = { startGoogle(GoogleAction.SignIn) },
              onSignInWithEmail = { email, password ->
                runAuthAction(
                  scope = scope,
                  setLoading = { isLoading = it },
                  setError = { errorMessage = it },
                  defaultError = genericErrorText,
                  actionLabel = "sign_in_email",
                  appErrorHandler = appErrorHandler,
                ) {
                  authService.signInWithEmail(email, password)
                }
              },
            )
          user?.isAnonymous == true ->
            LinkAccountScreen(
              isLoading = isLoading,
              errorMessage = errorMessage,
              onLinkWithGoogle = { startGoogle(GoogleAction.Link) },
            )
          else -> LoadingScreen()
        }
      }
      composable(Routes.EXAM) {
        val examNavController = rememberNavController()
        ExamNavGraph(
          navController = examNavController,
          repository = questionRepository,
          explanationRepository = explanationRepository,
          analytics = analytics,
          userPrefs = userPrefs,
          appLocaleTag = appLocale,
        )
      }
      composable(Routes.SYNC) {
        RequireNonAnonymous(
          user = user,
          onRequire = {
            val reason = when {
              user == null -> "no_user"
              user?.isAnonymous == true -> "anonymous"
              else -> "unknown"
            }
            Timber.i("[auth_guard] route=/sync allowed=false reason=%s", reason)
            navigateToAuth()
          },
        ) {
          SyncScreen(onBack = { navController.popBackStack() })
        }
      }
      composable(Routes.SETTINGS) {
        SettingsScreen(
          userPrefs = userPrefs as UserPrefsDataStore,
          attemptsRepository = attemptsRepository,
          answersRepository = answersRepository,
          questionRepository = questionRepository,
          contentIndexReader = contentIndexReader,
          appLocaleTag = appLocale,
          onLocaleSelected = { tag -> handleLocaleSelection(tag, "settings") },
          onExportLogs =
            if (logExportActions != null) {
              {
                showLogDialog = true
              }
            } else {
              null
            },
          onOpenAdminDashboard =
            if (BuildConfig.DEBUG) {
              {
                Timber.i("[ui_nav] screen=AdminDashboard")
                navController.navigate(Routes.ADMIN_DASHBOARD) { launchSingleTop = true }
              }
            } else {
              null
            },
          onBack = { navController.popBackStack() },
        )
      }
      composable(Routes.ABOUT) {
        AboutScreen(
          onExportDiagnostics =
            if (logExportActions != null) {
              { showLogDialog = true }
            } else {
              null
            },
        )
      }
      composable(Routes.ADMIN_REPORTS) {
        val viewModel: com.qweld.app.admin.QuestionReportsViewModel = hiltViewModel()
        com.qweld.app.admin.QuestionReportsScreen(
          viewModel = viewModel,
          onNavigateToDetail = { reportId ->
            navController.navigate(Routes.adminReportDetail(reportId))
          },
          onBack = { navController.popBackStack() }
        )
      }
      composable(Routes.ADMIN_DASHBOARD) {
        if (!BuildConfig.DEBUG) {
          UnauthorizedAdminScreen(onBack = { navController.popBackStack() })
        } else {
          val viewModel: AdminDashboardViewModel = hiltViewModel()
          AdminDashboardRoute(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
      }
      composable(
        route = Routes.ADMIN_REPORT_DETAIL,
        arguments = listOf(navArgument("reportId") { type = NavType.StringType })
      ) { backStackEntry ->
        val viewModel: com.qweld.app.admin.QuestionReportDetailViewModel =
          hiltViewModel(backStackEntry)
        com.qweld.app.admin.QuestionReportDetailScreen(
          viewModel = viewModel,
          onBack = { navController.popBackStack() }
        )
      }
    }
    val currentErrorEvent = pendingErrorEvent
    if (currentErrorEvent != null && errorReportingEnabled) {
      AppErrorReportDialog(
        onDismiss = {
          if (!isSubmittingError) {
            pendingErrorEvent = null
          }
        },
        onSubmit = { comment ->
          if (isSubmittingError) return@AppErrorReportDialog
          scope.launch {
            isSubmittingError = true
            val result = appErrorHandler.submitReport(currentErrorEvent, comment)
            isSubmittingError = false
            pendingErrorEvent = null
            val message =
              when (result) {
                AppErrorReportResult.Submitted -> errorReportingSubmittedMessage
                AppErrorReportResult.Disabled -> errorReportingDisabledMessage
                is AppErrorReportResult.Failed -> {
                  Timber.w("[app_error_report] dialog_submit_failed reason=%s", result.reason)
                  errorReportingFailedMessage
                }
              }
            snackbarHostState.showSnackbar(message = message)
          }
        },
        isSubmitting = isSubmittingError,
      )
    }
    if (showLogDialog && logExportActions != null) {
      LogExportDialog(
        onDismiss = { showLogDialog = false },
        onExportText = {
          showLogDialog = false
          logExportActions.exportText()
        },
        onExportJson = {
          showLogDialog = false
          logExportActions.exportJson()
        },
      )
    }
  }
}

@Composable
private fun LoadingScreen() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}

private enum class GoogleAction {
  SignIn,
  Link,
}

private data class LogExportActions(
  val exportText: () -> Unit,
  val exportJson: () -> Unit,
)

@Composable
private fun rememberLogExportActions(logCollector: LogCollector?): LogExportActions? {
  if (logCollector == null) return null
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val textLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument(LogExportFormat.TEXT.mimeType),
    ) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        try {
          val result = logCollector.writeTo(context, uri, LogExportFormat.TEXT)
          val attrs =
            "{\"exported_at\":\"${result.exportedAtIso}\",\"entries\":${result.entryCount}}"
          Timber.i(
            "[export_logs] format=%s uri=%s | attrs=%s",
            result.format.label,
            uri,
            attrs,
          )
        } catch (t: Throwable) {
          val reason = t.message ?: t::class.java.simpleName
          Timber.e(
            t,
            "[export_logs_error] format=%s reason=%s | attrs=%s",
            LogExportFormat.TEXT.label,
            reason,
            "{}",
          )
        }
      }
    }
  val jsonLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument(LogExportFormat.JSON.mimeType),
    ) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        try {
          val result = logCollector.writeTo(context, uri, LogExportFormat.JSON)
          val attrs =
            "{\"exported_at\":\"${result.exportedAtIso}\",\"entries\":${result.entryCount}}"
          Timber.i(
            "[export_logs] format=%s uri=%s | attrs=%s",
            result.format.label,
            uri,
            attrs,
          )
        } catch (t: Throwable) {
          val reason = t.message ?: t::class.java.simpleName
          Timber.e(
            t,
            "[export_logs_error] format=%s reason=%s | attrs=%s",
            LogExportFormat.JSON.label,
            reason,
            "{}",
          )
        }
      }
    }
  return remember(logCollector, textLauncher, jsonLauncher) {
    LogExportActions(
      exportText = { textLauncher.launch(logCollector.createDocumentName(LogExportFormat.TEXT)) },
      exportJson = { jsonLauncher.launch(logCollector.createDocumentName(LogExportFormat.JSON)) },
    )
  }
}

@Composable
private fun LogExportDialog(
  onDismiss: () -> Unit,
  onExportText: () -> Unit,
  onExportJson: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(id = com.qweld.app.R.string.export_logs_title)) },
    text = { Text(text = stringResource(id = com.qweld.app.R.string.export_logs_message)) },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onExportText) {
          Text(text = stringResource(id = com.qweld.app.R.string.export_logs_txt))
        }
        TextButton(onClick = onExportJson) {
          Text(text = stringResource(id = com.qweld.app.R.string.export_logs_json))
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(id = android.R.string.cancel))
      }
    },
  )
}

private fun <T> runAuthAction(
  scope: CoroutineScope,
  setLoading: (Boolean) -> Unit,
  setError: (String?) -> Unit,
  defaultError: String,
  actionLabel: String,
  appErrorHandler: AppErrorHandler? = null,
  block: suspend () -> T,
) {
  scope.launch {
    try {
      setLoading(true)
      setError(null)
      block()
    } catch (exception: Exception) {
      setError(exception.localizedMessage?.takeIf { it.isNotBlank() } ?: defaultError)
      appErrorHandler?.handle(
        AppError.Unexpected(
          cause = exception,
          context = ErrorContext(screen = "auth", action = actionLabel),
        )
      )
    } finally {
      setLoading(false)
    }
  }
}

private object Routes {
  const val AUTH = "auth"
  const val EXAM = "exam"
  const val SYNC = "sync"
  const val SETTINGS = "settings"
  const val ABOUT = "about"
  const val ADMIN_REPORTS = "admin/reports"
  const val ADMIN_REPORT_DETAIL = "admin/reports/{reportId}"
  const val ADMIN_DASHBOARD = "admin/dashboard"

  fun adminReportDetail(reportId: String) = "admin/reports/$reportId"
}

@Composable
private fun SyncScreen(onBack: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(id = com.qweld.app.R.string.sync_coming_soon),
      style = MaterialTheme.typography.headlineSmall,
    )
    Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
      Text(text = stringResource(id = com.qweld.app.R.string.sync_back_to_exam))
    }
  }
}

@Composable
private fun UnauthorizedAdminScreen(onBack: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(id = com.qweld.app.R.string.admin_dashboard_unavailable),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
      Text(text = stringResource(id = android.R.string.cancel))
    }
  }
}
