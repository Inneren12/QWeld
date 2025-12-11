package com.qweld.app.data.reports

import com.google.firebase.firestore.FieldValue

internal class QuestionReportPayloadBuilder(
  private val createdAtValueProvider: () -> Any = { FieldValue.serverTimestamp() },
) {
  fun build(report: QuestionReport): Map<String, Any?> {
    val data = linkedMapOf<String, Any?>()

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
    report.contentVersion.putIfNotNull(data, "contentVersion")
    report.contentIndexSha.putIfNotNull(data, "contentIndexSha")
    report.appVersionName.putIfNotNull(data, "appVersionName")
    report.appVersionCode.putIfNotNull(data, "appVersionCode")
    buildAppVersionLabel(report.appVersionName, report.appVersionCode)
      .putIfNotNull(data, "appVersion")
    report.buildType.putIfNotNull(data, "buildType")
    report.platform.putIfNotNull(data, "platform")
    report.osVersion.putIfNotNull(data, "osVersion")
    report.deviceModel.putIfNotNull(data, "deviceModel")

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
