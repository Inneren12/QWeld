package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.repo.AnswersRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface ExamAutosaveController {
  fun prepare(attemptId: String)

  fun recordAnswer(entity: AnswerEntity)

  fun flush(force: Boolean = true)

  suspend fun stop()
}

class DefaultExamAutosaveController(
  private val answersRepository: AnswersRepository,
  private val scope: CoroutineScope,
  private val ioDispatcher: CoroutineDispatcher,
  private val autosaveIntervalSec: Int,
  private val autosaveFactory: (String) -> AutosaveController = { attemptId ->
    AutosaveController(
      attemptId = attemptId,
      answersRepository = answersRepository,
      scope = scope,
      ioDispatcher = ioDispatcher,
    )
  },
) : ExamAutosaveController {

  private var autosaveController: AutosaveController? = null
  private var autosaveTickerJob: Job? = null

  override fun prepare(attemptId: String) {
    stopInternal()
    val controller = autosaveFactory(attemptId)
    controller.configure(autosaveIntervalSec)
    autosaveController = controller
    startAutosaveTicker()
  }

  override fun recordAnswer(entity: AnswerEntity) {
    val controller = autosaveController
    if (controller != null) {
      controller.onAnswer(
        questionId = entity.questionId,
        choiceId = entity.selectedId,
        correctChoiceId = entity.correctId,
        isCorrect = entity.isCorrect,
        displayIndex = entity.displayIndex,
        timeSpentSec = entity.timeSpentSec,
        seenAt = entity.seenAt,
        answeredAt = entity.answeredAt,
      )
    } else {
      scope.launch(ioDispatcher) { answersRepository.upsert(listOf(entity)) }
    }
  }

  override fun flush(force: Boolean) {
    autosaveController?.flush(force)
  }

  override suspend fun stop() {
    stopInternal()
  }

  private fun stopInternal() {
    autosaveTickerJob?.cancel()
    autosaveTickerJob = null
    autosaveController?.flush(force = true)
    autosaveController = null
  }

  private fun startAutosaveTicker() {
    stopAutosaveTicker()
    val controller = autosaveController ?: return
    autosaveTickerJob =
      scope.launch {
        while (isActive) {
          delay(controller.intervalMillis)
          controller.onTick()
        }
      }
  }

  private fun stopAutosaveTicker() {
    autosaveTickerJob?.cancel()
    autosaveTickerJob = null
  }
}
