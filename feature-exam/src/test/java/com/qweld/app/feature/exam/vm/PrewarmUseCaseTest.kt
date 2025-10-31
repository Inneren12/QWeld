package com.qweld.app.feature.exam.vm

import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.feature.exam.data.AssetQuestionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrewarmUseCaseTest {

  @Test
  fun prewarmLoadsAllTasksAndReportsProgress() = runTest {
    val tasks = ExamBlueprint.default().taskQuotas.map { it.taskId }
    val assets = tasks.associate { taskId ->
      val path = "questions/en/tasks/$taskId.json"
      path to taskPayload(taskId)
    }
    val openCount = mutableMapOf<String, Int>()
    val repository =
      AssetQuestionRepository(
        assetReader =
          AssetQuestionRepository.AssetReader(
            open = { path ->
              val payload = assets[path] ?: return@AssetQuestionRepository.AssetReader null
              openCount[path] = openCount.getOrDefault(path, 0) + 1
              payload.byteInputStream()
            },
          ),
        localeResolver = { "en" },
        json = Json { ignoreUnknownKeys = true },
      )
    val dispatcher = StandardTestDispatcher(testScheduler)
    val useCase = PrewarmUseCase(repository, dispatcher) { testScheduler.currentTime }
    val progress = mutableListOf<Pair<Int, Int>>()

    useCase.prewarm("en", tasks.toSet()) { loaded, total ->
      progress += loaded to total
    }

    val expectedPaths = assets.keys
    assertEquals(tasks.size + 1, progress.size)
    assertEquals((0..tasks.size).toList(), progress.map { it.first })
    assertTrue(progress.all { it.second == tasks.size })
    assertEquals(expectedPaths, openCount.keys)
    assertTrue(openCount.values.all { it == 1 })
  }

  private fun taskPayload(taskId: String): String {
    val choiceId = "${taskId}_CHOICE_A"
    return """
      [
        {
          "id": "${taskId}_Q1",
          "taskId": "$taskId",
          "blockId": "${taskId.substringBefore("-")}",
          "stem": { "en": "Stem $taskId" },
          "choices": [
            { "id": "$choiceId", "text": { "en": "Answer" } }
          ],
          "correctId": "$choiceId"
        }
      ]
    """.trimIndent()
  }
}
