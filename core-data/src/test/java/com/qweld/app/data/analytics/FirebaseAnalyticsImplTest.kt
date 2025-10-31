package com.qweld.app.data.analytics

import android.os.Bundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirebaseAnalyticsImplTest {
  private val backend = RecordingBackend()

  @Test
  fun log_whenOptedOut_doesNotForward() {
    val analytics = FirebaseAnalyticsImpl(backend, isEnabled = false)

    analytics.log("exam_start", mapOf("mode" to "practice"))

    assertTrue(backend.events.isEmpty())
  }

  @Test
  fun log_whenEnabled_forwardsEvent() {
    val analytics = FirebaseAnalyticsImpl(backend, isEnabled = true)

    analytics.log("exam_start", mapOf("mode" to "practice", "totals" to mapOf("questions" to 10)))

    val recorded = backend.events.single()
    assertEquals("exam_start", recorded.first)
    assertEquals("practice", recorded.second["mode"])
    val totals = recorded.second["totals"] as Bundle
    assertEquals(10, totals.getInt("questions"))
  }

  private class RecordingBackend : AnalyticsBackend {
    val events = mutableListOf<Pair<String, Bundle>>()
    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}

    override fun logEvent(event: String, params: Bundle) {
      events += event to Bundle(params)
    }
  }
}
