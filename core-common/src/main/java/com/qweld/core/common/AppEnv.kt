package com.qweld.core.common

/**
 * Provides access to application environment information such as version and build type.
 * This interface allows feature modules to access app-level build configuration
 * without directly depending on BuildConfig from the application module.
 */
interface AppEnv {
    /**
     * Application version name (e.g., "1.0.0")
     */
    val appVersionName: String?

    /**
     * Application version code (numeric)
     */
    val appVersionCode: Int?

    /**
     * Build type (e.g., "debug", "release")
     */
    val buildType: String?
}
