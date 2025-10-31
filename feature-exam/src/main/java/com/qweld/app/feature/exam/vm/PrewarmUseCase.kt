package com.qweld.app.feature.exam.vm

import com.qweld.app.feature.exam.data.AssetQuestionRepository
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class PrewarmUseCase(
  private val repository: AssetQuestionRepository,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val now: () -> Long = { System.currentTimeMillis() },
) {

  suspend fun prewarm(
    locale: String,
    tasks: Set<String>,
    onProgress: (loaded: Int, total: Int) -> Unit,
  ) {
    val resolvedLocale = locale.lowercase(Locale.US)
    val normalizedTasks = tasks.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val totalTasks = normalizedTasks.size
    val startAt = now()
    Timber.i("[prewarm_start] locale=%s tasks=%d", resolvedLocale, totalTasks)

    if (totalTasks == 0) {
      onProgress(0, 1)
      val fallbackResult = repository.loadQuestions(resolvedLocale)
      val fallbackOk = fallbackResult is AssetQuestionRepository.Result.Success
      if (fallbackOk) {
        onProgress(1, 1)
      }
      Timber.i("[prewarm_done] ok=%s fallback=%s", fallbackOk, "bank")
      return
    }

    onProgress(0, totalTasks)
    val limitedDispatcher = dispatcher.limitedParallelism(3)
    val loadedCount = AtomicInteger(0)
    val successCount = AtomicInteger(0)

    coroutineScope {
      for (task in normalizedTasks.sorted()) {
        launch(limitedDispatcher) {
          val outcome =
            runCatching {
              withTimeout(2_000L) { repository.loadTaskIntoCache(resolvedLocale, task) }
            }
          if (outcome.isSuccess) {
            successCount.incrementAndGet()
          } else {
            Timber.e(outcome.exceptionOrNull(), "[prewarm_error] locale=%s task=%s", resolvedLocale, task)
          }
          val loaded = loadedCount.incrementAndGet()
          val elapsed = now() - startAt
          Timber.i("[prewarm_step] loaded=%d/%d elapsed=%dms", loaded, totalTasks, elapsed)
          onProgress(loaded, totalTasks)
        }
      }
    }

    val successfulTasks = successCount.get()
    val fallbackUsed = successfulTasks == 0
    val fallbackOk =
      if (fallbackUsed) {
        val result = repository.loadQuestions(resolvedLocale)
        result is AssetQuestionRepository.Result.Success
      } else {
        false
      }
    val overallOk = if (fallbackUsed) fallbackOk else successfulTasks == totalTasks

    Timber.i("[prewarm_done] ok=%s fallback=%s", overallOk, if (fallbackUsed) "bank" else "none")
  }
}
