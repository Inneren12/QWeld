package com.qweld.app.data.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qweld.app.data.db.QWeldDb
import com.qweld.app.data.db.dao.QueuedQuestionReportDao
import com.qweld.app.data.db.entities.QueuedQuestionReportEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreQuestionReportRepositoryTest {
  private lateinit var context: Context
  private lateinit var db: QWeldDb
  private lateinit var dao: QueuedQuestionReportDao
  private val json = Json { encodeDefaults = true }

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = QWeldDb.inMemory(context)
    dao = db.queuedQuestionReportDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun queuesReportWhenSubmitFails() = runTest {
    val sender = FailingReportSender()
    val repository = buildRepository(sender)
    val report = sampleReport()

    runCatching { repository.submitReport(report) }

    val pending = dao.listOldest()
    assertEquals(1, pending.size)
    val payload = json.decodeFromString<QueuedQuestionReportPayload>(pending.first().payload)
    assertEquals(report.questionId, payload.questionId)
    assertEquals(report.reasonCode, payload.reasonCode)
  }

  @Test
  fun retrySendsQueuedReportsAndClears() = runTest {
    val sender = RecordingReportSender()
    val repository = buildRepository(sender)
    val report = sampleReport(reason = "typo")
    enqueueReport(report)

    val result = repository.retryQueuedReports(maxAttempts = 3, batchSize = 5)

    assertEquals(1, sender.sent.size)
    assertEquals(0, dao.count())
    assertEquals(1, result.sent)
    assertEquals(0, result.dropped)
  }

  @Test
  fun retryFailureIncrementsAttemptCount() = runTest {
    val sender = FailingReportSender()
    val repository = buildRepository(sender, clock = { 200L })
    val report = sampleReport(questionId = "Q-2")
    val entityId = enqueueReport(report, attemptCount = 0)

    val result = repository.retryQueuedReports(maxAttempts = 2, batchSize = 5)

    val pending = dao.getById(entityId)
    assertNotNull(pending)
    assertEquals(1, pending?.attemptCount)
    assertEquals(200L, pending?.lastAttemptAt)
    assertEquals(0, result.sent)
    assertEquals(0, result.dropped)
  }

  @Test
  fun retryDropsWhenAttemptLimitReached() = runTest {
    val sender = FailingReportSender()
    val repository = buildRepository(sender)
    val report = sampleReport(questionId = "Q-3")
    enqueueReport(report, attemptCount = 1)

    val result = repository.retryQueuedReports(maxAttempts = 2, batchSize = 2)

    assertEquals(0, dao.count())
    assertEquals(0, result.sent)
    assertEquals(1, result.dropped)
  }

  private fun buildRepository(
    sender: FirestoreQuestionReportRepository.ReportSender,
    clock: () -> Long = { 100L },
  ): FirestoreQuestionReportRepository {
    return FirestoreQuestionReportRepository(
      firestore = null,
      queuedReportDao = dao,
      reportSender = sender,
      json = json,
      clock = clock,
    )
  }

  private fun enqueueReport(report: QuestionReport, attemptCount: Int = 0): Long {
    val payload = json.encodeToString(QueuedQuestionReportPayload.fromReport(report))
    return dao.insert(
      QueuedQuestionReportEntity(
        questionId = report.questionId,
        locale = report.locale,
        reasonCode = report.reasonCode,
        payload = payload,
        attemptCount = attemptCount,
        lastAttemptAt = null,
        createdAt = 50L,
      ),
    )
  }

  private fun sampleReport(
    questionId: String = "Q-1",
    reason: String = "wrong_answer",
  ): QuestionReport {
    return QuestionReport(
      questionId = questionId,
      taskId = "A-1",
      blockId = "A",
      blueprintId = "blueprint_ip_mock",
      locale = "en",
      mode = "PRACTICE",
      reasonCode = reason,
      reasonDetail = "detail",
      userComment = "comment",
      questionIndex = 1,
      totalQuestions = 10,
      selectedChoiceIds = listOf(0),
      correctChoiceIds = listOf(1),
      blueprintTaskQuota = 8,
      contentIndexSha = "sha",
      blueprintVersion = "2024",
      appVersionName = "1.0",
      appVersionCode = 1,
      buildType = "debug",
      platform = "android",
      osVersion = "34",
      deviceModel = "Pixel",
      sessionId = "session",
      attemptId = "attempt",
      seed = 123L,
      attemptKind = "practice",
    )
  }
}

private class FailingReportSender : FirestoreQuestionReportRepository.ReportSender {
  override suspend fun send(questionId: String, data: Map<String, Any?>) {
    error("send failed for $questionId")
  }
}

private class RecordingReportSender : FirestoreQuestionReportRepository.ReportSender {
  val sent = mutableListOf<Map<String, Any?>>()

  override suspend fun send(questionId: String, data: Map<String, Any?>) {
    sent.add(data + ("questionId" to questionId))
  }
}
