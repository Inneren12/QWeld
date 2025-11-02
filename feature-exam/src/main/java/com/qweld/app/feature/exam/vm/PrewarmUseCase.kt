package com.qweld.app.feature.exam.vm

import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.core.common.logging.LogTag
import com.qweld.core.common.logging.Logx
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PrewarmUseCase(
  private val repository: AssetQuestionRepository,
  private val prewarmDisabled: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val nowProvider: () -> Long = { System.currentTimeMillis() },
  private val config: PrewarmConfig = PrewarmConfig(),
) {
  suspend fun prewarm(
    locale: String,
    tasks: Set<String>,
    onProgress: (loaded: Int, total: Int) -> Unit,
  ): RunResult {
    val normalizedLocale = locale.lowercase(Locale.US)
    val sanitizedTasks = tasks.filter { it.isNotBlank() }.toSet()
    if (!config.enabled) {
      Logx.i(
        LogTag.PREWARM,
        "skip",
        "src" to "per-task",
        "locale" to normalizedLocale,
        "reason" to "config_disabled",
      )
      return RunResult.Skipped(normalizedLocale, RunResult.SkipReason.ConfigDisabled)
    }
    val totalTasks = sanitizedTasks.size
    val disabled = prewarmDisabled.first()
    if (disabled) {
      Logx.i(
        LogTag.PREWARM,
        "skip",
        "src" to "per-task",
        "locale" to normalizedLocale,
        "reason" to "user_disabled",
      )
      return RunResult.Skipped(normalizedLocale, RunResult.SkipReason.UserDisabled)
    }
    Logx.i(
      LogTag.PREWARM,
      "start",
      "src" to "per-task",
      "locale" to normalizedLocale,
      "tasks" to totalTasks,
    )

    if (totalTasks == 0) {
      onProgress(0, 1)
      val (elapsed, result) = measureWithElapsed { repository.loadQuestions(normalizedLocale) }
      onProgress(1, 1)
      Logx.i(
        LogTag.PREWARM,
        "step",
        "src" to "bank",
        "locale" to normalizedLocale,
        "loaded" to "1/1",
        "elapsedMs" to elapsed,
      )
      val ok = result is AssetQuestionRepository.LoadResult.Success
      Logx.i(
        LogTag.PREWARM,
        "done",
        "src" to "bank",
        "locale" to normalizedLocale,
        "ok" to ok,
        "fallback" to "bank",
        "elapsedMs" to elapsed,
      )
      return RunResult.Completed(
        locale = normalizedLocale,
        requestedTasks = emptySet(),
        tasksLoaded = if (ok) 1 else 0,
        fallbackToBank = true,
        hadError = !ok,
        elapsedMs = elapsed,
        usedQuestionBank = true,
      )
    }

    val sortedTasks = sanitizedTasks.sorted()
    val startTimestamp = nowProvider()
    val loadedCount = AtomicInteger(0)
    val hadError = AtomicBoolean(false)
    val fallbackToBank = AtomicBoolean(false)
    onProgress(0, totalTasks)

    val dispatcher =
      ioDispatcher.limitedParallelism(totalTasks.coerceAtMost(config.sanitizedMaxConcurrency))
    withContext(dispatcher) {
      supervisorScope {
        for (taskId in sortedTasks) {
          launchTask(
            locale = normalizedLocale,
            taskId = taskId,
            totalTasks = totalTasks,
            startTimestamp = startTimestamp,
            loadedCount = loadedCount,
            hadError = hadError,
            fallbackToBank = fallbackToBank,
            onProgress = onProgress,
          )
        }
      }
    }

    var fallbackResult: AssetQuestionRepository.LoadResult? = null
    if (fallbackToBank.get()) {
      val (elapsed, result) = measureWithElapsed { repository.loadQuestions(normalizedLocale) }
      Logx.i(
        LogTag.PREWARM,
        "step",
        "src" to "bank",
        "locale" to normalizedLocale,
        "loaded" to "1/1",
        "elapsedMs" to elapsed,
      )
      fallbackResult = result
    }

    val overallElapsed = nowProvider() - startTimestamp
    val ok = !hadError.get() && !fallbackToBank.get()
    val finalSource = if (fallbackToBank.get()) "bank" else "per-task"
    Logx.i(
      LogTag.PREWARM,
      "done",
      "src" to finalSource,
      "locale" to normalizedLocale,
      "ok" to ok,
      "fallback" to if (fallbackToBank.get()) "bank" else "none",
      "elapsedMs" to overallElapsed,
    )
    if (fallbackResult is AssetQuestionRepository.LoadResult.Corrupt) {
      Logx.w(
        LogTag.PREWARM,
        "bank_corrupt",
        "src" to "bank",
        "locale" to normalizedLocale,
        "reason" to fallbackResult.reason,
      )
    }
    return RunResult.Completed(
      locale = normalizedLocale,
      requestedTasks = sanitizedTasks,
      tasksLoaded = loadedCount.get().coerceAtMost(totalTasks),
      fallbackToBank = fallbackToBank.get(),
      hadError = hadError.get(),
      elapsedMs = overallElapsed,
      usedQuestionBank = fallbackToBank.get(),
    )
  }

  private fun CoroutineScope.launchTask(
    locale: String,
    taskId: String,
    totalTasks: Int,
    startTimestamp: Long,
    loadedCount: AtomicInteger,
    hadError: AtomicBoolean,
    fallbackToBank: AtomicBoolean,
    onProgress: (loaded: Int, total: Int) -> Unit,
  ) = launch {
    try {
      withTimeout(config.sanitizedTimeoutMs) { repository.loadTaskIntoCache(locale, taskId) }
    } catch (missing: AssetQuestionRepository.TaskAssetMissingException) {
      fallbackToBank.set(true)
      hadError.set(true)
      Logx.w(
        LogTag.PREWARM,
        "missing",
        "src" to "per-task",
        "locale" to locale,
        "task" to missing.taskId,
        "path" to missing.path,
      )
    } catch (timeout: TimeoutCancellationException) {
      hadError.set(true)
      Logx.w(
        LogTag.PREWARM,
        "timeout",
        "src" to "per-task",
        "locale" to locale,
        "task" to taskId,
      )
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: Throwable) {
      hadError.set(true)
      Logx.e(
        LogTag.PREWARM,
        "error",
        error,
        "src" to "per-task",
        "locale" to locale,
        "task" to taskId,
      )
    } finally {
      val loaded = loadedCount.incrementAndGet().coerceAtMost(totalTasks)
      val elapsed = nowProvider() - startTimestamp
      onProgress(loaded, totalTasks)
      Logx.i(
        LogTag.PREWARM,
        "step",
        "src" to "per-task",
        "locale" to locale,
        "loaded" to "$loaded/$totalTasks",
        "elapsedMs" to elapsed,
      )
    }
  }

  private suspend fun measureWithElapsed(
    block: suspend () -> AssetQuestionRepository.LoadResult,
  ): Pair<Long, AssetQuestionRepository.LoadResult> {
    val start = nowProvider()
    val result = withContext(ioDispatcher) { block() }
    return nowProvider() - start to result
  }

  sealed class RunResult {
    data class Completed(
      val locale: String,
      val requestedTasks: Set<String>,
      val tasksLoaded: Int,
      val fallbackToBank: Boolean,
      val hadError: Boolean,
      val elapsedMs: Long,
      val usedQuestionBank: Boolean,
    ) : RunResult()

    data class Skipped(val locale: String, val reason: SkipReason) : RunResult()

    enum class SkipReason { ConfigDisabled, UserDisabled }
  }
}
