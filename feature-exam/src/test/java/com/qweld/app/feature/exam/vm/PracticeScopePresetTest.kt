package com.qweld.app.feature.exam.vm

import com.qweld.app.data.prefs.UserPrefsDataStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PracticeScopePresetTest {

  @Test
  fun aOnlyPresetUsesSingleBlock() {
    val scope = PracticeScopePresetName.A_ONLY.toScope(Distribution.Proportional, lastScope = null)

    assertNotNull(scope)
    scope!!
    assertEquals(setOf("A"), scope.blocks)
    assertEquals(emptySet<String>(), scope.taskIds)
    assertEquals(Distribution.Proportional, scope.distribution)
  }

  @Test
  fun lastUsedPresetRestoresStoredScope() {
    val stored =
      UserPrefsDataStore.LastScope(
        blocks = setOf("b", "A"),
        tasks = setOf("a-1", "B-6"),
        distribution = "Even",
      )

    val scope = PracticeScopePresetName.LAST_USED.toScope(Distribution.Proportional, stored)

    assertNotNull(scope)
    scope!!
    assertEquals(setOf("A", "B"), scope.blocks)
    assertEquals(setOf("A-1", "B-6"), scope.taskIds)
    assertEquals(Distribution.Even, scope.distribution)
  }
}
