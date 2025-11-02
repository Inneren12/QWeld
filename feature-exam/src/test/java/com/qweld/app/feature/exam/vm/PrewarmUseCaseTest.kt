package com.qweld.app.feature.exam.vm

import com.qweld.app.feature.exam.data.AssetQuestionRepository
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrewarmUseCaseTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  @Test
  fun prewarmLoadsEveryTaskAndReportsProgress() = runTest {
    val openedPaths = CopyOnWriteArrayList<String>()
    val repository = repositoryWithTasks(openedPaths)
    val useCase =
      PrewarmUseCase(
        repository = repository,
        prewarmDisabled = flowOf(false),
        ioDispatcher = dispatcherRule.dispatcher,
        nowProvider = { 0L },
      )
    val tasks = setOf("A-1", "A-2", "A-3")
    val progress = mutableListOf<Pair<Int, Int>>()

    useCase.prewarm(locale = "en", tasks = tasks) { loaded, total -> progress += loaded to total }

    val taskPaths = openedPaths.filter { it.contains("/tasks/") }.toSet()
    val expectedPaths = tasks.map { "questions/en/tasks/$it.json" }.toSet()
    assertEquals(expectedPaths, taskPaths)
    assertTrue(progress.first() == (0 to tasks.size))
    assertEquals(tasks.size to tasks.size, progress.last())
  }

  @Test
  fun missingTaskFallsBackToBank() = runTest {
    val openedPaths = CopyOnWriteArrayList<String>()
    val repository = repositoryWithTasks(openedPaths, missingTasks = setOf("A-1"), includeBank = true)
    val useCase =
      PrewarmUseCase(
        repository = repository,
        prewarmDisabled = flowOf(false),
        ioDispatcher = dispatcherRule.dispatcher,
        nowProvider = { 0L },
      )
    val progress = mutableListOf<Pair<Int, Int>>()

    useCase.prewarm(locale = "en", tasks = setOf("A-1")) { loaded, total -> progress += loaded to total }

    assertEquals(1 to 1, progress.last())
    assertTrue(openedPaths.any { it == "questions/en/bank.v1.json" })
  }

  private fun repositoryWithTasks(
    openedPaths: MutableList<String>,
    missingTasks: Set<String> = emptySet(),
    includeBank: Boolean = false,
  ): AssetQuestionRepository {
    val reader =
      AssetQuestionRepository.AssetReader(
        opener = { path ->
          openedPaths += path
          when {
            path.endsWith("bank.v1.json") && includeBank -> bankPayload().byteInputStream()
            path.contains("/tasks/") -> {
              val taskId = path.substringAfterLast("/").removeSuffix(".json")
              if (missingTasks.contains(taskId)) {
                null
              } else {
                taskPayload(taskId).byteInputStream()
              }
            }
            else -> null
          }
        },
      )
    return AssetQuestionRepository(
      assetReader = reader,
      localeResolver = { "en" },
      json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
    )
  }

  private fun taskPayload(taskId: String): String {
    return """
      [
        {
          "id": "Q-$taskId-1",
          "taskId": "$taskId",
          "blockId": "A",
          "locale": "en",
          "stem": { "en": "Stem" },
          "choices": [
            { "id": "${taskId}-A", "text": { "en": "Choice A" } },
            { "id": "${taskId}-B", "text": { "en": "Choice B" } }
          ],
          "correctId": "${taskId}-A"
        }
      ]
    """.trimIndent()
  }

  private fun bankPayload(): String {
    return """
      [
        {
          "id": "BANK-1",
          "taskId": "A-1",
          "blockId": "A",
          "locale": "en",
          "stem": { "en": "Bank" },
          "choices": [
            { "id": "BANK-A", "text": { "en": "Choice A" } },
            { "id": "BANK-B", "text": { "en": "Choice B" } }
          ],
          "correctId": "BANK-A"
        }
      ]
    """.trimIndent()
  }
}
