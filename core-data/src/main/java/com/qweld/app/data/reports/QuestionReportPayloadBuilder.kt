package com.qweld.app.data.reports

import com.google.firebase.firestore.FieldValue

internal class QuestionReportPayloadBuilder(
  private val createdAtValueProvider: () -> Any = { FieldValue.serverTimestamp() },
  private val environmentMetadataProvider: ReportEnvironmentMetadataProvider =
    EmptyReportEnvironmentMetadataProvider,
) {
  fun build(report: QuestionReport): Map<String, Any?> {
    val data = linkedMapOf<String, Any?>()
    val environmentMetadata = environmentMetadataProvider.metadata()

    // Core identifiers
    data["questionId"] = report.questionId
    data["taskId"] = report.taskId
    data["blockId"] = report.blockId
    data["blueprintId"] = report.blueprintId
    report.blueprintVersion.putIfNotNull(data, "blueprintVersion")

    // Localization & mode
    data["locale"] = report.locale
    data["mode"] = report.mode

    // Reason
    data["reasonCode"] = report.reasonCode
    report.reasonDetail.putIfNotNull(data, "reasonDetail")
    report.userComment.putIfNotNull(data, "userComment")

    // Position/context within attempt
    report.questionIndex.putIfNotNull(data, "questionIndex")
    report.totalQuestions.putIfNotNull(data, "totalQuestions")
    report.selectedChoiceIds.putIfNotNull(data, "selectedChoiceIds")
    report.correctChoiceIds.putIfNotNull(data, "correctChoiceIds")
    report.blueprintTaskQuota.putIfNotNull(data, "blueprintTaskQuota")

    // Versions & environment
    val appVersionName = report.appVersionName ?: environmentMetadata.appVersionName
    val appVersionCode = report.appVersionCode ?: environmentMetadata.appVersionCode
    val deviceModel = report.deviceModel ?: environmentMetadata.deviceModel
    val androidVersion = report.androidVersion ?: environmentMetadata.androidVersion
    val buildType = report.buildType ?: environmentMetadata.buildType
    val environment = environmentMetadata.environment ?: buildType

    report.contentVersion.putIfNotNull(data, "contentVersion")
    report.contentIndexSha.putIfNotNull(data, "contentIndexSha")
    appVersionName.putIfNotNull(data, "appVersionName")
    appVersionCode.putIfNotNull(data, "appVersionCode")
    buildAppVersionLabel(appVersionName, appVersionCode)
      .putIfNotNull(data, "appVersion")
    buildType.putIfNotNull(data, "buildType")
    environment.putIfNotNull(data, "env")
    report.platform.putIfNotNull(data, "platform")
    androidVersion.putIfNotNull(data, "androidVersion")
    deviceModel.putIfNotNull(data, "deviceModel")

    // Session/attempt context (no PII)
    report.sessionId.putIfNotNull(data, "sessionId")
    report.attemptId.putIfNotNull(data, "attemptId")
    report.seed.putIfNotNull(data, "seed")
    report.attemptKind.putIfNotNull(data, "attemptKind")

    // Admin / workflow
    data["status"] = report.status
    data["createdAt"] = createdAtValueProvider()
    report.review.putIfNotNull(data, "review")

    return data
  }

  private fun buildAppVersionLabel(appVersionName: String?, appVersionCode: Int?): String? {
    return when {
      appVersionName.isNullOrBlank() && appVersionCode == null -> null
      appVersionName.isNullOrBlank() -> appVersionCode?.toString()
      appVersionCode == null -> appVersionName
      else -> "$appVersionName ($appVersionCode)"
    }
  }
}

private fun Any?.putIfNotNull(destination: MutableMap<String, Any?>, key: String) {
  if (this != null) {
    destination[key] = this
  }
}
