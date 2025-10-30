package com.qweld.app.domain.exam

import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FakeQuestionRepository(
  private val questions: List<Question>,
) : QuestionRepository {
  override fun loadQuestions(
    taskId: String,
    locale: String,
    allowFallbackToEnglish: Boolean,
  ): List<Question> {
    val primary =
      questions.filter { it.taskId == taskId && it.locale.equals(locale, ignoreCase = true) }
    if (primary.isNotEmpty() || !allowFallbackToEnglish || locale.equals("EN", ignoreCase = true)) {
      return primary.sortedBy { it.id }
    }
    val fallback =
      questions.filter { it.taskId == taskId && it.locale.equals("EN", ignoreCase = true) }
    return fallback.sortedBy { it.id }
  }
}

class FakeUserStatsRepository(
  private val stats: Map<String, ItemStats> = emptyMap(),
) : UserStatsRepository {
  override fun loadQuestionStats(
    userId: String,
    questionIds: Collection<String>
  ): Map<String, ItemStats> {
    return questionIds.mapNotNull { id -> stats[id]?.let { id to it } }.toMap()
  }
}

fun fixedClock(epochSeconds: Long = 0L): Clock =
  Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)

fun buildQuestion(
  taskId: String,
  index: Int,
  locale: String = "EN",
  correctIndex: Int = 0,
  blockId: String = taskId.substringBefore("-"),
  familyId: String? = null,
): Question {
  val questionId = "$taskId-$locale-$index"
  val choices =
    listOf("A", "B", "C", "D").mapIndexed { idx, label ->
      val choiceId = "$questionId-$label"
      Choice(
        id = choiceId,
        text =
          mapOf(
            "EN" to "Choice $label",
            "RU" to "Выбор $label",
          ),
      )
    }
  val correctChoiceId = choices[correctIndex].id
  return Question(
    id = questionId,
    taskId = taskId,
    blockId = blockId,
    locale = locale,
    familyId = familyId,
    choices = choices,
    correctChoiceId = correctChoiceId,
  )
}

fun generateQuestions(
  taskId: String,
  count: Int,
  locale: String = "EN",
  blockId: String = taskId.substringBefore("-"),
  familyIdProvider: (Int) -> String? = { null },
): List<Question> =
  (0 until count).map { index ->
    buildQuestion(
      taskId = taskId,
      index = index,
      locale = locale,
      blockId = blockId,
      familyId = familyIdProvider(index),
      correctIndex = index % 4,
    )
  }
