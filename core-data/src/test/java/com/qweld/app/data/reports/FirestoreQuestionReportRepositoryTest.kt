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
import org.junit.Assert.assertTrue
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

    val result = repository.submitReport(report)
    assertTrue(result is QuestionReportSubmitResult.Queued)

    val pending = dao.listOldest()
    assertEquals(1, pending.size)
    val payload = json.decodeFromString<QueuedQuestionReportPayload>(pending.first().payload)
    assertEquals(report.questionId, payload.questionId)
    assertEquals(report.reasonCode, payload.reasonCode)
  }

  @Test
  fun submitReportReturnsSentWhenSenderSucceeds() = runTest {
    val sender = RecordingReportSender()
    val repository = buildRepository(sender)

    val result = repository.submitReport(sampleReport())

    assertTrue(result is QuestionReportSubmitResult.Sent)
    assertEquals(1, sender.sent.size)
    assertEquals(0, dao.count())
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

  @Test
  fun payloadIncludesRequiredFieldsAndVersions() {
    val builder = QuestionReportPayloadBuilder(createdAtValueProvider = { "ts" })
    val payload = builder.build(sampleReport(contentVersion = "v1"))

    assertEquals(ALLOWED_PAYLOAD_KEYS, payload.keys)
    assertEquals("Q-1", payload["questionId"])
    assertEquals("en", payload["locale"])
    assertEquals("v1", payload["contentVersion"])
    assertEquals("2024", payload["blueprintVersion"])
    assertEquals("1.0 (1)", payload["appVersion"])
    assertEquals("ts", payload["createdAt"])
    assertNoPii(payload)
  }

  @Test
  fun payloadBuilderAddsEnvironmentMetadataWhenMissingFromReport() {
    val metadata =
      ReportEnvironmentMetadata(
        appVersionName = "2.0",
        appVersionCode = 20,
        buildType = "release",
        deviceModel = "Pixel 9",
        androidVersion = "35",
        environment = "release",
      )
    val builder =
      QuestionReportPayloadBuilder(
        createdAtValueProvider = { "ts" },
        environmentMetadataProvider = FakeMetadataProvider(metadata),
      )

    val payload =
      builder.build(
        sampleReport(contentVersion = "v1").copy(
          appVersionName = null,
          appVersionCode = null,
          buildType = null,
          androidVersion = null,
          deviceModel = null,
        ),
      )

    assertEquals("2.0", payload["appVersionName"])
    assertEquals(20, payload["appVersionCode"])
    assertEquals("2.0 (20)", payload["appVersion"])
    assertEquals("Pixel 9", payload["deviceModel"])
    assertEquals("35", payload["androidVersion"])
    assertEquals("release", payload["env"])
  }

  @Test
  fun submitReportSendsSanitizedPayloadWithRequiredFields() = runTest {
    val sender = RecordingReportSender()
    val builder = QuestionReportPayloadBuilder(createdAtValueProvider = { "2024-01-01T00:00:00Z" })
    val repository = buildRepository(sender, payloadBuilder = builder)

    repository.submitReport(sampleReport(questionId = "Q-100", locale = "ru"))

    val payload = sender.sent.single()
    assertEquals(ALLOWED_PAYLOAD_KEYS, payload.keys)
    assertEquals("Q-100", payload["questionId"])
    assertEquals("ru", payload["locale"])
    assertEquals("android", payload["platform"])
    assertEquals("2024-01-01T00:00:00Z", payload["createdAt"])
    assertNoPii(payload)
  }

  @Test
  fun payloadBuilderOmitMissingMetadataAndKeepsStructure() {
    val builder = QuestionReportPayloadBuilder(createdAtValueProvider = { "ts" })
    val minimalReport = QuestionReport(
      questionId = "Q-200",
      taskId = "B-2",
      blockId = "B",
      blueprintId = "bp-2024",
      locale = "en",
      mode = "EXAM",
      reasonCode = "typo",
      userComment = null,
      appVersionName = null,
      appVersionCode = null,
      platform = "android",
    )

    val payload = builder.build(minimalReport)

    val expectedKeys = setOf(
      "questionId",
      "taskId",
      "blockId",
      "blueprintId",
      "locale",
      "mode",
      "reasonCode",
      "status",
      "platform",
      "createdAt",
    )

    assertEquals(expectedKeys, payload.keys)
    assertEquals("bp-2024", payload["blueprintId"])
    assertEquals("EXAM", payload["mode"])
    assertEquals("android", payload["platform"])
    assertNoPii(payload)
  }

  @Test
  fun payloadExcludesPiiFields() {
    val builder = QuestionReportPayloadBuilder(createdAtValueProvider = { "ts" })
    val payload = builder.build(sampleReport())

    assertEquals(ALLOWED_PAYLOAD_KEYS, payload.keys)
    assertNoPii(payload)
  }

  private fun buildRepository(
    sender: FirestoreQuestionReportRepository.ReportSender,
    clock: () -> Long = { 100L },
    environmentMetadataProvider: ReportEnvironmentMetadataProvider =
      EmptyReportEnvironmentMetadataProvider,
    payloadBuilder: QuestionReportPayloadBuilder =
      QuestionReportPayloadBuilder(environmentMetadataProvider = environmentMetadataProvider),
  ): FirestoreQuestionReportRepository {
    return FirestoreQuestionReportRepository(
      firestore = null,
      queuedReportDao = dao,
      reportSender = sender,
      json = json,
      clock = clock,
      environmentMetadataProvider = environmentMetadataProvider,
      payloadBuilder = payloadBuilder,
    )
  }

  private suspend fun enqueueReport(report: QuestionReport, attemptCount: Int = 0): Long {
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
    contentVersion: String = "content_v1",
    locale: String = "en",
  ): QuestionReport {
    return QuestionReport(
      questionId = questionId,
      taskId = "A-1",
      blockId = "A",
      blueprintId = "blueprint_ip_mock",
      locale = locale,
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
      contentVersion = contentVersion,
      appVersionName = "1.0",
      appVersionCode = 1,
      buildType = "debug",
      platform = "android",
      androidVersion = "34",
      deviceModel = "Pixel",
      sessionId = "session",
      attemptId = "attempt",
      seed = 123L,
      attemptKind = "practice",
    )
  }

  private fun assertNoPii(payload: Map<String, Any?>) {
    val disallowedKeys = setOf("email", "userName", "fullName", "phone", "userId")
    assertTrue(payload.keys.intersect(disallowedKeys).isEmpty())
  }

  companion object {
    private val ALLOWED_PAYLOAD_KEYS =
      setOf(
        "questionId",
        "taskId",
        "blockId",
        "blueprintId",
        "blueprintVersion",
        "locale",
        "mode",
        "reasonCode",
        "reasonDetail",
        "userComment",
        "questionIndex",
        "totalQuestions",
        "selectedChoiceIds",
        "correctChoiceIds",
        "blueprintTaskQuota",
        "contentVersion",
        "contentIndexSha",
        "appVersionName",
        "appVersionCode",
        "appVersion",
        "buildType",
        "env",
        "platform",
        "androidVersion",
        "deviceModel",
        "sessionId",
        "attemptId",
        "seed",
        "attemptKind",
        "status",
        "createdAt",
      )
  }
}

private class FailingReportSender : FirestoreQuestionReportRepository.ReportSender {
  override suspend fun send(questionId: String, data: Map<String, Any?>) {
    error("send failed for $questionId")
  }
}

private class FakeMetadataProvider(
  private val metadata: ReportEnvironmentMetadata,
) : ReportEnvironmentMetadataProvider {
  override fun metadata(): ReportEnvironmentMetadata = metadata
}

private class RecordingReportSender : FirestoreQuestionReportRepository.ReportSender {
  val sent = mutableListOf<Map<String, Any?>>()

  override suspend fun send(questionId: String, data: Map<String, Any?>) {
    sent.add(data + ("questionId" to questionId))
  }
}
