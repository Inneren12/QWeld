package com.qweld.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.qweld.app.feature.auth.AuthService.User

@Composable
fun RequireUser(
  user: User?,
  onRequire: () -> Unit,
  content: @Composable () -> Unit,
) {
  if (user == null) {
    LaunchedEffect(user) { onRequire() }
  } else {
    content()
  }
}

@Composable
fun RequireNonAnonymous(
  user: User?,
  onRequire: () -> Unit,
  content: @Composable () -> Unit,
) {
  if (user == null || user.isAnonymous) {
    LaunchedEffect(user) { onRequire() }
  } else {
    content()
  }
}
