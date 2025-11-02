package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.vm.ExamViewModel
import com.qweld.app.feature.exam.vm.Distribution
import com.qweld.app.feature.exam.vm.PracticeScope
import com.qweld.app.feature.exam.vm.PracticeConfig
import com.qweld.app.feature.exam.vm.PracticeScopePresetName
import com.qweld.app.feature.exam.vm.detectPresetForScope
import com.qweld.app.feature.exam.vm.toScope
import java.util.LinkedHashSet
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScopeSheet(
  size: Int,
  tasksByBlock: Map<String, List<String>>,
  scope: PracticeScope,
  blueprint: ExamBlueprint,
  lastScope: UserPrefsDataStore.LastScope?,
  onDismiss: () -> Unit,
  onConfirm: (PracticeScope, PracticeScopePresetName?) -> Boolean,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val sanitizedSize = remember(size) { PracticeConfig.sanitizeSize(size) }

  fun normalizeSet(values: Set<String>): Set<String> {
    return values
      .asSequence()
      .map { it.trim().uppercase(Locale.US) }
      .filter { it.isNotBlank() }
      .toCollection(LinkedHashSet())
  }

  fun normalizeValue(value: String): String {
    return value.trim().uppercase(Locale.US)
  }

  var selectedBlocks by remember { mutableStateOf(normalizeSet(scope.blocks)) }
  var selectedTasks by remember { mutableStateOf(normalizeSet(scope.taskIds)) }
  var distribution by remember { mutableStateOf(scope.distribution) }
  var showCustom by remember { mutableStateOf(scope.taskIds.isNotEmpty()) }
  var selectedPreset by remember { mutableStateOf(detectPresetForScope(scope, lastScope)) }

  LaunchedEffect(scope, lastScope) {
    val initial =
      if (lastScope != null) {
        PracticeScopePresetName.LAST_USED.toScope(scope.distribution, lastScope) ?: scope
      } else {
        scope
      }
    val normalizedBlocks = normalizeSet(initial.blocks)
    val normalizedTasks = normalizeSet(initial.taskIds)
    selectedBlocks = normalizedBlocks
    selectedTasks = normalizedTasks
    distribution = initial.distribution
    showCustom = normalizedTasks.isNotEmpty()
    selectedPreset =
      if (lastScope != null) PracticeScopePresetName.LAST_USED else detectPresetForScope(initial, lastScope)
  }

  val previewSize by remember {
    derivedStateOf {
      val previewScope =
        PracticeScope(
          blocks = selectedBlocks,
          taskIds = selectedTasks,
          distribution = distribution,
        )
      ExamViewModel.resolvePracticeQuotas(
        blueprint = blueprint,
        scope = previewScope,
        total = sanitizedSize,
      ).values.sum()
    }
  }

  fun updatePresetFromSelection() {
    val currentScope =
      PracticeScope(
        blocks = selectedBlocks,
        taskIds = selectedTasks,
        distribution = distribution,
      )
    selectedPreset = detectPresetForScope(currentScope, lastScope)
  }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    val scrollState = rememberScrollState()
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(scrollState)
        .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = stringResource(id = R.string.practice_scope_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(id = R.string.practice_scope_presets),
          style = MaterialTheme.typography.titleMedium,
        )
        val presets =
          listOf(
            PracticeScopePresetName.A_ONLY,
            PracticeScopePresetName.B_ONLY,
            PracticeScopePresetName.C_ONLY,
            PracticeScopePresetName.D_ONLY,
            PracticeScopePresetName.A_B,
            PracticeScopePresetName.C_D,
            PracticeScopePresetName.ALL,
            PracticeScopePresetName.LAST_USED,
          )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for (preset in presets) {
            val labelRes =
              when (preset) {
                PracticeScopePresetName.A_ONLY -> R.string.practice_scope_preset_a_only
                PracticeScopePresetName.B_ONLY -> R.string.practice_scope_preset_b_only
                PracticeScopePresetName.C_ONLY -> R.string.practice_scope_preset_c_only
                PracticeScopePresetName.D_ONLY -> R.string.practice_scope_preset_d_only
                PracticeScopePresetName.A_B -> R.string.practice_scope_preset_a_b
                PracticeScopePresetName.C_D -> R.string.practice_scope_preset_c_d
                PracticeScopePresetName.ALL -> R.string.practice_scope_preset_all
                PracticeScopePresetName.LAST_USED -> R.string.practice_scope_preset_last_used
              }
            val enabled = preset != PracticeScopePresetName.LAST_USED || lastScope != null
            FilterChip(
              selected = selectedPreset == preset,
              onClick = {
                if (!enabled) return@FilterChip
                val applied = preset.toScope(distribution, lastScope) ?: return@FilterChip
                val blocks = normalizeSet(applied.blocks)
                val tasks = normalizeSet(applied.taskIds)
                selectedBlocks = blocks
                selectedTasks = tasks
                distribution = applied.distribution
                showCustom = tasks.isNotEmpty()
                selectedPreset = preset
              },
              label = { Text(text = stringResource(id = labelRes)) },
              enabled = enabled,
              colors = FilterChipDefaults.filterChipColors(),
            )
          }
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(id = R.string.practice_scope_blocks),
          style = MaterialTheme.typography.titleMedium,
        )
        val blockOrder = listOf("A", "B", "C", "D")
        for (blockId in blockOrder) {
          val hasTasks = tasksByBlock[blockId].orEmpty().isNotEmpty()
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = selectedBlocks.contains(normalizeValue(blockId)),
              onCheckedChange = { checked ->
                val updated = LinkedHashSet(selectedBlocks)
                if (checked) {
                  updated += normalizeValue(blockId)
                } else {
                  updated -= normalizeValue(blockId)
                }
                selectedBlocks = normalizeSet(updated)
                updatePresetFromSelection()
              },
              enabled = hasTasks,
            )
            Text(
              text = stringResource(id = R.string.practice_scope_block_label, blockId),
              style = MaterialTheme.typography.bodyLarge,
            )
          }
        }
        val hasSelection = selectedTasks.isNotEmpty() || selectedBlocks.isNotEmpty()
        if (!hasSelection) {
          Text(
            text = stringResource(id = R.string.practice_scope_selection_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
      TextButton(onClick = { showCustom = !showCustom }) {
        Text(text = stringResource(id = R.string.practice_scope_custom_tasks))
      }
      if (showCustom) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = stringResource(id = R.string.practice_scope_select_tasks),
            style = MaterialTheme.typography.titleMedium,
          )
          for ((blockId, tasks) in tasksByBlock) {
            if (tasks.isEmpty()) continue
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = stringResource(id = R.string.practice_scope_block_label, blockId),
                  style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  TextButton(
                    onClick = {
                      val updated = LinkedHashSet(selectedTasks)
                      updated.addAll(tasks.map(::normalizeValue))
                      selectedTasks = normalizeSet(updated)
                      showCustom = true
                      updatePresetFromSelection()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                  ) {
                    Text(text = stringResource(id = R.string.practice_scope_select_all_block))
                  }
                  TextButton(
                    onClick = {
                      val updated = LinkedHashSet(selectedTasks)
                      val normalized = tasks.map(::normalizeValue).toSet()
                      updated.removeAll(normalized)
                      selectedTasks = normalizeSet(updated)
                      showCustom = true
                      updatePresetFromSelection()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                  ) {
                    Text(text = stringResource(id = R.string.practice_scope_clear_block))
                  }
                }
              }
              for (taskId in tasks) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  val normalizedTask = normalizeValue(taskId)
                  Checkbox(
                    checked = selectedTasks.contains(normalizedTask),
                    onCheckedChange = { checked ->
                      val updated = LinkedHashSet(selectedTasks)
                      if (checked) {
                        updated += normalizedTask
                      } else {
                        updated -= normalizedTask
                      }
                      selectedTasks = normalizeSet(updated)
                      showCustom = true
                      updatePresetFromSelection()
                    },
                  )
                  Text(text = taskId, style = MaterialTheme.typography.bodyLarge)
                }
              }
            }
          }
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(id = R.string.practice_scope_distribution),
          style = MaterialTheme.typography.titleMedium,
        )
        val options = listOf(Distribution.Proportional, Distribution.Even)
        for (option in options) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = distribution == option,
              onClick = {
                distribution = option
                updatePresetFromSelection()
              },
            )
            val label =
              when (option) {
                Distribution.Proportional -> stringResource(id = R.string.practice_scope_distribution_proportional)
                Distribution.Even -> stringResource(id = R.string.practice_scope_distribution_even)
              }
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
      Text(
        text = stringResource(id = R.string.practice_scope_result_size, previewSize),
        style = MaterialTheme.typography.bodyMedium,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onDismiss) {
          Text(text = stringResource(id = R.string.practice_scope_cancel))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Button(
          onClick = {
            val normalizedBlocks = selectedBlocks.filter { it.isNotBlank() }.toSet()
            val normalizedTasks = selectedTasks.filter { it.isNotBlank() }.toSet()
            val scopeToSubmit =
              PracticeScope(
                blocks = normalizedBlocks,
                taskIds = normalizedTasks,
                distribution = distribution,
              )
            if (onConfirm(scopeToSubmit, selectedPreset)) {
              onDismiss()
            }
          },
        ) {
          Text(text = stringResource(id = R.string.practice_scope_start))
        }
      }
    }
  }
}
