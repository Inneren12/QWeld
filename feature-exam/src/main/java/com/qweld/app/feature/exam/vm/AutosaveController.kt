package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.repo.AnswersRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

open class AutosaveController(
  private val attemptId: String,
  private val answersRepository: AnswersRepository,
  private val scope: CoroutineScope,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  private val pending = LinkedHashMap<String, PendingAnswer>()
  private val mutex = Mutex()
  private var intervalSec: Int = DEFAULT_INTERVAL_SEC

  open fun configure(intervalSec: Int = DEFAULT_INTERVAL_SEC) {
    require(intervalSec > 0) { "Interval must be positive" }
    this.intervalSec = intervalSec
  }

  val intervalMillis: Long
    get() = intervalSec * 1_000L

  open fun onAnswer(
    questionId: String,
    choiceId: String,
    correctChoiceId: String,
    isCorrect: Boolean,
    displayIndex: Int,
    timeSpentSec: Int,
    seenAt: Long,
    answeredAt: Long,
  ) {
    val entity =
      AnswerEntity(
        attemptId = attemptId,
        displayIndex = displayIndex,
        questionId = questionId,
        selectedId = choiceId,
        correctId = correctChoiceId,
        isCorrect = isCorrect,
        timeSpentSec = timeSpentSec,
        seenAt = seenAt,
        answeredAt = answeredAt,
      )

    scope.launch {
      mutex.withLock { pending[questionId] = PendingAnswer(entity = entity, dirty = true) }
      val result =
        withContext(ioDispatcher) {
          runCatching {
            Timber.i("[autosave_submit] qId=%s correct=%s", questionId, isCorrect)
            answersRepository.upsert(listOf(entity))
          }
        }
      result.fold(
        onSuccess = { markClean(listOf(questionId)) },
        onFailure = { error ->
          Timber.e(error, "[autosave_submit] failed attemptId=%s qId=%s", attemptId, questionId)
          markDirty(listOf(questionId))
        },
      )
    }
  }

  open fun onTick() {
    scope.launch(ioDispatcher) {
      val targets = mutex.withLock {
        pending
          .mapNotNull { (questionId, pendingAnswer) ->
            pendingAnswer.takeIf { it.dirty }?.let { questionId to it.entity }
          }
      }
      if (targets.isEmpty()) {
        Timber.i("[autosave_tick] buffered=%d written=%d", 0, 0)
        return@launch
      }
      val result = runCatching { answersRepository.upsert(targets.map { it.second }) }
      if (result.isSuccess) {
        markClean(targets.map { it.first })
      } else {
        Timber.e(
          result.exceptionOrNull(),
          "[autosave_tick] failed attemptId=%s buffered=%d",
          attemptId,
          targets.size,
        )
        markDirty(targets.map { it.first })
      }
      val written = if (result.isSuccess) targets.size else 0
      Timber.i("[autosave_tick] buffered=%d written=%d", targets.size, written)
    }
  }

  open fun flush(force: Boolean = false) {
    scope.launch(ioDispatcher) {
      val targets = mutex.withLock {
        val selected =
          if (force) {
            pending.map { it.key to it.value.entity }
          } else {
            pending
              .mapNotNull { (questionId, pendingAnswer) ->
                pendingAnswer.takeIf { it.dirty }?.let { questionId to it.entity }
              }
          }
        if (force) {
          selected.forEach { (questionId, _) -> pending[questionId]?.dirty = true }
        }
        selected
      }
      if (targets.isEmpty()) {
        Timber.i("[autosave_flush] forced=%s written=%d", force, 0)
        return@launch
      }
      val result = runCatching { answersRepository.upsert(targets.map { it.second }) }
      if (result.isSuccess) {
        markClean(targets.map { it.first })
      } else {
        Timber.e(
          result.exceptionOrNull(),
          "[autosave_flush] failed attemptId=%s buffered=%d",
          attemptId,
          targets.size,
        )
        markDirty(targets.map { it.first })
      }
      val written = if (result.isSuccess) targets.size else 0
      Timber.i("[autosave_flush] forced=%s written=%d", force, written)
    }
  }

    private suspend fun markClean(ids: List<String>) {
    mutex.withLock { ids.forEach { id -> pending[id]?.dirty = false } }
  }

  private suspend fun markDirty(ids: List<String>) {
    mutex.withLock { ids.forEach { id -> pending[id]?.dirty = true } }
  }

  private data class PendingAnswer(
    var entity: AnswerEntity,
    var dirty: Boolean,
  )

  companion object {
    private const val DEFAULT_INTERVAL_SEC = 10
  }
}
