package com.qweld.app.feature.exam.vm

import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.repo.UserStatsRepository
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.TestIntegrity
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResumeUseCaseTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  private val statsRepository = object : UserStatsRepository {
    override suspend fun getUserItemStats(
      userId: String,
      ids: List<String>,
    ): Outcome<Map<String, com.qweld.app.domain.exam.ItemStats>> = Outcome.Ok(emptyMap())
  }

  @Test
  fun reconstructAttemptProducesDeterministicOrder() = runTest {
    val useCase = createUseCase()

    val firstResult =
      useCase.reconstructAttempt(
        mode = ExamMode.PRACTICE,
        seed = 42L,
        locale = "en",
        questionCount = 2,
      )
    val secondResult =
      useCase.reconstructAttempt(
        mode = ExamMode.PRACTICE,
        seed = 42L,
        locale = "en",
        questionCount = 2,
      )

    require(firstResult is Outcome.Ok)
    require(secondResult is Outcome.Ok)
    val firstIds = firstResult.value.attempt.questions.map { it.question.id }
    val secondIds = secondResult.value.attempt.questions.map { it.question.id }
    assertEquals(firstIds, secondIds)
    assertFalse(firstIds.isEmpty())
    assertTrue(firstResult.value.rationales.isNotEmpty())
  }

  @Test
  fun mergeAnswersReturnsFirstUnansweredIndex() = runTest {
    val useCase = createUseCase()
    val reconstructionResult =
      useCase.reconstructAttempt(
        mode = ExamMode.PRACTICE,
        seed = 1L,
        locale = "en",
        questionCount = 3,
      )
    require(reconstructionResult is Outcome.Ok)
    val questions = reconstructionResult.value.attempt.questions
    val first = questions.first()
    val answers =
      listOf(
        AnswerEntity(
          attemptId = "attempt",
          displayIndex = 0,
          questionId = first.question.id,
          selectedId = first.choices.first().id,
          correctId = first.choices.first().id,
          isCorrect = true,
          timeSpentSec = 12,
          seenAt = 0,
          answeredAt = 12,
        ),
      )

    val merged = useCase.mergeAnswers(questions, answers)

    assertEquals(first.question.id, merged.answers.keys.first())
    assertEquals(1, merged.currentIndex)
  }

  @Test
  fun remainingTimeClampsToZero() {
    val useCase = createUseCase()
    val attempt =
      AttemptEntity(
        id = "attempt",
        mode = ExamMode.IP_MOCK.name,
        locale = "EN",
        seed = 1L,
        questionCount = 2,
        startedAt = 0L,
      )
    val remainingPositive = useCase.remainingTime(attempt, ExamMode.IP_MOCK, nowMillis = 3_600_000L)
    assertEquals(Duration.ofHours(3), remainingPositive)

    val remainingZero =
      useCase.remainingTime(
        attempt,
        ExamMode.IP_MOCK,
        nowMillis = Duration.ofHours(5).toMillis(),
      )
    assertEquals(Duration.ZERO, remainingZero)

    val practiceRemaining = useCase.remainingTime(attempt, ExamMode.PRACTICE, nowMillis = 0L)
    assertEquals(Duration.ZERO, practiceRemaining)
  }

  private fun createUseCase(): ResumeUseCase {
    val blueprint = ExamBlueprint(
      totalQuestions = 3,
      taskQuotas = listOf(TaskQuota(taskId = "A-1", blockId = "A", required = 3)),
    )
    return ResumeUseCase(
      repository = repositoryWithQuestions(count = 3),
      statsRepository = statsRepository,
      blueprintProvider = { _, _ -> blueprint },
      userIdProvider = { "local" },
      ioDispatcher = dispatcherRule.dispatcher,
    )
  }

  private fun repositoryWithQuestions(count: Int): AssetQuestionRepository {
    val questions = buildString {
      append("[")
      repeat(count) { index ->
        if (index > 0) append(",")
        val qId = "Q${index + 1}"
        val choiceIds = listOf("A", "B", "C", "D").map { suffix -> "${qId}-$suffix" }
        append(
          """
          {
            \"id\": \"$qId\",
            \"taskId\": \"A-1\",
            \"blockId\": \"A\",
            \"locale\": \"en\",
            \"stem\": { \"en\": \"Stem $qId\" },
            \"choices\": [
              { \"id\": \"${choiceIds[0]}\", \"text\": { \"en\": \"Choice 1\" } },
              { \"id\": \"${choiceIds[1]}\", \"text\": { \"en\": \"Choice 2\" } },
              { \"id\": \"${choiceIds[2]}\", \"text\": { \"en\": \"Choice 3\" } },
              { \"id\": \"${choiceIds[3]}\", \"text\": { \"en\": \"Choice 4\" } }
            ],
            \"correctId\": \"${choiceIds[0]}\"
          }
          """.trimIndent(),
        )
      }
      append("]")
    }
    val assets =
      TestIntegrity.addIndexes(
        mapOf("questions/en/bank.v1.json" to questions.toByteArray()),
      )
    return AssetQuestionRepository(
      assetReader = AssetQuestionRepository.AssetReader(open = { path -> assets[path]?.inputStream() }),
      localeResolver = { "en" },
      json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
    )
  }
}
