package com.qweld.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.qweld.app.feature.auth.AuthService.User

@Composable
@Suppress("unused") // reserved for future guards
    /**
     * Guard for "any signed-in user" (anonymous or not).
     * Currently unused; kept for future flows that only require sign-in,
     * while [RequireNonAnonymous] is used where a non-anonymous profile is required.
     */
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
