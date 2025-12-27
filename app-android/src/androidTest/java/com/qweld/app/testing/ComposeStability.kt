package com.qweld.app.testing

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.Lifecycle

/**
 * Compose stability utilities shared across instrumentation tests.
 */
object ComposeStability {

  /**
   * Brings the underlying ActivityScenario back to RESUMED (tolerating transient failures)
   * and confirms that at least one Compose root is registered. Retries until [timeoutMs]
   * before throwing to avoid "No compose hierarchies found" and STOPPED → RESUMED races.
   */
  fun <A : ComponentActivity> ensureComposeReady(
    composeRule: AndroidComposeTestRule<*, A>,
    timeoutMs: Long = 10_000,
  ) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs

    while (SystemClock.uptimeMillis() < deadline) {
      // Attempt to bring the activity back to RESUMED; ignore transient failures
      runCatching { composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED) }

      val hasRoots = runCatching {
        composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty()
      }.getOrDefault(false)

      if (hasRoots) return

      SystemClock.sleep(50)
    }

    throw AssertionError("Compose hierarchies did not appear within ${timeoutMs}ms")
  }
}
