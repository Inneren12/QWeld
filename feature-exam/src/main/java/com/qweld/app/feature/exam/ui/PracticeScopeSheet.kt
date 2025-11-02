package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScopeSheet(
  size: Int,
  tasksByBlock: Map<String, List<String>>,
  blockLabels: Map<String, String> = emptyMap(),
  taskLabels: Map<String, String> = emptyMap(),
  scope: PracticeScope,
  blueprint: ExamBlueprint,
  lastScope: UserPrefsDataStore.LastScope?,
  onDismiss: () -> Unit,
  onConfirm: (PracticeScope, Int, PracticeScopePresetName?) -> Boolean,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val sanitizedSize = remember(size) { PracticeConfig.sanitizeSize(size) }
  var sizeText by remember { mutableStateOf(sanitizedSize.toString()) }
  var showInvalidSize by remember { mutableStateOf(false) }

  LaunchedEffect(sanitizedSize) {
    sizeText = sanitizedSize.toString()
    showInvalidSize = false
  }

  val parsedSize = sizeText.toIntOrNull()
  val clampedSize = parsedSize?.coerceIn(PracticeConfig.MIN_SIZE, PracticeConfig.MAX_SIZE)
  val effectiveSize = clampedSize ?: sanitizedSize
  val isOutOfRange = parsedSize != null && clampedSize != null && parsedSize != clampedSize

  fun setSize(value: Int) {
    val resolved = value.coerceIn(PracticeConfig.MIN_SIZE, PracticeConfig.MAX_SIZE)
    sizeText = resolved.toString()
    showInvalidSize = false
  }

  fun adjustSize(delta: Int) {
    val base = (clampedSize ?: sanitizedSize) + delta
    setSize(base)
  }

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
  var selectedPreset by remember {
    mutableStateOf(
      detectPresetForScope(scope, lastScope).takeIf { it == PracticeScopePresetName.LAST_USED },
    )
  }

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
    selectedPreset = detectPresetForScope(initial, lastScope).takeIf {
      it == PracticeScopePresetName.LAST_USED
    }
  }

  val previewSize by remember(selectedBlocks, selectedTasks, distribution, effectiveSize, blueprint) {
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
        total = effectiveSize,
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
    selectedPreset = detectPresetForScope(currentScope, lastScope).takeIf {
      it == PracticeScopePresetName.LAST_USED
    }
  }

  val blockOrder = listOf("A", "B", "C", "D")

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding(),
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = true),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Text(
            text = stringResource(id = R.string.practice_scope_title),
            style = MaterialTheme.typography.titleLarge,
          )
        }
        item {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(id = R.string.practice_scope_presets),
              style = MaterialTheme.typography.titleMedium,
            )
            val preset = PracticeScopePresetName.LAST_USED
            val enabled = lastScope != null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                label = { Text(text = stringResource(id = R.string.practice_scope_preset_last_used)) },
                enabled = enabled,
                colors = FilterChipDefaults.filterChipColors(),
              )
            }
          }
        }
        item {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = stringResource(id = R.string.practice_scope_questions_label),
              style = MaterialTheme.typography.titleMedium,
            )
            val supportingText =
              if (showInvalidSize) {
                stringResource(id = R.string.practice_scope_questions_invalid)
              } else {
                stringResource(
                  id = R.string.practice_scope_questions_hint,
                  PracticeConfig.MIN_SIZE,
                  PracticeConfig.MAX_SIZE,
                )
              }
            val supportingColor =
              if (showInvalidSize || isOutOfRange) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              }
            val minusEnabled = effectiveSize > PracticeConfig.MIN_SIZE
            val plusEnabled = effectiveSize < PracticeConfig.MAX_SIZE
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(onClick = { adjustSize(-1) }, enabled = minusEnabled) {
                Icon(
                  imageVector = Icons.Filled.Remove,
                  contentDescription = stringResource(id = R.string.practice_scope_questions_decrease),
                )
              }
              TextField(
                modifier =
                  Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                      if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                        return@onPreviewKeyEvent false
                      }
                      when (event.key) {
                        Key.DirectionUp, Key.DirectionRight -> {
                          adjustSize(+1)
                          true
                        }
                        Key.DirectionDown, Key.DirectionLeft -> {
                          adjustSize(-1)
                          true
                        }
                        else -> false
                      }
                    },
                value = sizeText,
                onValueChange = { newValue ->
                  val digits = newValue.filter { it.isDigit() }
                  sizeText = digits.take(3)
                  showInvalidSize = false
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                  Text(
                    text = supportingText,
                    color = supportingColor,
                    style = MaterialTheme.typography.bodySmall,
                  )
                },
                isError = showInvalidSize || isOutOfRange,
              )
              IconButton(onClick = { adjustSize(+1) }, enabled = plusEnabled) {
                Icon(
                  imageVector = Icons.Filled.Add,
                  contentDescription = stringResource(id = R.string.practice_scope_questions_increase),
                )
              }
            }
            val sliderSteps = (PracticeConfig.MAX_SIZE - PracticeConfig.MIN_SIZE).coerceAtLeast(1) - 1
            Slider(
              modifier = Modifier.fillMaxWidth(),
              value = effectiveSize.toFloat(),
              onValueChange = { updated -> setSize(updated.roundToInt()) },
              valueRange = PracticeConfig.MIN_SIZE.toFloat()..PracticeConfig.MAX_SIZE.toFloat(),
              steps = sliderSteps,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              TextButton(onClick = { setSize(PracticeConfig.MIN_SIZE) }, enabled = effectiveSize != PracticeConfig.MIN_SIZE) {
                Text(text = stringResource(id = R.string.practice_scope_questions_min, PracticeConfig.MIN_SIZE))
              }
              TextButton(onClick = { setSize(PracticeConfig.MAX_SIZE) }, enabled = effectiveSize != PracticeConfig.MAX_SIZE) {
                Text(text = stringResource(id = R.string.practice_scope_questions_max, PracticeConfig.MAX_SIZE))
              }
            }
          }
        }
        item {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(id = R.string.practice_scope_blocks),
              style = MaterialTheme.typography.titleMedium,
            )
            for (blockId in blockOrder) {
              val hasTasks = tasksByBlock[blockId].orEmpty().isNotEmpty()
              val blockTitle =
                blockLabels[normalizeValue(blockId)]?.takeIf { it.isNotBlank() }
                  ?: stringResource(id = R.string.practice_scope_block_label, blockId)
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
                Text(text = blockTitle, style = MaterialTheme.typography.bodyLarge)
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
        }
        item {
          TextButton(onClick = { showCustom = !showCustom }) {
            Text(text = stringResource(id = R.string.practice_scope_custom_tasks))
          }
        }
        if (showCustom) {
          item {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = stringResource(id = R.string.practice_scope_select_tasks),
                style = MaterialTheme.typography.titleMedium,
              )
              for (blockId in blockOrder) {
                val tasks = tasksByBlock[blockId].orEmpty()
                if (tasks.isEmpty()) continue
                val blockTitle =
                  blockLabels[normalizeValue(blockId)]?.takeIf { it.isNotBlank() }
                    ?: stringResource(id = R.string.practice_scope_block_label, blockId)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(text = blockTitle, style = MaterialTheme.typography.titleSmall)
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
                      val taskTitle =
                        taskLabels[normalizedTask]?.takeIf { it.isNotBlank() }?.let { label ->
                          "$taskId — $label"
                        } ?: taskId
                      Text(text = taskTitle, style = MaterialTheme.typography.bodyLarge)
                    }
                  }
                }
              }
            }
          }
        }
        item {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
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
        }
        item {
          Text(
            text = stringResource(id = R.string.practice_scope_result_size, previewSize),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .padding(top = 8.dp, bottom = 24.dp),
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
            val parsed = sizeText.toIntOrNull()
            if (parsed == null) {
              showInvalidSize = true
              return@Button
            }
            val resolvedSize = parsed.coerceIn(PracticeConfig.MIN_SIZE, PracticeConfig.MAX_SIZE)
            if (resolvedSize != parsed) {
              sizeText = resolvedSize.toString()
            }
            showInvalidSize = false
            if (onConfirm(scopeToSubmit, resolvedSize, selectedPreset)) {
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
