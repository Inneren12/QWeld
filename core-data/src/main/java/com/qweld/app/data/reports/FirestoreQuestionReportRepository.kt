package com.qweld.app.data.reports

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jakewharton.timber.log.Timber
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [QuestionReportRepository].
 * Writes and manages question reports in the "question_reports" collection.
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

  override suspend fun listReports(status: String?, limit: Int): List<QuestionReportWithId> {
    try {
      var query: Query = firestore.collection(COLLECTION_NAME)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(limit.toLong())

      if (status != null) {
        query = query.whereEqualTo("status", status)
      }

      val snapshot = query.get().await()
      val reports = snapshot.documents.mapNotNull { doc ->
        try {
          val report = parseReportFromDocument(doc.data ?: return@mapNotNull null)
          QuestionReportWithId(id = doc.id, report = report)
        } catch (e: Exception) {
          Timber.tag(TAG).w(e, "[report_parse_error] id=${doc.id}")
          null
        }
      }

      Timber.tag(TAG).d("[report_list] status=$status limit=$limit count=${reports.size}")
      return reports
    } catch (e: Exception) {
      Timber.tag(TAG).e(e, "[report_list_error] status=$status")
      throw e
    }
  }

  override suspend fun getReportById(reportId: String): QuestionReportWithId? {
    try {
      val doc = firestore.collection(COLLECTION_NAME)
        .document(reportId)
        .get()
        .await()

      if (!doc.exists()) {
        Timber.tag(TAG).d("[report_get] id=$reportId found=false")
        return null
      }

      val report = parseReportFromDocument(doc.data ?: return null)
      Timber.tag(TAG).d("[report_get] id=$reportId found=true")
      return QuestionReportWithId(id = doc.id, report = report)
    } catch (e: Exception) {
      Timber.tag(TAG).e(e, "[report_get_error] id=$reportId")
      throw e
    }
  }

  override suspend fun updateReportStatus(
    reportId: String,
    status: String,
    review: Map<String, Any?>?
  ) {
    try {
      val updates = mutableMapOf<String, Any?>(
        "status" to status
      )

      // Build review fields with automatic resolvedAt timestamp for terminal statuses
      if (review != null) {
        val reviewFields = review.toMutableMap()

        // Auto-set resolvedAt timestamp when moving to RESOLVED or WONT_FIX
        if (status == "RESOLVED" || status == "WONT_FIX") {
          reviewFields["resolvedAt"] = FieldValue.serverTimestamp()
        }

        // Update each review field individually to support partial updates
        reviewFields.forEach { (key, value) ->
          updates["review.$key"] = value
        }
      }

      firestore.collection(COLLECTION_NAME)
        .document(reportId)
        .update(updates)
        .await()

      Timber.tag(TAG).d("[report_update] id=$reportId status=$status")
    } catch (e: Exception) {
      Timber.tag(TAG).e(e, "[report_update_error] id=$reportId")
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

  private fun parseReportFromDocument(data: Map<String, Any?>): QuestionReport {
    return QuestionReport(
      // Core identifiers (required)
      questionId = data["questionId"] as? String ?: "",
      taskId = data["taskId"] as? String ?: "",
      blockId = data["blockId"] as? String ?: "",
      blueprintId = data["blueprintId"] as? String ?: "",

      // Localization & mode (required)
      locale = data["locale"] as? String ?: "",
      mode = data["mode"] as? String ?: "",

      // Reason (required reasonCode)
      reasonCode = data["reasonCode"] as? String ?: "",
      reasonDetail = data["reasonDetail"] as? String,
      userComment = data["userComment"] as? String,

      // Position/context within attempt
      questionIndex = (data["questionIndex"] as? Number)?.toInt(),
      totalQuestions = (data["totalQuestions"] as? Number)?.toInt(),
      selectedChoiceIds = (data["selectedChoiceIds"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() },
      correctChoiceIds = (data["correctChoiceIds"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() },
      blueprintTaskQuota = (data["blueprintTaskQuota"] as? Number)?.toInt(),

      // Versions & environment
      contentIndexSha = data["contentIndexSha"] as? String,
      blueprintVersion = data["blueprintVersion"] as? String,
      appVersionName = data["appVersionName"] as? String,
      appVersionCode = (data["appVersionCode"] as? Number)?.toInt(),
      buildType = data["buildType"] as? String,
      platform = data["platform"] as? String,
      osVersion = data["osVersion"] as? String,
      deviceModel = data["deviceModel"] as? String,

      // Session/attempt context
      sessionId = data["sessionId"] as? String,
      attemptId = data["attemptId"] as? String,
      seed = (data["seed"] as? Number)?.toLong(),
      attemptKind = data["attemptKind"] as? String,

      // Admin / workflow
      status = data["status"] as? String ?: "OPEN",
      createdAt = data["createdAt"] as? Timestamp,
      review = data["review"] as? Map<String, Any?>,
    )
  }

  companion object {
    private const val TAG = "FirestoreQuestionReportRepo"
    private const val COLLECTION_NAME = "question_reports"
  }
}
