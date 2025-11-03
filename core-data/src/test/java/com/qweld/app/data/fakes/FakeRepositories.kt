package com.qweld.app.data.fakes

import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ItemStats
import com.qweld.app.domain.exam.Question
import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository

class FakeQuestionRepository(
  private val backing: List<Question>,
) : QuestionRepository {
  override fun listByTaskAndLocale(
    taskId: String,
    locale: String,
    allowFallbackToEnglish: Boolean,
  ): Outcome<List<Question>> {
    val primary =
      backing.filter { it.taskId == taskId && it.locale.equals(locale, ignoreCase = true) }
    if (primary.isNotEmpty() || !allowFallbackToEnglish || locale.equals("EN", ignoreCase = true)) {
      return Outcome.Ok(primary.sortedBy { it.id })
    }
    val fallback =
      backing.filter { it.taskId == taskId && it.locale.equals("EN", ignoreCase = true) }
    return Outcome.Ok(fallback.sortedBy { it.id })
  }
}

class FakeUserStatsRepository(
  private val stats: Map<String, ItemStats> = emptyMap(),
) : UserStatsRepository {
  override suspend fun getUserItemStats(
    userId: String,
    ids: List<String>,
  ): Outcome<Map<String, ItemStats>> {
    return Outcome.Ok(ids.mapNotNull { id -> stats[id]?.let { id to it } }.toMap())
  }
}
