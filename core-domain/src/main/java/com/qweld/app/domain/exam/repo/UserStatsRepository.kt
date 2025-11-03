package com.qweld.app.domain.exam.repo

import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ItemStats

interface UserStatsRepository {
    suspend fun getUserItemStats(
    userId: String,
    ids: List<String>,
  ): Outcome<Map<String, ItemStats>>
}
