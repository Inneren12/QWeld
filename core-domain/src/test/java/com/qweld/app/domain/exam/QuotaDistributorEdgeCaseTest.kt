package com.qweld.app.domain.exam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuotaDistributorEdgeCaseTest {

  @Test
  fun fractionalProportionalAllocationRemainsDeterministic() {
    val quotas = mapOf("A-1" to 50, "B-1" to 30, "C-1" to 20)

    val allocation = QuotaDistributor.proportional(quotas, quotas.keys, total = 125)

    assertEquals(125, allocation.values.sum())
    assertEquals(63, allocation.getValue("A-1"))
    assertEquals(37, allocation.getValue("B-1"))
    assertEquals(25, allocation.getValue("C-1"))
  }

  @Test
  fun nonDivisiblePercentagesDistributeRemainderDeterministically() {
    val quotas = mapOf("A-1" to 33, "B-1" to 33, "C-1" to 34)

    val allocation = QuotaDistributor.proportional(quotas, quotas.keys, total = 125)

    assertEquals(125, allocation.values.sum())
    assertEquals(41, allocation.getValue("A-1"))
    assertEquals(41, allocation.getValue("B-1"))
    assertEquals(43, allocation.getValue("C-1"))
  }

  @Test
  fun evenSplitHandlesSmallTotals() {
    val chosen = setOf("A-1", "B-2", "C-3")

    val allocation = QuotaDistributor.even(chosen, total = 2)

    assertEquals(2, allocation.values.sum())
    assertTrue(allocation.values.all { it <= 1 })
  }

  @Test
  fun zeroWeightsFallbackToEvenDistribution() {
    val quotas = mapOf("A-1" to 0, "B-1" to 0, "C-1" to 10)

    val allocation = QuotaDistributor.proportional(quotas, quotas.keys, total = 5)

    assertEquals(5, allocation.values.sum())
    assertTrue(allocation.values.all { it >= 0 })
  }

  @Test
  fun nearTotalWeightHonoursUpperBound() {
    val quotas = mapOf("A-1" to 98, "B-1" to 1, "C-1" to 1)

    val allocation = QuotaDistributor.proportional(quotas, quotas.keys, total = 10)

    assertEquals(10, allocation.values.sum())
    assertTrue(allocation.getValue("A-1") >= 8)
  }

  @Test
  fun smallQuotaTasksRetainShareWhenDominatedByLargeWeight() {
    val quotas = mapOf("A-1" to 90, "B-1" to 5, "C-1" to 5)

    val allocation = QuotaDistributor.proportional(quotas, quotas.keys, total = 125)

    assertEquals(125, allocation.values.sum())
    assertEquals(113, allocation.getValue("A-1"))
    assertEquals(6, allocation.getValue("B-1"))
    assertEquals(6, allocation.getValue("C-1"))
  }

  @Test
  fun tinyTotalsDistributeLeftoverWithoutDroppingCount() {
    val quotas = mapOf("A-1" to 1, "B-1" to 1, "C-1" to 1)

    val allocation = QuotaDistributor.proportional(quotas, quotas.keys, total = 7)

    assertEquals(7, allocation.values.sum())
    assertEquals(3, allocation.getValue("A-1"))
    assertEquals(2, allocation.getValue("B-1"))
    assertEquals(2, allocation.getValue("C-1"))
  }
}
