package com.qweld.app.feature.exam.vm

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

class PracticeShortcutsTest {

  @Test
  fun repeatMistakesBlueprintUsesWrongPoolDistribution() {
    val wrongPool =
      listOf(
        "Q-A-1_wrong_one",
        "Q-A-1_wrong_two",
        "Q-C-9_wrong_one",
        "Q-C-9_wrong_two",
        "Q-D-13_wrong_one",
      )

    val blueprint = PracticeShortcuts.buildRepeatMistakesBlueprint(wrongPool, practiceSize = 6)

    assertNotNull(blueprint)
    assertEquals(6, blueprint.totalQuestions)
    val quotas = blueprint.taskQuotas.associate { it.taskId to it.required }
    assertEquals(setOf("A-1", "C-9", "D-13"), quotas.keys)
    assertEquals(3, quotas["A-1"])
    assertEquals(2, quotas["C-9"])
    assertEquals(1, quotas["D-13"])
  }

  @Test
  fun repeatMistakesBlueprintHandlesSingleTaskPool() {
    val wrongPool = listOf("Q-B-2_only_one")

    val blueprint = PracticeShortcuts.buildRepeatMistakesBlueprint(wrongPool, practiceSize = 5)

    assertNotNull(blueprint)
    assertEquals(5, blueprint.totalQuestions)
    assertEquals(1, blueprint.taskQuotas.size)
    val quota = blueprint.taskQuotas.first()
    assertEquals("B-2", quota.taskId)
    assertEquals(5, quota.required)
  }
}
