package com.qweld.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.repo.UserStatsRepositoryRoom
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.auth.firebase.FirebaseAuthService
import com.qweld.app.navigation.AppNavGraph
import com.google.firebase.auth.FirebaseAuth
import timber.log.Timber

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.i("[ui] screen=Main | attrs=%s", "{\"start\":true}")
    setContent { QWeldAppRoot() }
  }
}

@Composable
fun QWeldAppRoot() {
  val context = LocalContext.current
  val appContext = context.applicationContext
  val questionRepository = remember(appContext) { AssetQuestionRepository(appContext) }
  val explanationRepository = remember(appContext) { AssetExplanationRepository(appContext) }
  val database = remember(appContext) { QWeldDb.create(appContext) }
  val attemptsRepository = remember(database) { AttemptsRepository(database.attemptDao()) }
  val answersRepository = remember(database) { AnswersRepository(database.answerDao()) }
  val statsRepository = remember(database) { UserStatsRepositoryRoom(database.answerDao()) }
  val authService = remember { FirebaseAuthService(FirebaseAuth.getInstance()) }
  MaterialTheme {
    AppNavGraph(
      authService = authService,
      questionRepository = questionRepository,
      explanationRepository = explanationRepository,
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      statsRepository = statsRepository,
      appVersion = BuildConfig.VERSION_NAME,
    )
  }
}
