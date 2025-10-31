package com.qweld.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.qweld.app.feature.exam.data.AssetExplanationRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.navigation.ExamNavGraph
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
  val navController = rememberNavController()
  val questionRepository = remember(appContext) { AssetQuestionRepository(appContext) }
  val explanationRepository = remember(appContext) { AssetExplanationRepository(appContext) }
  MaterialTheme {
    ExamNavGraph(
      navController = navController,
      repository = questionRepository,
      explanationRepository = explanationRepository,
    )
  }
}
