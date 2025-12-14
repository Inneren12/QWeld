package com.qweld.app.common.error

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lightweight error handler used for non-fatal, user-visible issues.
 * Stores a rolling buffer of recent error contexts for correlation with
 * question reports and admin diagnostics.
 */
class AppErrorHandler(
  private val maxEvents: Int = DEFAULT_MAX_EVENTS,
  private val clock: () -> Long = { System.currentTimeMillis() },
) {

  private val _events = MutableStateFlow<List<AppErrorEvent>>(emptyList())
  val events: StateFlow<List<AppErrorEvent>> = _events.asStateFlow()

  fun recordError(message: String?, throwable: Throwable? = null): AppErrorEvent {
    val event = AppErrorEvent(
      id = UUID.randomUUID().toString(),
      message = message ?: throwable?.localizedMessage,
      throwableType = throwable?.javaClass?.simpleName,
      timestamp = clock(),
    )
    _events.update { previous ->
      (previous + event).takeLast(maxEvents)
    }
    return event
  }

  fun lastErrorWithin(windowMs: Long): AppErrorEvent? {
    val now = clock()
    return events.value.lastOrNull { event -> (now - event.timestamp) <= windowMs }
  }

  companion object {
    private const val DEFAULT_MAX_EVENTS = 30
  }
}

data class AppErrorEvent(
  val id: String,
  val message: String?,
  val throwableType: String?,
  val timestamp: Long,
)
