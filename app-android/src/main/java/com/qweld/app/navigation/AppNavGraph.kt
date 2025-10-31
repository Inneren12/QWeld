package com.qweld.app.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.qweld.app.data.analytics.Analytics
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
import com.qweld.app.ui.AccountMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.compose.ui.unit.dp

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
  modifier: Modifier = Modifier,
) {
  val navController = rememberNavController()
  val user by authService.currentUser.collectAsStateWithLifecycle(initialValue = null)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val genericErrorText = stringResource(id = R.string.auth_error_generic)
  val googleCancelledText = stringResource(id = R.string.auth_error_google_sign_in)
  val missingTokenText = stringResource(id = R.string.auth_error_missing_token)
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var pendingGoogleAction by remember { mutableStateOf<GoogleAction?>(null) }
  val googleSignInClient = remember {
    val options =
      GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(com.qweld.app.R.string.default_web_client_id))
        .requestEmail()
        .build()
    GoogleSignIn.getClient(context, options)
  }

  val navigateToAuth: () -> Unit = {
    navController.navigate(Routes.AUTH) {
      popUpTo(navController.graph.startDestinationId) { inclusive = true }
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
    when (action) {
      GoogleAction.SignIn ->
        analytics.log("auth_signin", mapOf("provider" to "google", "method" to "oauth"))
      GoogleAction.Link ->
        analytics.log("auth_link", mapOf("provider" to "google", "method" to "oauth"))
    }
    pendingGoogleAction = action
    errorMessage = null
    isLoading = true
    googleLauncher.launch(googleSignInClient.signInIntent)
  }

  LaunchedEffect(user) {
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    when {
      user == null -> navigateToAuth()
      user.isAnonymous -> navigateToAuth()
      currentRoute == Routes.AUTH -> {
        navController.navigate(Routes.EXAM) {
          popUpTo(navController.graph.startDestinationId) { inclusive = true }
          launchSingleTop = true
        }
      }
    }
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      AccountMenu(
        user = user,
        onNavigateToSync = {
          Timber.i("[ui_nav] screen=Sync")
          navController.navigate(Routes.SYNC) { launchSingleTop = true }
        },
        onSignOut = {
          scope.launch {
            runCatching { authService.signOut() }
              .onSuccess { navigateToAuth() }
              .onFailure { Timber.e(it, "Sign out failed") }
          }
        },
      )
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
                analytics.log(
                  "auth_signin",
                  mapOf("provider" to "anonymous", "method" to "anonymous"),
                )
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
                analytics.log(
                  "auth_signin",
                  mapOf("provider" to "password", "method" to "password"),
                )
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
        )
      }
      composable(Routes.SYNC) {
        RequireNonAnonymous(
          user = user,
          onRequire = {
            val reason = when {
              user == null -> "no_user"
              user.isAnonymous -> "anonymous"
              else -> "unknown"
            }
            Timber.i("[auth_guard] route=/sync allowed=false reason=%s", reason)
            navigateToAuth()
          },
        ) {
          SyncScreen(onBack = { navController.popBackStack() })
        }
      }
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
