package com.qweld.app.feature.exam.vm

import com.qweld.app.domain.exam.ExamBlueprint
import kotlin.test.Test
import kotlin.test.assertEquals

class PracticeScopeTest {
  private val blueprint = ExamBlueprint.default()

  @Test
  fun blockAUsesAllTasksAndMatchesSize() {
    val scope = PracticeScope(blocks = setOf("A"), distribution = Distribution.Proportional)
    val size = 10

    val quotas = ExamViewModel.resolvePracticeQuotas(blueprint, scope, size)

    assertEquals(setOf("A-1", "A-2", "A-3", "A-4", "A-5"), quotas.keys)
    assertEquals(size, quotas.values.sum())
  }

  @Test
  fun customEvenSplitHonoursSize() {
    val scope =
      PracticeScope(
        blocks = emptySet(),
        taskIds = setOf("B-6", "D-13"),
        distribution = Distribution.Even,
      )

    val quotas = ExamViewModel.resolvePracticeQuotas(blueprint, scope, 10)

    assertEquals(mapOf("B-6" to 5, "D-13" to 5), quotas)
  }

  @Test
  fun customProportionalUsesLargestRemainder() {
    val scope =
      PracticeScope(
        blocks = emptySet(),
        taskIds = setOf("C-8", "C-9", "C-10"),
        distribution = Distribution.Proportional,
      )

    val quotas = ExamViewModel.resolvePracticeQuotas(blueprint, scope, 12)

    assertEquals(mapOf("C-8" to 4, "C-9" to 5, "C-10" to 3), quotas)
    assertEquals(12, quotas.values.sum())
  }
}
