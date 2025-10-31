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
import timber.log.Timber

class PrewarmUseCase(
  private val repository: AssetQuestionRepository,
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
    Timber.i("[prewarm_start] locale=%s tasks=%d", normalizedLocale, totalTasks)

    if (totalTasks == 0) {
      onProgress(0, 1)
      val (elapsed, result) = measureWithElapsed { repository.loadQuestions(normalizedLocale) }
      onProgress(1, 1)
      Timber.i("[prewarm_step] loaded=1/1 elapsed=%dms", elapsed)
      val ok = result is AssetQuestionRepository.Result.Success
      Timber.i("[prewarm_done] ok=%s fallback=bank", ok)
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

    var fallbackResult: AssetQuestionRepository.Result? = null
    if (fallbackToBank.get()) {
      val (elapsed, result) = measureWithElapsed { repository.loadQuestions(normalizedLocale) }
      Timber.i("[prewarm_step] loaded=1/1 elapsed=%dms", elapsed)
      fallbackResult = result
    }

    val overallElapsed = nowProvider() - startTimestamp
    val ok = !hadError.get() && !fallbackToBank.get()
    Timber.i(
      "[prewarm_done] ok=%s fallback=%s elapsed=%dms",
      ok,
      if (fallbackToBank.get()) "bank" else "none",
      overallElapsed,
    )
    if (fallbackResult is AssetQuestionRepository.Result.Error) {
      Timber.w(fallbackResult.cause, "[prewarm_bank_error] locale=%s", normalizedLocale)
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
        "[prewarm_missing] task=%s locale=%s path=%s",
        missing.taskId,
        locale,
        missing.path,
      )
    } catch (timeout: TimeoutCancellationException) {
      hadError.set(true)
      Timber.w("[prewarm_timeout] task=%s locale=%s", taskId, locale)
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: Throwable) {
      hadError.set(true)
      Timber.e(error, "[prewarm_error] task=%s locale=%s", taskId, locale)
    } finally {
      val loaded = loadedCount.incrementAndGet().coerceAtMost(totalTasks)
      val elapsed = nowProvider() - startTimestamp
      onProgress(loaded, totalTasks)
      Timber.i("[prewarm_step] loaded=%d/%d elapsed=%dms", loaded, totalTasks, elapsed)
    }
  }

  private suspend fun measureWithElapsed(
    block: suspend () -> AssetQuestionRepository.Result,
  ): Pair<Long, AssetQuestionRepository.Result> {
    val start = nowProvider()
    val result = withContext(ioDispatcher) { block() }
    return nowProvider() - start to result
  }

  companion object {
    private const val TASK_TIMEOUT_MS = 2_000L
    private const val MAX_PARALLELISM = 3
  }
}
