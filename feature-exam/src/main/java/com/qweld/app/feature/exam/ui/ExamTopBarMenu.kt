package com.qweld.app.feature.exam.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
  var expanded by remember { mutableStateOf(false) }
  CenterAlignedTopAppBar(
    modifier = modifier,
    title = { Text(text = stringResource(id = R.string.exam_title)) },
    actions = {
      val exitLabel = stringResource(id = R.string.exit)
      val exitCd = stringResource(id = R.string.menu_exit)
      ExitTopBarAction(
        label = exitLabel,
        onClick = onExit,
        modifier = Modifier.semantics { contentDescription = exitCd },
      )
      val menuCd = stringResource(id = R.string.exam_overflow_cd)
      IconButton(
        onClick = { expanded = true },
        modifier = Modifier.semantics {
          role = Role.Button
          contentDescription = menuCd
        },
      ) {
        Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        val restartCd = stringResource(id = R.string.menu_start_over)
        DropdownMenuItem(
          text = { Text(text = stringResource(id = R.string.menu_start_over)) },
          onClick = {
            expanded = false
            onStartOver()
          },
          modifier = Modifier.semantics {
            role = Role.Button
            contentDescription = restartCd
          },
        )
      }
    },
  )
}
