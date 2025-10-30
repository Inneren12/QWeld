package com.qweld.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import timber.log.Timber

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.i("[ui] screen=Main | attrs=%s", "{\"start\":true}")
    setContent { QWeldAppRoot() }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QWeldAppRoot() {
  MaterialTheme {
    Scaffold(
      topBar = { TopAppBar(title = { Text(text = stringResource(id = R.string.app_name)) }) }
    ) { paddingValues ->
      Text(
        modifier = Modifier.padding(paddingValues).padding(16.dp),
        text = stringResource(id = R.string.hello_qweld)
      )
    }
  }
}
