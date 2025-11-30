package com.qweld.app.feature.exam.export

import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.export.AttemptExporter
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AttemptExporterTest {
  private val attemptDao = InMemoryAttemptDao()
  private val answerDao = InMemoryAnswerDao()
  private val attemptsRepository = AttemptsRepository(attemptDao) { }
  private val answersRepository = AnswersRepository(answerDao)
  private val clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
  private val exporter =
    AttemptExporter(
      attemptsRepository = attemptsRepository,
      answersRepository = answersRepository,
      json = Json { prettyPrint = false },
      clock = clock,
      versionProvider = { "1.2.3" },
      errorLogger = { _, _ -> },
    )

  @Test
  fun `exports attempt with answers and summaries`() = runBlocking {
    val attempt =
      AttemptEntity(
        id = "attempt-1",
        mode = "IP_MOCK",
        locale = "en",
        seed = 42L,
        questionCount = 2,
        startedAt = 1_700_000_000_000,
        finishedAt = 1_700_000_360_000,
        durationSec = 3600,
        scorePct = 80.0,
        passThreshold = 70,
      )
    attemptDao.insert(attempt)
    val answers =
      listOf(
        AnswerEntity(
          attemptId = "attempt-1",
          displayIndex = 0,
          questionId = "Q-A-1_sample_question",
          selectedId = "CHOICE-1",
          correctId = "CHOICE-1",
          isCorrect = true,
          timeSpentSec = 12,
          seenAt = 1_700_000_010_000,
          answeredAt = 1_700_000_012_000,
        ),
        AnswerEntity(
          attemptId = "attempt-1",
          displayIndex = 1,
          questionId = "Q-A-2_other_question",
          selectedId = "CHOICE-3",
          correctId = "CHOICE-2",
          isCorrect = false,
          timeSpentSec = 20,
          seenAt = 1_700_000_020_000,
          answeredAt = 1_700_000_025_000,
        ),
      )
    answerDao.insertAll(answers)

    val jsonString = exporter.exportAttemptJson("attempt-1")
    val root = Json.parseToJsonElement(jsonString).jsonObject
    assertEquals("qweld.attempt.v1", root["schema"]?.jsonPrimitive?.content)

    val attemptNode = root.requireObject("attempt")
    assertEquals("attempt-1", attemptNode.requirePrimitive("id").content)
    assertEquals("IP_MOCK", attemptNode.requirePrimitive("mode").content)
    assertEquals(2, attemptNode.requirePrimitive("questionCount").int)
    assertEquals(1_700_000_000_000, attemptNode.requirePrimitive("startedAt").long)
    assertEquals(1_700_000_360_000, attemptNode.requirePrimitive("finishedAt").long)
    assertEquals(80.0, attemptNode.requirePrimitive("scorePct").double)

    val answersNode = root.requireArray("answers")
    assertEquals(2, answersNode.size)
    val firstAnswer = answersNode[0].jsonObject
    assertEquals("qweld.answer.v1", firstAnswer.requirePrimitive("schema").content)
    assertEquals("Q-A-1_sample_question", firstAnswer.requirePrimitive("questionId").content)
    assertEquals("CHOICE-1", firstAnswer.requirePrimitive("selectedId").content)
    assertEquals("CHOICE-1", firstAnswer.requirePrimitive("correctId").content)
    assertEquals("2023-11-14T22:13:30Z", firstAnswer.requirePrimitive("seenAt").content)
    assertEquals("2023-11-14T22:13:32Z", firstAnswer.requirePrimitive("answeredAt").content)
    val telemetry = firstAnswer.requireObject("telemetry")
    val shuffled = telemetry.requireArray("shuffledChoiceOrder").map { it.jsonPrimitive.content }
    assertEquals(listOf("A", "B", "C", "D"), shuffled)

    val summaries = root.requireObject("summaries")
    val byBlock = summaries.requireObject("byBlock")
    val blockA = byBlock.requireObject("A")
    assertEquals(2, blockA.requirePrimitive("total").int)
    assertEquals(1, blockA.requirePrimitive("correct").int)
    val byTask = summaries.requireObject("byTask")
    val taskA1 = byTask.requireObject("A-1")
    assertEquals(1, taskA1.requirePrimitive("total").int)
    assertEquals(1, taskA1.requirePrimitive("correct").int)

    val meta = root.requireObject("meta")
    assertEquals("1.2.3", meta.requirePrimitive("appVersion").content)
    assertEquals("2024-01-01T00:00:00Z", meta.requirePrimitive("exportedAt").content)
  }

  private class InMemoryAttemptDao : AttemptDao {
    private val attempts = mutableMapOf<String, AttemptEntity>()

    override suspend fun insert(attempt: AttemptEntity) {
      attempts[attempt.id] = attempt
    }

    override suspend fun updateFinish(
      attemptId: String,
      finishedAt: Long?,
      durationSec: Int?,
      passThreshold: Int?,
      scorePct: Double?,
    ) {
      val existing = attempts[attemptId] ?: return
      attempts[attemptId] =
        existing.copy(
          finishedAt = finishedAt,
          durationSec = durationSec,
          passThreshold = passThreshold,
          scorePct = scorePct,
        )
    }

    override suspend fun markAborted(id: String, finishedAt: Long) {
      val existing = attempts[id] ?: return
      attempts[id] =
        existing.copy(
          finishedAt = finishedAt,
          durationSec = null,
          passThreshold = null,
          scorePct = null,
        )
    }

    override suspend fun getById(id: String): AttemptEntity? = attempts[id]

    override suspend fun listRecent(limit: Int): List<AttemptEntity> =
      attempts.values.sortedByDescending { it.startedAt }.take(limit)

    override suspend fun getUnfinished(): AttemptEntity? =
      attempts.values.filter { it.finishedAt == null }.maxByOrNull { it.startedAt }

    override suspend fun getLastFinished(): AttemptEntity? =
      attempts.values.filter { it.finishedAt != null }.maxByOrNull { it.finishedAt ?: Long.MIN_VALUE }

    override suspend fun clearAll() {
      attempts.clear()
    }
  }

  private class InMemoryAnswerDao : AnswerDao {
    private val answers = mutableListOf<AnswerEntity>()

    override suspend fun insertAll(answers: List<AnswerEntity>) {
      this.answers.removeAll { existing -> answers.any { it.attemptId == existing.attemptId && it.displayIndex == existing.displayIndex } }
      this.answers += answers
    }

    override suspend fun listByAttempt(attemptId: String): List<AnswerEntity> =
      answers.filter { it.attemptId == attemptId }.sortedBy { it.displayIndex }

    override suspend fun listWrongByAttempt(attemptId: String): List<String> =
      answers.filter { it.attemptId == attemptId && !it.isCorrect }.map { it.questionId }

    override suspend fun countByQuestion(questionId: String): AnswerDao.QuestionAggregate? {
      throw UnsupportedOperationException()
    }

    override suspend fun bulkCountByQuestions(questionIds: List<String>): List<AnswerDao.QuestionAggregate> {
      throw UnsupportedOperationException()
    }

    override suspend fun clearAll() {
      answers.clear()
    }
  }
}

private fun JsonObject.requireObject(key: String): JsonObject =
  this[key]?.jsonObject ?: error("Missing key $key")

private fun JsonObject.requireArray(key: String): List<JsonElement> =
  this[key]?.jsonArray ?: error("Missing array $key")

private fun JsonObject.requirePrimitive(key: String) =
  this[key]?.jsonPrimitive ?: error("Missing primitive $key")

private val kotlinx.serialization.json.JsonPrimitive.int: Int
  get() = this.intOrNull ?: error("Expected int")

private val kotlinx.serialization.json.JsonPrimitive.long: Long
  get() = this.longOrNull ?: error("Expected long")

private val kotlinx.serialization.json.JsonPrimitive.double: Double
  get() = this.doubleOrNull ?: error("Expected double")
