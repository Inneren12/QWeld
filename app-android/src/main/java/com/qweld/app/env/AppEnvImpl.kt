package com.qweld.app.env

import com.qweld.app.BuildConfig
import com.qweld.core.common.AppEnv

/**
 * Production implementation of [AppEnv] that uses the application module's BuildConfig.
 */
class AppEnvImpl : AppEnv {
    override val appVersionName: String? = BuildConfig.VERSION_NAME
    override val appVersionCode: Int? = BuildConfig.VERSION_CODE
    override val buildType: String? = BuildConfig.BUILD_TYPE
}
