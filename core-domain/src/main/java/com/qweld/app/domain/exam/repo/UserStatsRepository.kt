package com.qweld.app.domain.exam.repo

import com.qweld.app.domain.exam.ItemStats

interface UserStatsRepository {
  fun loadQuestionStats(
    userId: String,
    questionIds: Collection<String>,
  ): Map<String, ItemStats>
}
