package com.qweld.app.data.repo

import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ItemStats
import com.qweld.app.domain.exam.repo.UserStatsRepository
import java.time.Instant
import kotlinx.coroutines.CancellationException
import timber.log.Timber

class UserStatsRepositoryRoom(
  private val answerDao: AnswerDao,
  private val logger: (String) -> Unit = { Timber.tag(TAG).i(it) },
) : UserStatsRepository {
  override suspend fun getUserItemStats(
    userId: String,
    ids: List<String>,
  ): Outcome<Map<String, ItemStats>> {
    if (ids.isEmpty()) return Outcome.Ok(emptyMap())
    val uniqueIds = ids.toSet()
    logger("[stats_fetch] ids=${ids.size} unique=${uniqueIds.size}")
    return runCatching { answerDao.bulkCountByQuestions(uniqueIds.toList()) }
      // Не маскируем отмену корутины под I/O-ошибку
      .onFailure { e -> if (e is CancellationException) throw e }
      .map { aggregates ->
        if (aggregates.isEmpty()) {
          emptyMap()
        } else {
          aggregates.associate { aggregate ->
            val lastAnswered = aggregate.lastAnsweredAt?.let(Instant::ofEpochMilli) ?: Instant.EPOCH
            aggregate.questionId to
              ItemStats(
                questionId = aggregate.questionId,
                attempts = aggregate.attempts,
                correct = aggregate.correct,
                lastAnsweredAt = lastAnswered,
                lastAnswerCorrect = aggregate.lastIsCorrect,
              )
          }
        }
      }
      .fold(
        onSuccess = { Outcome.Ok(it) },
        onFailure = { error -> Outcome.Err.IoFailure(error) },
      )
  }

  private companion object {
    private const val TAG = "UserStatsRepository"
  }
}
