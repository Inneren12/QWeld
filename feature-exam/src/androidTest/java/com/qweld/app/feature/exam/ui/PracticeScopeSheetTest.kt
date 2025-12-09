package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.vm.PracticeScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule

@RunWith(AndroidJUnit4::class)
class PracticeScopeSheetTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val blueprint = ExamBlueprint.default()
  private val tasksByBlock = blueprint.taskQuotas.groupBy { it.blockId }.mapValues { entry ->
    entry.value.map { it.taskId }
  }

  private val labeledBlocks = mapOf(
    "A" to "Block A — Safety & Tools",
    "B" to "Block B — Drawings & Measurement",
    "C" to "Block C — Materials & Metallurgy",
    "D" to "Block D — Welding processes",
  )

  private val labeledTasks = mapOf(
    "A-1" to "Shop safety",
    "D-12" to "Process fundamentals",
    "D-13" to "WPS & parameters",
    "D-14" to "Inspection & QA",
    "D-15" to "Discontinuities",
  )

  @Test
  fun practicePresetOnlyLastUsed() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        PracticeScopeSheet(
          size = 10,
          tasksByBlock = tasksByBlock,
          blockLabels = labeledBlocks,
          taskLabels = labeledTasks,
          scope = PracticeScope(),
          blueprint = blueprint,
          lastScope = UserPrefsDataStore.LastScope(
            blocks = setOf("A", "B"),
            tasks = emptySet(),
            distribution = "Proportional",
          ),
          onDismiss = {},
          onConfirm = { _, _, _ -> false },
        )
      }
    }

    composeTestRule.onNodeWithText(context.getString(R.string.practice_scope_preset_last_used)).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.practice_scope_preset_a_only)).assertDoesNotExist()
    composeTestRule.onNodeWithText(context.getString(R.string.practice_scope_preset_all)).assertDoesNotExist()
  }

  @Test
  fun practiceLabelsFallback() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        PracticeScopeSheet(
          size = 10,
          tasksByBlock = tasksByBlock,
          scope = PracticeScope(),
          blueprint = blueprint,
          lastScope = null,
          onDismiss = {},
          onConfirm = { _, _, _ -> false },
        )
      }
    }

    composeTestRule.onNodeWithText(context.getString(R.string.practice_scope_block_label, "A")).assertExists()
    composeTestRule.onNodeWithText(context.getString(R.string.practice_scope_custom_tasks)).performClick()
    composeTestRule.onNodeWithText("A-1", substring = true).assertExists()
  }

  @Test
  fun practiceScrollSafe() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        Box(modifier = Modifier.height(320.dp)) {
          PracticeScopeSheet(
            size = 20,
            tasksByBlock = tasksByBlock,
            blockLabels = labeledBlocks,
            taskLabels = labeledTasks,
            scope = PracticeScope(),
            blueprint = blueprint,
            lastScope = null,
            onDismiss = {},
            onConfirm = { _, _, _ -> false },
          )
        }
      }
    }

    composeTestRule.onNodeWithText(context.getString(R.string.practice_scope_custom_tasks)).performClick()
    composeTestRule
      .onNode(hasScrollAction())
      .performScrollToNode(hasText(context.getString(R.string.practice_scope_select_tasks)))
    composeTestRule.onNodeWithText(context.getString(R.string.practice_scope_start)).assertIsDisplayed()
  }

  @Test
  fun selectAllAndClearChangesSummary() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val totalTasks = blueprint.taskQuotas.size

    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        PracticeScopeSheet(
          size = 15,
          tasksByBlock = tasksByBlock,
          blockLabels = labeledBlocks,
          taskLabels = labeledTasks,
          scope = PracticeScope(),
          blueprint = blueprint,
          lastScope = null,
          onDismiss = {},
          onConfirm = { _, _, _ -> false },
        )
      }
    }

    composeTestRule
      .onNodeWithText(
        context.getString(R.string.practice_scope_selected_tasks, totalTasks, totalTasks),
      )
      .assertExists()

    composeTestRule.onNodeWithText(context.getString(R.string.practice_scope_clear_all)).performClick()

    composeTestRule
      .onNodeWithText(context.getString(R.string.practice_scope_selected_tasks, 0, totalTasks))
      .assertIsDisplayed()

    composeTestRule.onNodeWithText(context.getString(R.string.practice_scope_select_all_tasks)).performClick()

    composeTestRule
      .onNodeWithText(
        context.getString(R.string.practice_scope_selected_tasks, totalTasks, totalTasks),
      )
      .assertIsDisplayed()
  }
}
