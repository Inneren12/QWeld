package com.qweld.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qweld.app.R

@Composable
fun AboutScreen(
  modifier: Modifier = Modifier,
  onExportDiagnostics: (() -> Unit)? = null,
) {
  val uriHandler = LocalUriHandler.current

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text(text = stringResource(id = R.string.about_title), style = MaterialTheme.typography.headlineMedium)
    Divider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      val privacyUrl = stringResource(R.string.privacy_url)
      val contentPolicyUrl = stringResource(R.string.content_policy_url)
      val contactEmail = stringResource(R.string.contact_email)

      TextButton(onClick = { uriHandler.openUri(privacyUrl) }) {
        Text(text = stringResource(id = R.string.about_privacy_policy))
      }
      TextButton(onClick = { uriHandler.openUri(contentPolicyUrl) }) {
        Text(text = stringResource(id = R.string.about_content_policy))
      }
      TextButton(onClick = { uriHandler.openUri("mailto:$contactEmail") }) {
        Text(text = stringResource(id = R.string.about_contact))
      }
    }
    Divider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(text = stringResource(R.string.about_diagnostics_title), style = MaterialTheme.typography.titleMedium)
      Text(
        text = stringResource(R.string.about_diagnostics_summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (onExportDiagnostics != null) {
        TextButton(onClick = onExportDiagnostics) {
          Text(text = stringResource(R.string.about_export_diagnostics))
        }
      } else {
        Text(
          text = stringResource(R.string.about_diagnostics_unavailable),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
