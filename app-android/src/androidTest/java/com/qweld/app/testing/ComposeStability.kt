package com.qweld.app.testing

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

/**
 * Compose stability utilities shared across instrumentation tests.
 */
object ComposeStability {

  /**
   * Confirms that at least one Compose root is registered. Retries until [timeoutMs]
   * before throwing to avoid "No compose hierarchies found" races.
   */
  fun <A : ComponentActivity> ensureComposeReady(
    composeRule: AndroidComposeTestRule<*, A>,
    timeoutMs: Long = 10_000,
  ) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs

    while (SystemClock.uptimeMillis() < deadline) {
      val hasRoots = runCatching {
        composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty()
      }.getOrDefault(false)

      if (hasRoots) return

      SystemClock.sleep(50)
    }

    throw AssertionError("Compose hierarchies did not appear within ${timeoutMs}ms")
  }
}
