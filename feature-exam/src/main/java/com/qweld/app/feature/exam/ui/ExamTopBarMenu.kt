package com.qweld.app.feature.exam.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.qweld.app.feature.exam.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamTopBarMenu(
  onStartOver: () -> Unit,
  onExit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  CenterAlignedTopAppBar(
    modifier = modifier,
    title = { },
    actions = {
      val exitLabel = stringResource(id = R.string.exit)
      val exitCd = stringResource(id = R.string.menu_exit)
      ExitTopBarAction(
        label = exitLabel,
        onClick = onExit,
        modifier = Modifier.semantics { contentDescription = exitCd },
      )
      val restartCd = stringResource(id = R.string.menu_start_over)
      IconButton(
        onClick = onStartOver,
        modifier = Modifier.semantics {
          role = Role.Button
          contentDescription = restartCd
        },
      ) {
        Icon(imageVector = Icons.Outlined.RestartAlt, contentDescription = null)
      }
    },
  )
}
