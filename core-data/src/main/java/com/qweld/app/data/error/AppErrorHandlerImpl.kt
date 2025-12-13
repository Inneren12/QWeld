package com.qweld.app.data.error

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.qweld.core.common.error.AppError
import com.qweld.core.common.error.AppErrorHandler
import com.qweld.core.common.error.ErrorContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber

/**
 * Implementation of [AppErrorHandler] that routes errors to logging and Crashlytics.
 *
 * @property crashlyticsEnabled Whether to send errors to Firebase Crashlytics
 * @property isDebug Whether the app is running in debug mode (affects logging verbosity)
 */
class AppErrorHandlerImpl(
    private val crashlyticsEnabled: Boolean,
    private val isDebug: Boolean = false,
) : AppErrorHandler {

    private val _errorEvents = MutableSharedFlow<UiErrorEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )

    /**
     * Flow of error events for UI observation.
     * UI can collect this flow to show error dialogs, snackbars, etc.
     */
    val errorEvents: SharedFlow<UiErrorEvent> = _errorEvents

    override fun handle(error: AppError) {
        // Log the error
        logError(error)

        // Send to Crashlytics if enabled
        if (crashlyticsEnabled) {
            recordToCrashlytics(error)
        }

        // Emit UI event (for future dialog/snackbar integration)
        emitUiEvent(error)
    }

    private fun logError(error: AppError) {
        val (throwable, context, category) = when (error) {
            is AppError.Unexpected -> Triple(error.throwable, error.context, "unexpected")
            is AppError.Network -> Triple(error.cause, error.context, "network")
            is AppError.Reporting -> Triple(error.cause, error.context, "reporting")
            is AppError.Persistence -> Triple(error.cause, error.context, "persistence")
            is AppError.ContentLoad -> Triple(error.cause, error.context, "content_load")
            is AppError.Auth -> Triple(error.cause, error.context, "auth")
        }

        val message = buildLogMessage(category, context)

        if (isDebug) {
            // In debug, use ERROR level and include stack trace
            Timber.tag("AppError").e(throwable, message)
        } else {
            // In release, use WARNING level with limited info
            Timber.tag("AppError").w("$message | error=${throwable.javaClass.simpleName}")
        }
    }

    private fun recordToCrashlytics(error: AppError) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()

            // Add context breadcrumbs
            val (throwable, context, category) = when (error) {
                is AppError.Unexpected -> Triple(error.throwable, error.context, "unexpected")
                is AppError.Network -> Triple(error.cause, error.context, "network")
                is AppError.Reporting -> Triple(error.cause, error.context, "reporting")
                is AppError.Persistence -> Triple(error.cause, error.context, "persistence")
                is AppError.ContentLoad -> Triple(error.cause, error.context, "content_load")
                is AppError.Auth -> Triple(error.cause, error.context, "auth")
            }

            // Set custom keys for context
            crashlytics.setCustomKey("error_category", category)
            context.screen?.let { crashlytics.setCustomKey("error_screen", it) }
            context.action?.let { crashlytics.setCustomKey("error_action", it) }
            context.extra.forEach { (key, value) ->
                crashlytics.setCustomKey("error_extra_$key", value)
            }

            // Log to Crashlytics
            crashlytics.log("Non-fatal error: $category | ${buildContextString(context)}")

            // Record the exception
            crashlytics.recordException(throwable)

            if (isDebug) {
                Timber.tag("AppError").d("Recorded to Crashlytics: $category")
            }
        } catch (e: Exception) {
            // Don't let Crashlytics failures break error handling
            Timber.tag("AppError").w(e, "Failed to record to Crashlytics")
        }
    }

    private fun emitUiEvent(error: AppError) {
        val event = when (error) {
            is AppError.Network -> UiErrorEvent.NetworkError(error.context.screen)
            is AppError.Reporting -> UiErrorEvent.GenericError("Failed to submit report", error.context.screen)
            is AppError.Persistence -> UiErrorEvent.GenericError("Failed to save data", error.context.screen)
            is AppError.ContentLoad -> UiErrorEvent.GenericError("Failed to load content", error.context.screen)
            is AppError.Auth -> UiErrorEvent.GenericError("Authentication error", error.context.screen)
            is AppError.Unexpected -> UiErrorEvent.GenericError("An unexpected error occurred", error.context.screen)
        }

        _errorEvents.tryEmit(event)
    }

    private fun buildLogMessage(category: String, context: ErrorContext): String {
        return buildString {
            append("[error] category=")
            append(category)
            context.screen?.let {
                append(" screen=")
                append(it)
            }
            context.action?.let {
                append(" action=")
                append(it)
            }
            if (context.extra.isNotEmpty()) {
                append(" extra={")
                append(context.extra.entries.joinToString(", ") { "${it.key}=${it.value}" })
                append("}")
            }
        }
    }

    private fun buildContextString(context: ErrorContext): String {
        return buildString {
            append("screen=${context.screen ?: "unknown"}")
            append(" action=${context.action ?: "unknown"}")
            if (context.extra.isNotEmpty()) {
                append(" ${context.extra.entries.joinToString(" ") { "${it.key}=${it.value}" }}")
            }
        }
    }
}

/**
 * UI-facing error events that can be observed to show user feedback.
 */
sealed class UiErrorEvent {
    data class GenericError(val message: String, val screen: String?) : UiErrorEvent()
    data class NetworkError(val screen: String?) : UiErrorEvent()
}
