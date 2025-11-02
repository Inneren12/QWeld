package com.qweld.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweld.app.BuildConfig
import com.qweld.app.R
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
  LaunchedEffect(Unit) { Timber.i("[about_open]") }
  val uriHandler = LocalUriHandler.current
  val gitSha = BuildConfig.GIT_SHA.takeIf { it.isNotBlank() && it.lowercase() != "local" }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(id = R.string.about_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = stringResource(id = R.string.app_name),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(
            id = R.string.about_version_value,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
          ),
          style = MaterialTheme.typography.bodyLarge,
        )
        Text(
          text = stringResource(id = R.string.about_build_time_value, BuildConfig.BUILD_TIME),
          style = MaterialTheme.typography.bodyLarge,
        )
        gitSha?.let { sha ->
          Text(
            text = stringResource(id = R.string.about_commit_value, sha),
            style = MaterialTheme.typography.bodyLarge,
          )
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = stringResource(id = R.string.about_links_title),
        style = MaterialTheme.typography.titleMedium,
      )
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { uriHandler.openUri(stringResource(id = R.string.privacy_url)) }) {
          Text(text = stringResource(id = R.string.about_privacy_policy))
        }
        TextButton(onClick = { uriHandler.openUri(stringResource(id = R.string.content_policy_url)) }) {
          Text(text = stringResource(id = R.string.about_content_policy))
        }
        TextButton(
          onClick = {
            val email = stringResource(id = R.string.contact_email)
            uriHandler.openUri("mailto:$email")
          },
        ) {
          Text(text = stringResource(id = R.string.about_contact))
        }
      }
    }
  }
}
