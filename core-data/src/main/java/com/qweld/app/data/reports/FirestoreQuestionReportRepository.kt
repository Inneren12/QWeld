package com.qweld.app.data.reports

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.qweld.app.data.db.dao.QueuedQuestionReportDao
import com.qweld.app.data.db.entities.QueuedQuestionReportEntity
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Firestore implementation of [QuestionReportRepository].
 * Writes and manages question reports in the "question_reports" collection.
 */
class FirestoreQuestionReportRepository(
  private val firestore: FirebaseFirestore?,
  private val queuedReportDao: QueuedQuestionReportDao,
  private val reportSender: ReportSender = firestore?.let { FirestoreReportSender(it) }
    ?: throw IllegalArgumentException("Firestore instance is required when no ReportSender is provided"),
  private val json: Json = Json { encodeDefaults = true },
  private val clock: () -> Long = { System.currentTimeMillis() },
  private val payloadBuilder: QuestionReportPayloadBuilder = QuestionReportPayloadBuilder(),
) : QuestionReportRepository {

  override suspend fun submitReport(report: QuestionReport) {
    val data = payloadBuilder.build(report)

    try {
      reportSender.send(report.questionId, data)
    } catch (e: Exception) {
      Timber.tag(TAG).w(e, "[report_submit_error_queue] question=${report.questionId}")
      queueReport(report)
      throw e
    }
  }

  override suspend fun retryQueuedReports(
    maxAttempts: Int,
    batchSize: Int,
  ): QuestionReportRetryResult {
    var sent = 0
    var dropped = 0

    while (true) {
      val queued = queuedReportDao.listOldest(limit = batchSize)
      if (queued.isEmpty()) break

      queued.forEach { entity ->
        val report = decodeQueuedReport(entity)
        if (report == null) {
          queuedReportDao.deleteById(entity.id)
          dropped++
          return@forEach
        }

        try {
          reportSender.send(report.questionId, payloadBuilder.build(report))
          queuedReportDao.deleteById(entity.id)
          sent++
        } catch (e: Exception) {
          val attempts = entity.attemptCount + 1
          val now = clock()
          if (attempts >= maxAttempts) {
            Timber.tag(TAG).w(
              e,
              "[report_retry_drop] question=%s attempts=%d",
              report.questionId,
              attempts,
            )
            queuedReportDao.deleteById(entity.id)
            dropped++
          } else {
            queuedReportDao.incrementAttempt(entity.id, now)
            Timber.tag(TAG).w(
              e,
              "[report_retry_defer] question=%s attempts=%d",
              report.questionId,
              attempts,
            )
          }
        }
      }

      if (queued.size < batchSize) break
    }

    return QuestionReportRetryResult(sent = sent, dropped = dropped)
  }

  override suspend fun listReports(status: String?, limit: Int): List<QuestionReportWithId> {
    try {
      var query: Query = firestoreOrThrow().collection(COLLECTION_NAME)
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
      val doc = firestoreOrThrow().collection(COLLECTION_NAME)
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
    resolutionCode: String?,
    resolutionComment: String?,
  ) {
    try {
      val updates = mutableMapOf<String, Any?>(
        "status" to status
      )

      if (resolutionCode != null) {
        updates["review.resolutionCode"] = resolutionCode
      }

      if (resolutionComment != null) {
        updates["review.resolutionComment"] = resolutionComment
      }

      if (status == "RESOLVED" || status == "WONT_FIX") {
        updates["review.resolvedAt"] = FieldValue.serverTimestamp()
      }

      firestoreOrThrow().collection(COLLECTION_NAME)
        .document(reportId)
        .update(updates)
        .await()

      Timber.tag(TAG).d("[report_update] id=$reportId status=$status")
    } catch (e: Exception) {
      Timber.tag(TAG).e(e, "[report_update_error] id=$reportId")
      throw e
    }
  }

  private suspend fun queueReport(report: QuestionReport) {
    val payload = QueuedQuestionReportPayload.fromReport(report)
    val entity =
      QueuedQuestionReportEntity(
        questionId = report.questionId,
        locale = report.locale,
        reasonCode = report.reasonCode,
        payload = json.encodeToString(payload),
        attemptCount = 0,
        lastAttemptAt = null,
        createdAt = clock(),
      )
    queuedReportDao.insert(entity)
    Timber.tag(TAG).i(
      "[report_queued] question=%s reason=%s",
      report.questionId,
      report.reasonCode,
    )
  }

  private fun decodeQueuedReport(entity: QueuedQuestionReportEntity): QuestionReport? {
    return runCatching {
      json.decodeFromString<QueuedQuestionReportPayload>(entity.payload)
        .toQuestionReport()
    }.onFailure { error ->
      Timber.tag(TAG).w(error, "[report_queue_decode_error] id=${entity.id}")
    }.getOrNull()
  }

  private fun firestoreOrThrow(): FirebaseFirestore {
    return firestore
      ?: throw IllegalStateException("Firestore instance is required for read/update operations")
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
      contentVersion = data["contentVersion"] as? String,
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

  interface ReportSender {
    suspend fun send(questionId: String, data: Map<String, Any?>)
  }

  private class FirestoreReportSender(private val firestore: FirebaseFirestore) : ReportSender {
    override suspend fun send(questionId: String, data: Map<String, Any?>) {
      val docRef = firestore.collection(COLLECTION_NAME)
        .add(data)
        .await()

      Timber.tag(TAG).d("[report_submit] id=${docRef.id} question=$questionId")
    }
  }

  companion object {
    private const val TAG = "FirestoreQuestionReportRepo"
    private const val COLLECTION_NAME = "question_reports"
  }
}
