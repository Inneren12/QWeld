package com.qweld.app.feature.exam.vm

import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.feature.exam.model.PrewarmUiState
import com.qweld.core.common.logging.LogTag
import com.qweld.core.common.logging.Logx
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

interface ExamPrewarmCoordinator {
  val prewarmState: kotlinx.coroutines.flow.StateFlow<PrewarmUiState>

  fun startForIpMock(locale: String)

  suspend fun cancel()
}

class DefaultExamPrewarmCoordinator(
  private val scope: CoroutineScope,
  private val ioDispatcher: CoroutineDispatcher,
  private val blueprintProvider: (ExamMode, Int) -> ExamBlueprint,
  private val prewarmRunner: PrewarmController,
) : ExamPrewarmCoordinator {

  private val _prewarmState = kotlinx.coroutines.flow.MutableStateFlow(PrewarmUiState())
  override val prewarmState: kotlinx.coroutines.flow.StateFlow<PrewarmUiState> = _prewarmState

  private var prewarmJob: Job? = null

  override fun startForIpMock(locale: String) {
    val normalizedLocale = locale.lowercase(Locale.US)
    val current = _prewarmState.value
    if (current.isRunning) return
    if (current.isReady && current.locale.equals(normalizedLocale, ignoreCase = true)) return

    val blueprint = blueprintProvider(ExamMode.IP_MOCK, PracticeConfig.DEFAULT_SIZE)
    val tasks = blueprint.taskQuotas.mapNotNull { quota -> quota.taskId.takeIf { it.isNotBlank() } }.toSet()
    val expectedTotal = tasks.size.takeIf { it > 0 } ?: 1
    updatePrewarmState(
      locale = normalizedLocale,
      loaded = 0,
      total = expectedTotal,
      isRunning = true,
      isReady = false,
    )

    prewarmJob?.cancel()
    prewarmJob =
      scope.launch(ioDispatcher) {
        try {
          prewarmRunner.prewarm(normalizedLocale, tasks) { loaded, total ->
            dispatchPrewarmProgress(normalizedLocale, loaded, total, expectedTotal)
          }
          finalizePrewarm(normalizedLocale, expectedTotal)
        } catch (error: Throwable) {
          Logx.e(
            LogTag.PREWARM,
            "flow_error",
            error,
            "locale" to normalizedLocale,
          )
          finalizePrewarm(normalizedLocale, expectedTotal)
        }
      }
  }

  override suspend fun cancel() {
    prewarmJob?.cancelAndJoin()
    prewarmJob = null
  }

  private fun dispatchPrewarmProgress(
    locale: String,
    loaded: Int,
    total: Int,
    expectedTotal: Int,
  ) {
    val safeTotal = when {
      total > 0 -> total
      expectedTotal > 0 -> expectedTotal
      else -> 1
    }
    val clampedLoaded = loaded.coerceIn(0, safeTotal)
    val isReady = clampedLoaded >= safeTotal
    updatePrewarmState(
      locale = locale,
      loaded = clampedLoaded,
      total = safeTotal,
      isRunning = !isReady,
      isReady = isReady,
    )
  }

  private fun finalizePrewarm(locale: String, expectedTotal: Int) {
    val current = _prewarmState.value
    updatePrewarmState(
      locale = locale,
      loaded = max(current.loaded, current.total.takeIf { it > 0 } ?: expectedTotal),
      total = current.total.takeIf { it > 0 } ?: expectedTotal,
      isRunning = false,
      isReady = true,
    )
  }

  private fun updatePrewarmState(
    locale: String,
    loaded: Int,
    total: Int,
    isRunning: Boolean,
    isReady: Boolean,
  ) {
    scope.launch {
      val previous = _prewarmState.value
      val clampedTotal = total.coerceAtLeast(0)
      val resolvedTotal =
        when {
          clampedTotal > 0 -> clampedTotal
          previous.total > 0 -> previous.total
          else -> max(loaded, 1)
        }
      val clampedLoaded = loaded.coerceIn(0, resolvedTotal)
      val readyState = isReady || (resolvedTotal > 0 && clampedLoaded >= resolvedTotal)
      _prewarmState.value =
        previous.copy(
          locale = locale,
          loaded = clampedLoaded,
          total = resolvedTotal,
          isRunning = isRunning && !readyState,
          isReady = readyState,
        )
    }
  }
}
