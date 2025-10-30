package com.qweld.app.domain.exam.repo

import com.qweld.app.domain.exam.ItemStats

interface UserStatsRepository {
  fun getUserItemStats(
    userId: String,
    ids: List<String>,
  ): Map<String, ItemStats>
}
