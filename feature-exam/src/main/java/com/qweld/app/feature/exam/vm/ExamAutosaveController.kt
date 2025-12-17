package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.repo.AnswersRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

interface ExamAutosaveController {
  fun prepare(attemptId: String)

  fun recordAnswer(entity: AnswerEntity)

  fun flush(force: Boolean = true)

  suspend fun stop()
}

class DefaultExamAutosaveController(
    private val answersRepository: AnswersRepository,
    externalScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val autosaveIntervalSec: Int,
    private val autosaveFactory: (String) -> AutosaveController,
) : ExamAutosaveController {

    private val tickerScope = externalScope + ioDispatcher

    private var currentController: AutosaveController? = null
    private var tickerJob: Job? = null

    override fun prepare(attemptId: String) {
        stopInternal()

        val controller = autosaveFactory(attemptId).apply {
            configure(autosaveIntervalSec)
        }
        currentController = controller

        startAutosaveTicker()
    }

    override fun recordAnswer(answer: AnswerEntity) {
        val controller = currentController
        tickerScope.launch {
            if (controller != null) {
                controller.onAnswer(
                    questionId = answer.questionId,
                    choiceId = answer.selectedId ?: "",
                    correctChoiceId = answer.correctId,
                    isCorrect = answer.isCorrect,
                    displayIndex = answer.displayIndex,
                    timeSpentSec = answer.timeSpentSec,
                    seenAt = answer.seenAt,
                    answeredAt = answer.answeredAt,
                )
            } else {
                answersRepository.upsert(listOf(answer))
            }
        }
    }

    override fun flush(force: Boolean) {
        currentController?.flush(force)
    }

    override suspend fun stop() {
        stopInternal()
    }

    private fun startAutosaveTicker() {
        tickerJob?.cancel()
        val controller = currentController ?: return

        tickerJob = tickerScope.launch {
            while (isActive) {
                delay(autosaveIntervalSec * 1000L)
                controller.onTick()
            }
        }
    }

    private fun stopInternal() {
        tickerJob?.cancel()
        tickerJob = null
        currentController?.flush(force = true)
        currentController = null
    }
}
