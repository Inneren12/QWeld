package com.qweld.app.common.di

import javax.inject.Qualifier

/**
 * Qualifier for IO-optimized coroutine dispatcher.
 * Used for I/O-bound operations like database access and file operations.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Qualifier for Flow<Boolean> indicating whether prewarm is disabled.
 * Used for testing and feature flagging of prewarm functionality.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PrewarmDisabled
