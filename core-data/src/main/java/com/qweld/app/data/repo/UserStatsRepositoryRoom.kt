package com.qweld.app.data.repo

import android.util.Log
import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.domain.exam.ItemStats
import com.qweld.app.domain.exam.repo.UserStatsRepository
import kotlinx.coroutines.CancellationException
import java.time.Instant

class UserStatsRepositoryRoom(
  private val answerDao: AnswerDao,
  private val logger: (String) -> Unit = { Log.i(TAG, it) },
) : UserStatsRepository {
  override suspend fun getUserItemStats(userId: String, ids: List<String>): Map<String, ItemStats> {
    if (ids.isEmpty()) return emptyMap()
    val uniqueIds = ids.toSet()
    logger("[stats_fetch] ids=${ids.size} unique=${uniqueIds.size}")
    val aggregates =
      runCatching { answerDao.bulkCountByQuestions(uniqueIds.toList()) }
        .onFailure { error ->
          if (error is CancellationException) throw error // важно: не съедаем отмену
          logger("[stats_fetch] failed=${error.javaClass.simpleName} msg=${error.message}")
        }
        .getOrElse { return emptyMap() }
    if (aggregates.isEmpty()) return emptyMap()
    return aggregates.associate { aggregate ->
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

  private companion object {
    private const val TAG = "UserStatsRepository"
  }
}
