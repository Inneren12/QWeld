package com.qweld.app.feature.exam.vm.fakes

import com.qweld.core.common.AppEnv

/**
 * Fake implementation of [AppEnv] for testing purposes.
 */
class FakeAppEnv(
    override val appVersionName: String? = "test-1.0.0",
    override val appVersionCode: Int? = 1,
    override val buildType: String? = "debug"
) : AppEnv
