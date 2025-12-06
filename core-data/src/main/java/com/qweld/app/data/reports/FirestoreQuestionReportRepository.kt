package com.qweld.app.data.reports

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jakewharton.timber.log.Timber
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [QuestionReportRepository].
 * Writes question reports to the "question_reports" collection.
 */
class FirestoreQuestionReportRepository(
  private val firestore: FirebaseFirestore,
) : QuestionReportRepository {

  override suspend fun submitReport(report: QuestionReport) {
    val data = buildReportData(report)

    try {
      val docRef = firestore.collection(COLLECTION_NAME)
        .add(data)
        .await()

      Timber.tag(TAG).d(
        "[report_submit] id=${docRef.id} question=${report.questionId} reason=${report.reasonCode}"
      )
    } catch (e: Exception) {
      Timber.tag(TAG).e(e, "[report_submit_error] question=${report.questionId}")
      throw e
    }
  }

  private fun buildReportData(report: QuestionReport): Map<String, Any?> {
    val data = mutableMapOf<String, Any?>()

    // Core identifiers
    data["questionId"] = report.questionId
    data["taskId"] = report.taskId
    data["blockId"] = report.blockId
    data["blueprintId"] = report.blueprintId

    // Localization & mode
    data["locale"] = report.locale
    data["mode"] = report.mode

    // Reason
    data["reasonCode"] = report.reasonCode
    report.reasonDetail?.let { data["reasonDetail"] = it }
    report.userComment?.let { data["userComment"] = it }

    // Position/context within attempt
    report.questionIndex?.let { data["questionIndex"] = it }
    report.totalQuestions?.let { data["totalQuestions"] = it }
    report.selectedChoiceIds?.let { data["selectedChoiceIds"] = it }
    report.correctChoiceIds?.let { data["correctChoiceIds"] = it }
    report.blueprintTaskQuota?.let { data["blueprintTaskQuota"] = it }

    // Versions & environment
    report.contentIndexSha?.let { data["contentIndexSha"] = it }
    report.blueprintVersion?.let { data["blueprintVersion"] = it }
    report.appVersionName?.let { data["appVersionName"] = it }
    report.appVersionCode?.let { data["appVersionCode"] = it }
    report.buildType?.let { data["buildType"] = it }
    report.platform?.let { data["platform"] = it }
    report.osVersion?.let { data["osVersion"] = it }
    report.deviceModel?.let { data["deviceModel"] = it }

    // Session/attempt context (no PII)
    report.sessionId?.let { data["sessionId"] = it }
    report.attemptId?.let { data["attemptId"] = it }
    report.seed?.let { data["seed"] = it }
    report.attemptKind?.let { data["attemptKind"] = it }

    // Admin / workflow
    data["status"] = report.status
    data["createdAt"] = FieldValue.serverTimestamp()
    report.review?.let { data["review"] = it }

    return data
  }

  companion object {
    private const val TAG = "FirestoreQuestionReportRepo"
    private const val COLLECTION_NAME = "question_reports"
  }
}
