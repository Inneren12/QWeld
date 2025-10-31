package com.qweld.app.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.Locale
import timber.log.Timber

interface Analytics {
  fun log(event: String, params: Map<String, Any?> = emptyMap())
}

class FirebaseAnalyticsImpl(
  private val firebaseAnalytics: FirebaseAnalytics,
  private val isEnabled: Boolean,
) : Analytics {

  init {
    firebaseAnalytics.setAnalyticsCollectionEnabled(isEnabled)
  }

  override fun log(event: String, params: Map<String, Any?>) {
    val sanitized = params.filterValues { it != null }
    Timber.i("[analytics] event=%s params=%s", event, sanitized)
    if (!isEnabled) return
    val bundle = Bundle()
    sanitized.forEach { (key, value) ->
      value?.let { bundle.putParam(key, it) }
    }
    firebaseAnalytics.logEvent(event, bundle)
  }

  private fun Bundle.putParam(key: String, value: Any) {
    when (value) {
      is String -> putString(key, value)
      is Int -> putInt(key, value)
      is Long -> putLong(key, value)
      is Double -> putDouble(key, value)
      is Float -> putDouble(key, value.toDouble())
      is Boolean -> putInt(key, if (value) 1 else 0)
      is Enum<*> -> putString(key, value.name.lowercase(Locale.US))
      else -> putString(key, value.toString())
    }
  }
}
