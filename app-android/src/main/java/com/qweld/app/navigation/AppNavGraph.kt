package com.qweld.app.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.qweld.app.BuildConfig
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.content.ContentIndexReader
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.logging.LogExportFormat
import com.qweld.app.data.logging.writeTo
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.auth.AuthService
import com.qweld.app.feature.auth.R
import com.qweld.app.feature.auth.ui.LinkAccountScreen
import com.qweld.app.feature.auth.ui.SignInScreen
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.navigation.ExamNavGraph
import com.qweld.app.feature.exam.vm.PrewarmConfig
import com.qweld.app.ui.AboutScreen
import com.qweld.app.ui.SettingsScreen
import com.qweld.app.ui.TopBarMenus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

private const val START_ROUTE = Routes.AUTH

@Composable
fun AppNavGraph(
    authService: AuthService,
    questionRepository: AssetQuestionRepository,
    explanationRepository: AssetExplanationRepository,
    attemptsRepository: AttemptsRepository,
    answersRepository: AnswersRepository,
    statsRepository: UserStatsRepository,
    appVersion: String,
    analytics: Analytics,
    logCollector: LogCollector?,
    userPrefs: UserPrefsDataStore,
    contentIndexReader: ContentIndexReader,
    appLocaleTag: String,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    val user by authService.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()

    // Безопасная навигация с builder-ом опций
    val safeNavigate = remember(navController, scope) {
        { route: String, opts: (NavOptionsBuilder.() -> Unit)? ->
            scope.launch {
                // привязываемся к актуальному back stack entry (исправляет гонки)
                runCatching { navController.currentBackStackEntryFlow.first() }
                navController.navigate(route) {
                    if (opts != null) opts(this)
                }
            }
        }
    }

    val genericErrorText = stringResource(id = R.string.auth_error_generic)
    val googleCancelledText = stringResource(id = R.string.auth_error_google_sign_in)
    val missingTokenText = stringResource(id = R.string.auth_error_missing_token)

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingGoogleAction by remember { mutableStateOf<GoogleAction?>(null) }

    // Google Sign-In client: requestIdToken только если реально есть default_web_client_id
    val googleSignInClient = remember {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .apply { googleWebClientIdOrNull(context)?.let { requestIdToken(it) } }
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    // ---------- Язык: читаем текущий тег из DataStore, меняем ТОЛЬКО DataStore ----------
    val appLocale = appLocaleTag

    val handleLocaleSelection = remember(userPrefs, scope) {
        { tag: String, source: String ->
            Timber.i("[settings_locale] select tag=%s (source=%s)", tag, source)
            scope.launch {
                // единый источник истины — DataStore
                userPrefs.setAppLocale(tag)
            }
            // Применение locale делает MainActivity (наблюдает DataStore и делает recreate()).
        }
    }
    // -------------------------------------------------------------------------------

    val navigateToAuth: () -> Unit = {
        safeNavigate(Routes.AUTH) {
            popUpTo(START_ROUTE) { inclusive = true }
            launchSingleTop = true
        }
    }

    val googleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val action = pendingGoogleAction
            pendingGoogleAction = null
            if (result.resultCode == Activity.RESULT_OK && action != null) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    val idToken = account?.idToken
                    if (idToken != null) {
                        runAuthAction(
                            scope = scope,
                            setLoading = { isLoading = it },
                            setError = { errorMessage = it },
                            defaultError = genericErrorText,
                        ) {
                            when (action) {
                                GoogleAction.SignIn -> authService.signInWithGoogle(idToken)
                                GoogleAction.Link -> authService.linkAnonymousToGoogle(idToken)
                            }
                        }
                        return@rememberLauncherForActivityResult
                    } else {
                        errorMessage = missingTokenText
                    }
                } catch (exception: ApiException) {
                    Timber.e(exception, "Google sign-in failed")
                    errorMessage = exception.localizedMessage ?: googleCancelledText
                }
            } else if (action != null) {
                errorMessage = googleCancelledText
            }
            isLoading = false
        }

    fun startGoogle(action: GoogleAction) {
        pendingGoogleAction = action
        errorMessage = null
        isLoading = true
        googleLauncher.launch(googleSignInClient.signInIntent)
    }

    LaunchedEffect(user) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val u = user
        if (u == null) {
            navigateToAuth()
        } else if (u.isAnonymous) {
            navigateToAuth()
        } else if (currentRoute == Routes.AUTH) {
            safeNavigate(Routes.EXAM) {
                popUpTo(START_ROUTE) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val logExportActions = rememberLogExportActions(logCollector)
    var showLogDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
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
                        { showLogDialog = true }
                    } else null,
                currentLocaleTag = appLocale,
                onLocaleSelected = { tag -> handleLocaleSelection(tag, "topbar") },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = START_ROUTE,
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
                    attemptsRepository = attemptsRepository,
                    answersRepository = answersRepository,
                    statsRepository = statsRepository,
                    appVersion = appVersion,
                    analytics = analytics,
                    userPrefs = userPrefs,
                    prewarmConfig = prewarmConfigFromBuildConfig(),
                )
            }
            composable(Routes.SYNC) {
                // Если у тебя есть RequireNonAnonymous, оставь как было; иначе просто показываем экран.
                SyncScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    userPrefs = userPrefs,
                    attemptsRepository = attemptsRepository,
                    answersRepository = answersRepository,
                    questionRepository = questionRepository,
                    contentIndexReader = contentIndexReader,
                    appLocaleTag = appLocale,
                    onLocaleSelected = { tag -> handleLocaleSelection(tag, "settings") },
                    onExportLogs =
                        if (logExportActions != null) { { showLogDialog = true } } else null,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    onExportDiagnostics =
                        if (logExportActions != null) { { showLogDialog = true } } else null,
                )
            }
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
    SignIn, Link
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
                    val attrs = "{\"exported_at\":\"${result.exportedAtIso}\",\"entries\":${result.entryCount}}"
                    Timber.i("[export_logs] format=%s uri=%s | attrs=%s", result.format.label, uri, attrs)
                } catch (t: Throwable) {
                    val reason = t.message ?: t::class.java.simpleName
                    Timber.e(t, "[export_logs_error] format=%s reason=%s | attrs={}", LogExportFormat.TEXT.label, reason)
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
                    val attrs = "{\"exported_at\":\"${result.exportedAtIso}\",\"entries\":${result.entryCount}}"
                    Timber.i("[export_logs] format=%s uri=%s | attrs=%s", result.format.label, uri, attrs)
                } catch (t: Throwable) {
                    val reason = t.message ?: t::class.java.simpleName
                    Timber.e(t, "[export_logs_error] format=%s reason=%s | attrs={}", LogExportFormat.JSON.label, reason)
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
                TextButton(onClick = onExportText) { Text(text = stringResource(id = com.qweld.app.R.string.export_logs_txt)) }
                TextButton(onClick = onExportJson) { Text(text = stringResource(id = com.qweld.app.R.string.export_logs_json)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(id = android.R.string.cancel)) } },
    )
}

