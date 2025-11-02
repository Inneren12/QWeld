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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.vm.Distribution
import com.qweld.app.feature.exam.vm.PracticeScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScopeSheet(
  size: Int,
  tasksByBlock: Map<String, List<String>>,
  scope: PracticeScope,
  onDismiss: () -> Unit,
  onConfirm: (PracticeScope) -> Boolean,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedBlocks by remember { mutableStateOf(scope.blocks.toSet()) }
  var selectedTasks by remember { mutableStateOf(scope.taskIds.toSet()) }
  var distribution by remember { mutableStateOf(scope.distribution) }
  var showCustom by remember { mutableStateOf(scope.taskIds.isNotEmpty()) }

  LaunchedEffect(scope) {
    selectedBlocks = scope.blocks.toSet()
    selectedTasks = scope.taskIds.toSet()
    distribution = scope.distribution
    showCustom = scope.taskIds.isNotEmpty()
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
              checked = selectedBlocks.contains(blockId),
              onCheckedChange = { checked ->
                val updated = selectedBlocks.toMutableSet()
                if (checked) {
                  updated += blockId
                } else {
                  updated -= blockId
                }
                selectedBlocks = updated
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
                      val updated = selectedTasks.toMutableSet()
                      updated.addAll(tasks)
                      selectedTasks = updated
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                  ) {
                    Text(text = stringResource(id = R.string.practice_scope_select_all_block))
                  }
                  TextButton(
                    onClick = {
                      val updated = selectedTasks.toMutableSet()
                      updated.removeAll(tasks.toSet())
                      selectedTasks = updated
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
                  Checkbox(
                    checked = selectedTasks.contains(taskId),
                    onCheckedChange = { checked ->
                      val updated = selectedTasks.toMutableSet()
                      if (checked) {
                        updated += taskId
                      } else {
                        updated -= taskId
                      }
                      selectedTasks = updated
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
            RadioButton(selected = distribution == option, onClick = { distribution = option })
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
        text = stringResource(id = R.string.practice_scope_result_size, size),
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
            if (onConfirm(scopeToSubmit)) {
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
