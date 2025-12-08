package com.qweld.app.feature.exam.vm

import com.qweld.app.feature.exam.data.AssetQuestionRepository
import com.qweld.app.feature.exam.data.TestIntegrity
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.Ignore

@OptIn(ExperimentalCoroutinesApi::class)
@Ignore("Pending prewarm behavior alignment")
class PrewarmUseCaseTest {
  @get:Rule val dispatcherRule = MainDispatcherRule()

  @Test
  fun prewarmLoadsEveryTaskAndReportsProgress() = runTest {
    val openedPaths = CopyOnWriteArrayList<String>()
    val tasks = setOf("A-1", "A-2", "A-3")
    val repository = repositoryWithTasks(openedPaths, indexTasks = tasks)
    val useCase =
      PrewarmUseCase(
        repository = repository,
        prewarmDisabled = flowOf(false),
        ioDispatcher = dispatcherRule.dispatcher,
        nowProvider = { 0L },
      )
    val progress = mutableListOf<Pair<Int, Int>>()

    val result =
      useCase.prewarm(locale = "en", tasks = tasks) { loaded, total ->
        progress += loaded to total
      }

    val taskPaths = openedPaths.filter { it.contains("/tasks/") }.toSet()
    val expectedPaths = tasks.map { "questions/en/tasks/$it.json" }.toSet()
    assertEquals(expectedPaths, taskPaths)
    assertTrue(progress.first() == (0 to tasks.size))
    assertEquals(tasks.size to tasks.size, progress.last())
    assertTrue(result is PrewarmUseCase.RunResult.Completed)
    val completed = result as PrewarmUseCase.RunResult.Completed
    assertEquals(tasks, completed.requestedTasks)
    assertEquals(tasks.size, completed.tasksLoaded)
  }

  @Test
  fun missingTaskFallsBackToBank() = runTest {
    val openedPaths = CopyOnWriteArrayList<String>()
    val repository =
      repositoryWithTasks(
        openedPaths,
        missingTasks = setOf("A-1"),
        includeBank = true,
        indexTasks = setOf("A-1"),
      )
    val useCase =
      PrewarmUseCase(
        repository = repository,
        prewarmDisabled = flowOf(false),
        ioDispatcher = dispatcherRule.dispatcher,
        nowProvider = { 0L },
      )
    val progress = mutableListOf<Pair<Int, Int>>()

    val result =
      useCase.prewarm(locale = "en", tasks = setOf("A-1")) { loaded, total ->
        progress += loaded to total
      }

    assertEquals(1 to 1, progress.last())
    assertTrue(openedPaths.any { it == "questions/en/bank.v1.json" })
    assertTrue(result is PrewarmUseCase.RunResult.Completed)
    val completed = result as PrewarmUseCase.RunResult.Completed
    assertTrue(completed.fallbackToBank)
  }

  private fun repositoryWithTasks(
    openedPaths: MutableList<String>,
    missingTasks: Set<String> = emptySet(),
    includeBank: Boolean = false,
    indexTasks: Set<String> = emptySet(),
  ): AssetQuestionRepository {
    val assets = mutableMapOf<String, ByteArray>()
    if (includeBank) {
      assets["questions/en/bank.v1.json"] = bankPayload().toByteArray()
    }
    val tasksForIndex = if (indexTasks.isEmpty()) setOf("A-1", "A-2", "A-3") else indexTasks
    for (task in tasksForIndex) {
      if (!missingTasks.contains(task)) {
        val path = "questions/en/tasks/$task.json"
        assets[path] = taskPayload(task).toByteArray()
      }
    }
    val indexedAssets = TestIntegrity.addIndexes(assets)
    val reader =
      AssetQuestionRepository.AssetReader(
        opener = { path ->
          openedPaths += path
          indexedAssets[path]?.inputStream()
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
