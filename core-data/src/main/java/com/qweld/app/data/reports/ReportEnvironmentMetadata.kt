package com.qweld.app.data.reports

import android.os.Build
import com.qweld.core.common.AppEnv

/**
 * Environment metadata attached to question reports to aid moderation triage.
 */
data class ReportEnvironmentMetadata(
  val appVersionName: String? = null,
  val appVersionCode: Int? = null,
  val buildType: String? = null,
  val deviceModel: String? = null,
  val androidVersion: String? = null,
  val environment: String? = null,
)

fun interface ReportEnvironmentMetadataProvider {
  fun metadata(): ReportEnvironmentMetadata
}

class DefaultReportEnvironmentMetadataProvider(
  private val appEnv: AppEnv? = null,
  private val deviceInfoProvider: DeviceInfoProvider = DeviceInfoProvider(),
) : ReportEnvironmentMetadataProvider {
  override fun metadata(): ReportEnvironmentMetadata {
    val deviceInfo = deviceInfoProvider.deviceInfo()
    return ReportEnvironmentMetadata(
      appVersionName = appEnv?.appVersionName,
      appVersionCode = appEnv?.appVersionCode,
      buildType = appEnv?.buildType,
      deviceModel = deviceInfo.deviceModel,
      androidVersion = deviceInfo.androidVersion,
      environment = appEnv?.buildType,
    )
  }
}

class DeviceInfoProvider(
  private val modelProvider: () -> String? = { Build.MODEL },
  private val androidVersionProvider: () -> String? = { Build.VERSION.RELEASE },
) {
  data class DeviceInfo(
    val deviceModel: String?,
    val androidVersion: String?,
  )

  fun deviceInfo(): DeviceInfo {
    return DeviceInfo(
      deviceModel = runCatching { modelProvider() }.getOrNull(),
      androidVersion = runCatching { androidVersionProvider() }.getOrNull(),
    )
  }
}

object EmptyReportEnvironmentMetadataProvider : ReportEnvironmentMetadataProvider {
  override fun metadata(): ReportEnvironmentMetadata = ReportEnvironmentMetadata()
}
