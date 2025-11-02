package com.qweld.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qweld.app.R
import com.qweld.app.feature.auth.AuthService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarMenus(
  user: AuthService.User?,
  onNavigateToSync: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToAbout: () -> Unit,
  onSignOut: () -> Unit,
  onExportLogs: (() -> Unit)? = null,
  currentLocaleTag: String,
  onLocaleSelected: (String) -> Unit,
) {
  TopAppBar(
    title = { Text(text = stringResource(id = R.string.app_name)) },
    actions = {
      OverflowMenu(
        currentLocaleTag = currentLocaleTag,
        onLocaleSelected = onLocaleSelected,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToAbout = onNavigateToAbout,
        onExportLogs = onExportLogs,
      )
      AccountMenu(
        user = user,
        onNavigateToSync = onNavigateToSync,
        onSignOut = onSignOut,
      )
    },
  )
}

@Composable
private fun OverflowMenu(
  currentLocaleTag: String,
  onLocaleSelected: (String) -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToAbout: () -> Unit,
  onExportLogs: (() -> Unit)?,
) {
  var expanded by remember { mutableStateOf(false) }
  var showLanguageDialog by remember { mutableStateOf(false) }
  IconButton(onClick = { expanded = true }) {
    Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = null)
  }
  DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    DropdownMenuItem(
      text = { Text(text = stringResource(id = R.string.language)) },
      onClick = {
        expanded = false
        showLanguageDialog = true
      },
    )
    DropdownMenuItem(
      text = { Text(text = stringResource(id = R.string.menu_settings)) },
      onClick = {
        expanded = false
        onNavigateToSettings()
      },
    )
    DropdownMenuItem(
      text = { Text(text = stringResource(id = R.string.menu_about)) },
      onClick = {
        expanded = false
        onNavigateToAbout()
      },
    )
    onExportLogs?.let { exportLogs ->
      DropdownMenuItem(
        text = { Text(text = stringResource(id = R.string.menu_export_logs)) },
        onClick = {
          expanded = false
          exportLogs()
        },
      )
    }
  }
  if (showLanguageDialog) {
    LanguageSelectionDialog(
      currentLocaleTag = currentLocaleTag,
      onDismissRequest = { showLanguageDialog = false },
      onTagSelected = { tag ->
        showLanguageDialog = false
        onLocaleSelected(tag)
      },
    )
  }
}

@Composable
private fun LanguageSelectionDialog(
  currentLocaleTag: String,
  onDismissRequest: () -> Unit,
  onTagSelected: (String) -> Unit,
) {
  val options =
    listOf(
      "system" to R.string.language_system,
      "en" to R.string.language_en,
      "ru" to R.string.language_ru,
    )
  AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = {},
    title = { Text(text = stringResource(id = R.string.language)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { (tag, labelRes) ->
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .selectable(selected = currentLocaleTag == tag, onClick = { onTagSelected(tag) })
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            RadioButton(selected = currentLocaleTag == tag, onClick = null)
            Text(text = stringResource(id = labelRes), style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
    },
  )
}

@Composable
private fun AccountMenu(
  user: AuthService.User?,
  onNavigateToSync: () -> Unit,
  onSignOut: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  IconButton(onClick = { expanded = true }) {
    Icon(imageVector = Icons.Outlined.AccountCircle, contentDescription = null)
  }
  DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    DropdownMenuItem(
      text = { Text(text = stringResource(id = R.string.menu_sync)) },
      onClick = {
        expanded = false
        onNavigateToSync()
      },
    )
    if (user != null) {
      DropdownMenuItem(
        text = { Text(text = stringResource(id = R.string.menu_sign_out)) },
        onClick = {
          expanded = false
          onSignOut()
        },
      )
    }
  }
}
