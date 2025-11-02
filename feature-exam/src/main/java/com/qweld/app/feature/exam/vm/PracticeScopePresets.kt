package com.qweld.app.feature.exam.vm

import com.qweld.app.data.prefs.UserPrefsDataStore
import java.util.LinkedHashSet
import java.util.Locale

enum class PracticeScopePresetName(val logName: String) {
  A_ONLY("A-only"),
  B_ONLY("B-only"),
  C_ONLY("C-only"),
  D_ONLY("D-only"),
  A_B("A+B"),
  C_D("C+D"),
  ALL("All"),
  LAST_USED("LastUsed"),
}

internal fun PracticeScopePresetName.toScope(
  currentDistribution: Distribution,
  lastScope: UserPrefsDataStore.LastScope?,
): PracticeScope? {
  return when (this) {
    PracticeScopePresetName.A_ONLY ->
      PracticeScope(blocks = linkedSetOf("A"), distribution = currentDistribution)
    PracticeScopePresetName.B_ONLY ->
      PracticeScope(blocks = linkedSetOf("B"), distribution = currentDistribution)
    PracticeScopePresetName.C_ONLY ->
      PracticeScope(blocks = linkedSetOf("C"), distribution = currentDistribution)
    PracticeScopePresetName.D_ONLY ->
      PracticeScope(blocks = linkedSetOf("D"), distribution = currentDistribution)
    PracticeScopePresetName.A_B ->
      PracticeScope(blocks = linkedSetOf("A", "B"), distribution = currentDistribution)
    PracticeScopePresetName.C_D ->
      PracticeScope(blocks = linkedSetOf("C", "D"), distribution = currentDistribution)
    PracticeScopePresetName.ALL ->
      PracticeScope(blocks = linkedSetOf("A", "B", "C", "D"), distribution = currentDistribution)
    PracticeScopePresetName.LAST_USED -> {
      if (lastScope == null) return null
      val distribution = lastScope.distribution.toDistribution() ?: return null
      val blocks = normalizeSet(lastScope.blocks)
      val tasks = normalizeSet(lastScope.tasks)
      PracticeScope(blocks = blocks, taskIds = tasks, distribution = distribution)
    }
  }
}

internal fun detectPresetForScope(
  scope: PracticeScope,
  lastScope: UserPrefsDataStore.LastScope?,
): PracticeScopePresetName? {
  val normalizedTasks = normalizeSet(scope.taskIds)
  if (normalizedTasks.isNotEmpty()) {
    if (lastScope == null) return null
    val storedTasks = normalizeSet(lastScope.tasks)
    val storedBlocks = normalizeSet(lastScope.blocks)
    val matchesTasks = normalizedTasks == storedTasks
    val matchesBlocks = normalizeSet(scope.blocks) == storedBlocks
    val matchesDistribution = scope.distribution.name.equals(lastScope.distribution, ignoreCase = true)
    return if (matchesTasks && matchesBlocks && matchesDistribution) {
      PracticeScopePresetName.LAST_USED
    } else {
      null
    }
  }
  val normalizedBlocks = normalizeSet(scope.blocks)
  return when (normalizedBlocks) {
    setOf("A") -> PracticeScopePresetName.A_ONLY
    setOf("B") -> PracticeScopePresetName.B_ONLY
    setOf("C") -> PracticeScopePresetName.C_ONLY
    setOf("D") -> PracticeScopePresetName.D_ONLY
    setOf("A", "B") -> PracticeScopePresetName.A_B
    setOf("C", "D") -> PracticeScopePresetName.C_D
    setOf("A", "B", "C", "D") -> PracticeScopePresetName.ALL
    else -> null
  }
}

private fun normalizeSet(values: Set<String>): Set<String> {
  return values
    .asSequence()
    .map { it.trim().uppercase(Locale.US) }
    .filter { it.isNotBlank() }
    .toCollection(LinkedHashSet())
}

private fun String.toDistribution(): Distribution? {
  return when (uppercase(Locale.US)) {
    Distribution.Proportional.name.uppercase(Locale.US) -> Distribution.Proportional
    Distribution.Even.name.uppercase(Locale.US) -> Distribution.Even
    else -> null
  }
}
