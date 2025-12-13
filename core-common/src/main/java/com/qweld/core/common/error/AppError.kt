package com.qweld.core.common.error

/**
 * Represents different types of non-fatal errors that can occur in the application.
 * These errors are routed through the centralized error handler for logging,
 * analytics, and optional UI feedback.
 */
sealed class AppError {
    /**
     * Unexpected errors that don't fit into other categories.
     * These typically represent bugs or unforeseen issues.
     */
    data class Unexpected(
        val throwable: Throwable,
        val context: ErrorContext
    ) : AppError()

    /**
     * Network-related errors (connectivity, timeouts, API failures).
     */
    data class Network(
        val cause: Throwable,
        val context: ErrorContext
    ) : AppError()

    /**
     * Errors related to question/content reporting functionality.
     */
    data class Reporting(
        val cause: Throwable,
        val context: ErrorContext
    ) : AppError()

    /**
     * Errors related to data persistence (Room, DataStore).
     */
    data class Persistence(
        val cause: Throwable,
        val context: ErrorContext
    ) : AppError()

    /**
     * Errors related to content loading (assets, blueprints, questions).
     */
    data class ContentLoad(
        val cause: Throwable,
        val context: ErrorContext
    ) : AppError()

    /**
     * Errors related to authentication flows.
     */
    data class Auth(
        val cause: Throwable,
        val context: ErrorContext
    ) : AppError()
}

/**
 * Contextual information about where and when an error occurred.
 * This helps with debugging and analytics.
 *
 * @property screen The screen/destination where the error occurred (e.g., "ExamScreen", "ReviewScreen")
 * @property action The user action or operation that triggered the error (e.g., "load_questions", "submit_exam")
 * @property extra Additional key-value pairs for debugging (avoid PII)
 */
data class ErrorContext(
    val screen: String? = null,
    val action: String? = null,
    val extra: Map<String, String> = emptyMap()
)

/**
 * Centralized handler for non-fatal errors in the application.
 * Implementations route errors to logging, Crashlytics, and optional UI feedback.
 */
interface AppErrorHandler {
    /**
     * Handle a non-fatal error.
     * This method should:
     * - Log the error using the logging framework
     * - Report to Crashlytics (if enabled)
     * - Optionally emit UI events for user feedback
     *
     * @param error The error to handle
     */
    fun handle(error: AppError)
}

/**
 * Interface for components that provide access to the app's error handler.
 * Typically implemented by the Application class.
 */
interface AppErrorHandlerOwner {
    val errorHandler: AppErrorHandler
}

/**
 * Extension function to find the error handler from a Context.
 * Returns null if the application context doesn't implement [AppErrorHandlerOwner].
 */
fun android.content.Context.findErrorHandler(): AppErrorHandler? {
    val application = applicationContext
    return if (application is AppErrorHandlerOwner) application.errorHandler else null
}
