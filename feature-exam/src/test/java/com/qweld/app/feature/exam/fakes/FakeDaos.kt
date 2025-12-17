package com.qweld.app.feature.exam.fakes

import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * Shared in-memory fake implementation of AttemptDao for testing.
 * Provides basic CRUD operations without database dependencies.
 */
class FakeAttemptDao : AttemptDao {
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
    attempts.values.filter { it.finishedAt != null }.maxByOrNull { it.finishedAt ?: 0L }

  override suspend fun clearAll() {
    attempts.clear()
  }

    override suspend fun getAttemptStats(): AttemptDao.AttemptStatsRow {
        val all = attempts.values
        val total = all.size
        val finished = all.count { it.finishedAt != null }
        val inProgress = all.count { it.finishedAt == null }
        val failed = all.count { entity ->
            val threshold = entity.passThreshold
            val score = entity.scorePct
            threshold != null && score != null && score < threshold.toDouble()
        }
        val lastFinishedAt = all.mapNotNull { it.finishedAt }.maxOrNull()

        return AttemptDao.AttemptStatsRow(
            totalCount = total,
            finishedCount = finished,
            inProgressCount = inProgress,
            failedCount = failed,
            lastFinishedAt = lastFinishedAt,
            )
    }

    // Для unit-тестов достаточно заглушки: user_version тут не важен
    override suspend fun getUserVersion(query: SupportSQLiteQuery): Int = 0

    override suspend fun updateRemainingTime(
        attemptId: String,
        remainingTimeMs: Long?,
    ) {
        // Для юнит-тестов нам не критично хранить оставшееся время,
        // поэтому можно сделать no-op. Если нужно — потом можно
        // доработать и обновлять AttemptEntity в map.
    }

  /**
   * Test-only method to get all stored attempts.
   */
  fun getAll(): List<AttemptEntity> = attempts.values.toList()
}

/**
 * Shared in-memory fake implementation of AnswerDao for testing.
 * Provides basic CRUD operations without database dependencies.
 */
class FakeAnswerDao : AnswerDao {
  private val answers = mutableListOf<AnswerEntity>()

  override suspend fun insertAll(answers: List<AnswerEntity>) {
    this.answers.removeAll { existing ->
      answers.any {
        it.attemptId == existing.attemptId && it.displayIndex == existing.displayIndex
      }
    }
    this.answers += answers
  }

  override suspend fun listByAttempt(attemptId: String): List<AnswerEntity> =
    answers.filter { it.attemptId == attemptId }.sortedBy { it.displayIndex }

  override suspend fun listWrongByAttempt(attemptId: String): List<String> =
    answers
      .filter { it.attemptId == attemptId && !it.isCorrect }
      .sortedBy { it.displayIndex }
      .map { it.questionId }

  override suspend fun countByQuestion(questionId: String): AnswerDao.QuestionAggregate? {
    val relevant = answers.filter { it.questionId == questionId }
    if (relevant.isEmpty()) return null

    val lastEntry = relevant.maxByOrNull { it.answeredAt }
    return AnswerDao.QuestionAggregate(
      questionId = questionId,
      attempts = relevant.size,
      correct = relevant.count { it.isCorrect },
      lastAnsweredAt = relevant.maxOfOrNull { it.answeredAt },
      lastIsCorrect = lastEntry?.isCorrect,
    )
  }

  override suspend fun bulkCountByQuestions(questionIds: List<String>): List<AnswerDao.QuestionAggregate> {
    return questionIds.mapNotNull { countByQuestion(it) }
  }

    override suspend fun countAll(): Int = answers.size

  override suspend fun clearAll() {
    answers.clear()
  }

  /**
   * Test-only method to get all stored answers.
   */
  fun getAll(): List<AnswerEntity> = answers.toList()
}