/**
 * Безопасно вернуть default_web_client_id, если он существует.
 * Ресурс создаётся плагином google-services только при наличии google-services.json.
 */
private fun googleWebClientIdOrNull(context: Context): String? {
    val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (id == 0) return null
    val value = context.getString(id)
    return value.takeIf { it.isNotBlank() && it != "YOUR_WEB_CLIENT_ID" }
}

private fun <T> runAuthAction(
    scope: CoroutineScope,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    defaultError: String,
    block: suspend () -> T,
) {
    scope.launch {
        try {
            setLoading(true)
            setError(null)
            block()
        } catch (exception: Exception) {
            setError(exception.localizedMessage?.takeIf { it.isNotBlank() } ?: defaultError)
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
}

// ——— Utils ———
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun prewarmConfigFromBuildConfig(): PrewarmConfig {
    val enabled = BuildConfig.PREWARM_ENABLED
    val maxConc = (BuildConfig.PREWARM_MAX_CONCURRENCY as? Number)?.toInt()
        ?: BuildConfig.PREWARM_MAX_CONCURRENCY.toString().toDouble().toInt()
    val timeoutMs: Long = (BuildConfig.PREWARM_TIMEOUT_MS as? Number)?.toLong()
        ?: BuildConfig.PREWARM_TIMEOUT_MS.toString().toDouble().toLong()
    return PrewarmConfig(enabled = enabled, maxConcurrency = maxConc, taskTimeoutMs = timeoutMs)
}

@Composable
private fun SyncScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(id = com.qweld.app.R.string.sync_coming_soon), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text(text = stringResource(id = com.qweld.app.R.string.sync_back_to_exam))
        }
    }
}
