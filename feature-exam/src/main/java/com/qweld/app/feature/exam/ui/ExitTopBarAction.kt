package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag

@Composable
internal fun ExitTopBarAction(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  TextButton(
    onClick = onClick,
    modifier = modifier
      .testTag("btn-exit")
      .semantics { role = Role.Button },
  ) {
    Icon(imageVector = Icons.Outlined.ExitToApp, contentDescription = null)
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = label)
  }
}
