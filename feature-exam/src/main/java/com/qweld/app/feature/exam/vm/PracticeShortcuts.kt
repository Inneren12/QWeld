package com.qweld.app.feature.exam.vm

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qweld.app.data.prefs.UserPrefs
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.mapTaskToBlock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale

class PracticeShortcuts(
  private val attemptsRepository: AttemptsRepository,
  private val answersRepository: AnswersRepository,
  private val userPrefs: UserPrefs,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val logger: (String) -> Unit = { message -> Timber.i(message) },
) : ViewModel() {

  private val _repeatMistakes =
    MutableStateFlow(
      RepeatMistakesState(
        availability = RepeatMistakesAvailability.NO_FINISHED_ATTEMPT,
        practiceSize = PracticeConfig.DEFAULT_SIZE,
        wrongPool = 0,
        blueprint = null,
      ),
    )
  val repeatMistakes: StateFlow<RepeatMistakesState> = _repeatMistakes.asStateFlow()

  private var latestPracticeSize: Int = PracticeConfig.DEFAULT_SIZE

  init {
    viewModelScope.launch {
      userPrefs.practiceSizeFlow().collect { size ->
        val resolved = PracticeConfig.sanitizeSize(size)
        latestPracticeSize = resolved
        loadRepeatMistakes(resolved)
      }
    }
  }

  fun refresh() {
    viewModelScope.launch { loadRepeatMistakes(latestPracticeSize) }
  }

  private suspend fun loadRepeatMistakes(practiceSize: Int) {
    val attempt = withContext(ioDispatcher) { attemptsRepository.getLastFinishedAttempt() }
    if (attempt == null) {
      _repeatMistakes.value =
        RepeatMistakesState(
          availability = RepeatMistakesAvailability.NO_FINISHED_ATTEMPT,
          practiceSize = practiceSize,
          wrongPool = 0,
          blueprint = null,
        )
      return
    }

    val wrongQuestionIds = withContext(ioDispatcher) { answersRepository.listWrongByAttempt(attempt.id) }
    val blueprint = buildRepeatMistakesBlueprint(wrongQuestionIds, practiceSize)
    if (blueprint == null) {
      _repeatMistakes.value =
        RepeatMistakesState(
          availability = RepeatMistakesAvailability.NO_MISTAKES,
          practiceSize = practiceSize,
          wrongPool = wrongQuestionIds.size,
          blueprint = null,
        )
      return
    }

    logger("[practice_repeat] size=${blueprint.totalQuestions} wrongPool=${wrongQuestionIds.size}")
    _repeatMistakes.value =
      RepeatMistakesState(
        availability = RepeatMistakesAvailability.AVAILABLE,
        practiceSize = blueprint.totalQuestions,
        wrongPool = wrongQuestionIds.size,
        blueprint = blueprint,
      )
  }

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun buildRepeatMistakesBlueprint(
      wrongQuestionIds: List<String>,
      practiceSize: Int,
    ): ExamBlueprint? {
      if (wrongQuestionIds.isEmpty()) return null
      val sanitizedSize = practiceSize.coerceAtLeast(1)
      val taskEntries = mutableMapOf<String, TaskEntry>()
      for (questionId in wrongQuestionIds) {
        val taskId = extractTaskId(questionId) ?: continue
        val blockId = mapTaskToBlock(taskId) ?: continue
        val current = taskEntries[taskId]
        if (current == null) {
          taskEntries[taskId] = TaskEntry(taskId, blockId, 1)
        } else {
          taskEntries[taskId] = current.copy(count = current.count + 1)
        }
      }
      if (taskEntries.isEmpty()) return null
      val orderedEntries =
        taskEntries.values.sortedWith(compareByDescending<TaskEntry> { it.count }.thenBy { it.taskId })
      val totalWeight = orderedEntries.sumOf { it.count }
      if (totalWeight <= 0) return null
      val allocations = orderedEntries.associate { it.taskId to 0 }.toMutableMap()
      val remainders = mutableListOf<TaskRemainder>()
      var allocated = 0
      for (entry in orderedEntries) {
        val exact = sanitizedSize * entry.count.toDouble() / totalWeight
        val base = exact.toInt()
        allocations[entry.taskId] = base
        allocated += base
        remainders +=
          TaskRemainder(
            taskId = entry.taskId,
            blockId = entry.blockId,
            remainder = exact - base,
            count = entry.count,
          )
      }
      var remaining = sanitizedSize - allocated
      if (remaining > 0) {
        val sortedRemainders =
          remainders.sortedWith(
            compareByDescending<TaskRemainder> { it.remainder }
              .thenByDescending { it.count }
              .thenBy { it.taskId },
          )
        if (sortedRemainders.isNotEmpty()) {
          var index = 0
          while (remaining > 0) {
            val next = sortedRemainders[index % sortedRemainders.size]
            allocations[next.taskId] = allocations.getValue(next.taskId) + 1
            remaining -= 1
            index += 1
          }
        }
      }
      val quotas = mutableListOf<TaskQuota>()
      for (entry in orderedEntries) {
        val required = allocations[entry.taskId] ?: 0
        if (required > 0) {
          quotas += TaskQuota(taskId = entry.taskId, blockId = entry.blockId, required = required)
        }
      }
      if (quotas.isEmpty()) return null
      val totalQuestions = quotas.sumOf { it.required }
      if (totalQuestions <= 0) return null
      return ExamBlueprint(totalQuestions = totalQuestions, taskQuotas = quotas)
    }

    private fun extractTaskId(questionId: String): String? {
      val raw = questionId.substringAfter("Q-", missingDelimiterValue = "")
      if (raw.isEmpty()) return null
      val taskId = raw.substringBefore('_', missingDelimiterValue = "").ifBlank { null }
      return taskId?.uppercase(Locale.US)
    }
  }

  private data class TaskEntry(val taskId: String, val blockId: String, val count: Int)

  private data class TaskRemainder(
    val taskId: String,
    val blockId: String,
    val remainder: Double,
    val count: Int,
  )
}

enum class RepeatMistakesAvailability {
  AVAILABLE,
  NO_FINISHED_ATTEMPT,
  NO_MISTAKES,
}

data class RepeatMistakesState(
  val availability: RepeatMistakesAvailability,
  val practiceSize: Int,
  val wrongPool: Int,
  val blueprint: ExamBlueprint?,
) {
  val isEnabled: Boolean = availability == RepeatMistakesAvailability.AVAILABLE && blueprint != null
}

class PracticeShortcutsFactory(
  private val attemptsRepository: AttemptsRepository,
  private val answersRepository: AnswersRepository,
  private val userPrefs: UserPrefs,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModelProvider.Factory {

  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(PracticeShortcuts::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return PracticeShortcuts(
        attemptsRepository = attemptsRepository,
        answersRepository = answersRepository,
        userPrefs = userPrefs,
        ioDispatcher = ioDispatcher,
      ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
  }
}
