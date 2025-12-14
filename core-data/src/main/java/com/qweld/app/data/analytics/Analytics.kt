package com.qweld.app.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.qweld.app.domain.exam.ExamMode
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

/**
 * Analytics wrapper. Events must remain PII-free and respect user opt-out via [setEnabled].
 */
interface Analytics {
  fun log(event: String, params: Map<String, Any?> = emptyMap())
}

internal interface AnalyticsBackend {
  fun setAnalyticsCollectionEnabled(enabled: Boolean)
  fun logEvent(event: String, params: Bundle)
}

private class FirebaseAnalyticsBackend(
  private val delegate: FirebaseAnalytics,
) : AnalyticsBackend {
  override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
    delegate.setAnalyticsCollectionEnabled(enabled)
  }

  override fun logEvent(event: String, params: Bundle) {
    delegate.logEvent(event, params)
  }
}

class FirebaseAnalyticsImpl internal constructor(
  private val backend: AnalyticsBackend,
  isEnabled: Boolean,
) : Analytics {

  constructor(
    firebaseAnalytics: FirebaseAnalytics,
    isEnabled: Boolean,
  ) : this(FirebaseAnalyticsBackend(firebaseAnalytics), isEnabled)

  private val enabled = AtomicBoolean(isEnabled).also { backend.setAnalyticsCollectionEnabled(isEnabled) }

  fun setEnabled(value: Boolean) {
    enabled.set(value)
    backend.setAnalyticsCollectionEnabled(value)
  }

  override fun log(event: String, params: Map<String, Any?>) {
    if (!enabled.get()) {
      Timber.i("[analytics] skipped=true reason=optout event=%s", event)
      return
    }
    val sanitized = params.filterValues { it != null }
    Timber.i("[analytics] event=%s params=%s", event, sanitized)
    val bundle = Bundle()
    sanitized.forEach { (key, value) ->
      value?.let { bundle.putParam(key, it) }
    }
    backend.logEvent(event, bundle)
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
      is Map<*, *> -> putBundle(key, value.toBundle())
      else -> putString(key, value.toString())
    }
  }

  private fun Map<*, *>.toBundle(): Bundle {
    val bundle = Bundle()
    forEach { (key, value) ->
      if (key is String && value != null) {
        bundle.putParam(key, value)
      }
    }
    return bundle
  }
}

fun Analytics.logExamStart(mode: ExamMode, locale: String, totalQuestions: Int) {
  log(
    "exam_start",
    mapOf(
      "mode" to mode.name.lowercase(Locale.US),
      "locale" to locale.uppercase(Locale.US),
      "totals" to mapOf("questions" to totalQuestions),
    ),
  )
}

fun Analytics.logExamFinish(
  mode: ExamMode,
  locale: String,
  totalQuestions: Int,
  correctTotal: Int,
  scorePercent: Double,
) {
  log(
    "exam_finish",
    mapOf(
      "mode" to mode.name.lowercase(Locale.US),
      "locale" to locale.uppercase(Locale.US),
      "score_pct" to scorePercent,
      "totals" to mapOf(
        "questions" to totalQuestions,
        "correct" to correctTotal,
        "incorrect" to (totalQuestions - correctTotal),
      ),
    ),
  )
}

fun Analytics.logAnswerSubmit(
  mode: ExamMode,
  locale: String,
  questionPosition: Int,
  totalQuestions: Int,
) {
  log(
    "answer_submit",
    mapOf(
      "mode" to mode.name.lowercase(Locale.US),
      "locale" to locale.uppercase(Locale.US),
      "question_position" to questionPosition,
      "totals" to mapOf("questions" to totalQuestions),
    ),
  )
}

data class AnalyticsDeficitDetail(
  val taskId: String,
  val missing: Int,
)

fun Analytics.logDeficitDialog(details: List<AnalyticsDeficitDetail>) {
  if (details.isEmpty()) {
    log("deficit_dialog")
    return
  }
  val totals = mapOf(
    "tasks" to details.size,
    "missing" to details.sumOf { it.missing },
  )
  val taskIds =
    details.mapNotNull { it.taskId.takeIf { id -> id.isNotBlank() } }.joinToString(separator = ",")
      .takeIf { it.isNotBlank() }
  log(
    "deficit_dialog",
    mapOf(
      "totals" to totals,
      "task_ids" to taskIds,
    ),
  )
}

fun Analytics.logReviewOpen(
  mode: ExamMode,
  totalQuestions: Int,
  correctTotal: Int,
  scorePercent: Double,
  flaggedTotal: Int,
) {
  log(
    "review_open",
    mapOf(
      "mode" to mode.name.lowercase(Locale.US),
      "score_pct" to scorePercent,
      "totals" to mapOf(
        "questions" to totalQuestions,
        "correct" to correctTotal,
        "flagged" to flaggedTotal,
      ),
    ),
  )
}

fun Analytics.logExplainFetch(
  taskId: String,
  locale: String,
  found: Boolean,
  rationaleAvailable: Boolean,
) {
  log(
    "explain_fetch",
    mapOf(
      "taskId" to taskId,
      "locale" to locale.uppercase(Locale.US),
      "found" to found,
      "rationale_available" to rationaleAvailable,
    ),
  )
}

fun Analytics.logAuthSignIn(method: String) {
  log("auth_signin", mapOf("method" to method))
}

fun Analytics.logAuthLink(method: String) {
  log("auth_link", mapOf("method" to method))
}

fun Analytics.logAuthSignOut(method: String) {
  log("auth_signout", mapOf("method" to method))
}
