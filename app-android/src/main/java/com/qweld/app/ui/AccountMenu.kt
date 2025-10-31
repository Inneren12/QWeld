package com.qweld.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.qweld.app.R
import com.qweld.app.feature.auth.AuthService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMenu(
  user: AuthService.User?,
  onNavigateToSync: () -> Unit,
  onSignOut: () -> Unit,
  onExportLogs: (() -> Unit)? = null,
) {
  TopAppBar(
    title = { Text(text = stringResource(id = R.string.app_name)) },
    actions = {
      var expanded by remember { mutableStateOf(false) }
      IconButton(onClick = { expanded = true }) {
        Icon(imageVector = Icons.Outlined.AccountCircle, contentDescription = null)
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        onExportLogs?.let { exportLogs ->
          DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.menu_export_logs)) },
            onClick = {
              expanded = false
              exportLogs()
            },
          )
        }
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
    },
  )
}
