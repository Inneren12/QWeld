package com.qweld.app.data.analytics

import android.os.Bundle
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirebaseAnalyticsImplTest {
  private lateinit var backend: RecordingBackend

  @BeforeTest
  fun setUp() {
    backend = RecordingBackend()
  }

  @Test
  fun log_whenOptedOut_doesNotForward() {
    val analytics = FirebaseAnalyticsImpl(backend, isEnabled = false)

    analytics.log("exam_start", mapOf("mode" to "practice"))

    assertTrue(backend.events.isEmpty())
  }

  @Test
  fun log_whenEnabled_forwardsEvent() {
    val analytics = FirebaseAnalyticsImpl(backend, isEnabled = true)

    analytics.log("exam_start", mapOf("mode" to "practice", "questions" to 10))

    val recorded = backend.events.single()
    assertEquals("exam_start", recorded.first)
    assertEquals("practice", recorded.second.getString("mode"))
    assertEquals(10, recorded.second.getInt("questions"))
  }

  private class RecordingBackend : AnalyticsBackend {
    val events = mutableListOf<Pair<String, Bundle>>()
    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}

    override fun logEvent(event: String, params: Bundle) {
      events += event to Bundle(params)
    }
  }
}
