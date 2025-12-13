package com.qweld.core.common.error

/**
 * Convenience extensions for common error handling patterns.
 */

/**
 * Handles an unexpected error with optional context.
 * Use this for errors that don't fit into other categories.
 */
fun AppErrorHandler.handleUnexpected(
    throwable: Throwable,
    screen: String? = null,
    action: String? = null,
    extra: Map<String, String> = emptyMap()
) {
    handle(
        AppError.Unexpected(
            throwable = throwable,
            context = ErrorContext(screen = screen, action = action, extra = extra)
        )
    )
}

/**
 * Handles a network error with optional context.
 */
fun AppErrorHandler.handleNetwork(
    cause: Throwable,
    screen: String? = null,
    action: String? = null,
    extra: Map<String, String> = emptyMap()
) {
    handle(
        AppError.Network(
            cause = cause,
            context = ErrorContext(screen = screen, action = action, extra = extra)
        )
    )
}

/**
 * Handles a persistence error with optional context.
 */
fun AppErrorHandler.handlePersistence(
    cause: Throwable,
    screen: String? = null,
    action: String? = null,
    extra: Map<String, String> = emptyMap()
) {
    handle(
        AppError.Persistence(
            cause = cause,
            context = ErrorContext(screen = screen, action = action, extra = extra)
        )
    )
}

/**
 * Handles a content load error with optional context.
 */
fun AppErrorHandler.handleContentLoad(
    cause: Throwable,
    screen: String? = null,
    action: String? = null,
    extra: Map<String, String> = emptyMap()
) {
    handle(
        AppError.ContentLoad(
            cause = cause,
            context = ErrorContext(screen = screen, action = action, extra = extra)
        )
    )
}

/**
 * Handles a reporting error with optional context.
 */
fun AppErrorHandler.handleReporting(
    cause: Throwable,
    screen: String? = null,
    action: String? = null,
    extra: Map<String, String> = emptyMap()
) {
    handle(
        AppError.Reporting(
            cause = cause,
            context = ErrorContext(screen = screen, action = action, extra = extra)
        )
    )
}

/**
 * Handles an auth error with optional context.
 */
fun AppErrorHandler.handleAuth(
    cause: Throwable,
    screen: String? = null,
    action: String? = null,
    extra: Map<String, String> = emptyMap()
) {
    handle(
        AppError.Auth(
            cause = cause,
            context = ErrorContext(screen = screen, action = action, extra = extra)
        )
    )
}
