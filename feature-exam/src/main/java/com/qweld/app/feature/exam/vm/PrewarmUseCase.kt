package com.qweld.app.feature.exam.vm

import com.qweld.app.feature.exam.data.AssetQuestionRepository
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
import timber.log.Timber

class PrewarmUseCase(
  private val repository: AssetQuestionRepository,
  private val prewarmDisabled: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
  suspend fun prewarm(
    locale: String,
    tasks: Set<String>,
    onProgress: (loaded: Int, total: Int) -> Unit,
  ) {
    val normalizedLocale = locale.lowercase(Locale.US)
    val sanitizedTasks = tasks.filter { it.isNotBlank() }.toSet()
    val totalTasks = sanitizedTasks.size
    val disabled = prewarmDisabled.first()
    if (disabled) {
      Timber.i("[prewarm_skip] src=per-task locale=%s reason=disabled", normalizedLocale)
      return
    }
    Timber.i("[prewarm_start] src=per-task locale=%s tasks=%d", normalizedLocale, totalTasks)

    if (totalTasks == 0) {
      onProgress(0, 1)
      val (elapsed, result) = measureWithElapsed { repository.loadQuestions(normalizedLocale) }
      onProgress(1, 1)
      Timber.i(
        "[prewarm_step] src=bank locale=%s loaded=1/1 elapsedMs=%d",
        normalizedLocale,
        elapsed,
      )
      val ok = result is AssetQuestionRepository.LoadResult.Success
      Timber.i(
        "[prewarm_done] src=bank locale=%s ok=%s fallback=bank",
        normalizedLocale,
        ok,
      )
      return
    }

    val sortedTasks = sanitizedTasks.sorted()
    val startTimestamp = nowProvider()
    val loadedCount = AtomicInteger(0)
    val hadError = AtomicBoolean(false)
    val fallbackToBank = AtomicBoolean(false)
    onProgress(0, totalTasks)

    val dispatcher = ioDispatcher.limitedParallelism(totalTasks.coerceAtMost(MAX_PARALLELISM))
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
      Timber.i(
        "[prewarm_step] src=bank locale=%s loaded=1/1 elapsedMs=%d",
        normalizedLocale,
        elapsed,
      )
      fallbackResult = result
    }

    val overallElapsed = nowProvider() - startTimestamp
    val ok = !hadError.get() && !fallbackToBank.get()
    val finalSource = if (fallbackToBank.get()) "bank" else "per-task"
    Timber.i(
      "[prewarm_done] src=%s locale=%s ok=%s fallback=%s elapsedMs=%d",
      finalSource,
      normalizedLocale,
      ok,
      if (fallbackToBank.get()) "bank" else "none",
      overallElapsed,
    )
    if (fallbackResult is AssetQuestionRepository.LoadResult.Corrupt) {
      Timber.w(
        "[prewarm_bank_corrupt] src=bank locale=%s reason=%s",
        normalizedLocale,
        fallbackResult.reason,
      )
    }
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
      withTimeout(TASK_TIMEOUT_MS) { repository.loadTaskIntoCache(locale, taskId) }
    } catch (missing: AssetQuestionRepository.TaskAssetMissingException) {
      fallbackToBank.set(true)
      hadError.set(true)
      Timber.w(
        "[prewarm_missing] src=per-task locale=%s task=%s path=%s",
        locale,
        missing.taskId,
        missing.path,
      )
    } catch (timeout: TimeoutCancellationException) {
      hadError.set(true)
      Timber.w("[prewarm_timeout] src=per-task locale=%s task=%s", locale, taskId)
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: Throwable) {
      hadError.set(true)
      Timber.e(error, "[prewarm_error] src=per-task locale=%s task=%s", locale, taskId)
    } finally {
      val loaded = loadedCount.incrementAndGet().coerceAtMost(totalTasks)
      val elapsed = nowProvider() - startTimestamp
      onProgress(loaded, totalTasks)
      Timber.i(
        "[prewarm_step] src=per-task locale=%s loaded=%d/%d elapsedMs=%d",
        locale,
        loaded,
        totalTasks,
        elapsed,
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

  companion object {
    private const val TASK_TIMEOUT_MS = 2_000L
    private const val MAX_PARALLELISM = 3
  }
}
