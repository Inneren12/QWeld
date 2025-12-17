package com.qweld.app.feature.exam.vm

import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.core.common.logging.LogTag
import com.qweld.core.common.logging.Logx
import java.util.Locale

interface PrewarmController {
  suspend fun prewarm(
    locale: String,
    tasks: Set<String>,
    onProgress: (loaded: Int, total: Int) -> Unit,
  ): PrewarmUseCase.RunResult
}

class DefaultPrewarmController(
  private val repository: AssetQuestionRepository,
  private val prewarmUseCase: PrewarmUseCase,
) : PrewarmController {
  override suspend fun prewarm(
    locale: String,
    tasks: Set<String>,
    onProgress: (loaded: Int, total: Int) -> Unit,
  ): PrewarmUseCase.RunResult {
    val normalizedLocale = locale.lowercase(Locale.US)
    val cacheBefore = repository.cachedTasks(normalizedLocale)
    val totalCacheBefore = repository.cacheEntryCount()
    val result = prewarmUseCase.prewarm(locale, tasks, onProgress)
    when (result) {
      is PrewarmUseCase.RunResult.Completed -> {
        val cacheAfter = repository.cachedTasks(normalizedLocale)
        val totalCacheAfter = repository.cacheEntryCount()
        val requested = result.requestedTasks
        val cachedBefore = cacheBefore.intersect(requested)
        val cachedAfter = cacheAfter.intersect(requested)
        val newlyCached = (cachedAfter.size - cachedBefore.size).coerceAtLeast(0)
        val mode = if (cachedBefore.isEmpty()) "cold" else "warm"
        Logx.i(
          LogTag.PREWARM,
          "metrics",
          "locale" to normalizedLocale,
          "mode" to mode,
          "t_ms" to result.elapsedMs,
          "cache_entries" to totalCacheAfter,
          "cache_entries_before" to totalCacheBefore,
          "cache_locale" to cacheAfter.size,
          "cache_locale_before" to cacheBefore.size,
          "tasks_requested" to requested.size,
          "tasks_cached" to cachedAfter.size,
          "tasks_new" to newlyCached,
          "source" to if (result.usedQuestionBank) "bank" else "per-task",
          "fallback" to if (result.fallbackToBank) "bank" else "none",
          "errors" to result.hadError,
        )
      }
      is PrewarmUseCase.RunResult.Skipped -> {
        Logx.i(
          LogTag.PREWARM,
          "metrics_skip",
          "locale" to normalizedLocale,
          "reason" to result.reason.name.lowercase(Locale.US),
          "cache_entries" to totalCacheBefore,
        )
      }
    }
    return result
  }
}
