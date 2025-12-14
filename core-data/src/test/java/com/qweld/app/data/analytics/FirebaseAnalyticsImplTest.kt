package com.qweld.app.data.analytics

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAnalyticsImplTest {
  @Test
  fun `log skips when analytics disabled`() {
    val backend = RecordingBackend()
    val analytics = FirebaseAnalyticsImpl(backend = backend, isEnabled = false)

    analytics.log("test_event", mapOf("key" to "value"))

    assertEquals(listOf(false), backend.enabledStates)
    assertTrue(backend.events.isEmpty())
  }

  @Test
  fun `log honors opt-out toggles`() {
    val backend = RecordingBackend()
    val analytics = FirebaseAnalyticsImpl(backend = backend, isEnabled = true)

    analytics.setEnabled(false)
    analytics.log("should_skip")

    analytics.setEnabled(true)
    analytics.log("should_send", mapOf("foo" to "bar"))

    assertEquals(listOf(true, false, true), backend.enabledStates)
    assertEquals(1, backend.events.size)
    val (name, bundle) = backend.events.single()
    assertEquals("should_send", name)
    assertEquals("bar", bundle.getString("foo"))
  }

  private class RecordingBackend : AnalyticsBackend {
    val enabledStates = mutableListOf<Boolean>()
    val events = mutableListOf<Pair<String, Bundle>>()

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
      enabledStates.add(enabled)
    }

    override fun logEvent(event: String, params: Bundle) {
      events.add(event to params)
    }
  }
}
