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

    // Set state immediately (synchronously)
    _prewarmState.value = PrewarmUiState(
      locale = normalizedLocale,
      loaded = 0,
      total = 0,
      isRunning = true,
      isReady = false,
    )

    prewarmJob?.cancel()
    prewarmJob =
      scope.launch(ioDispatcher) {
        try {
          val blueprint = blueprintProvider(ExamMode.IP_MOCK, 125)
          val tasks = blueprint.taskQuotas.mapNotNull { quota -> quota.taskId.takeIf { it.isNotBlank() } }.toSet()

          val result = prewarmRunner.prewarm(normalizedLocale, tasks) { loaded, total ->
            val clampedLoaded = loaded.coerceIn(0, total)
            _prewarmState.value = _prewarmState.value.copy(
              loaded = clampedLoaded,
              total = total,
            )
          }

          // Finalize to ready state
          _prewarmState.value = _prewarmState.value.copy(
            isRunning = false,
            isReady = true,
          )
        } catch (error: Throwable) {
          Logx.e(
            LogTag.PREWARM,
            "flow_error",
            error,
            "locale" to normalizedLocale,
          )
          // Even on error, finalize to ready state
          _prewarmState.value = _prewarmState.value.copy(
            isRunning = false,
            isReady = true,
          )
        }
      }
  }

  override suspend fun cancel() {
    prewarmJob?.cancelAndJoin()
    prewarmJob = null
  }
}
