@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.reqlab.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorRenderer
import com.reqlab.editor.ui.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression: switching the editor from an unedited document (version 0) to another
 * reused LazyColumn row `"row-0"` and LineView `remember(version, docLine)`, so the
 * previous tab's text stayed on screen. Clicks could also target the previous VM.
 */
class EditorTabSwitchLeakTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switching_unedited_viewmodels_does_not_keep_previous_tab_text() {
        val textA = """{"from":"tab-A-only"}"""
        val textB = """{"from":"tab-B-only"}"""
        val vmA = EditorViewModel(textA, LanguageMode.JSON)
        val vmB = EditorViewModel(textB, LanguageMode.JSON)
        assertEquals(0, vmA.state.value.version, "fixture: A must be unedited")
        assertEquals(0, vmB.state.value.version, "fixture: B must be unedited")

        var active by mutableStateOf(vmA)
        composeRule.setContent {
            Box(Modifier.size(600.dp, 120.dp)) {
                EditorRenderer(
                    viewModel = active,
                    isReadOnly = false,
                    language = LanguageMode.JSON,
                    testTagPrefix = "tabswitch",
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("tab-A-only", substring = true)).assertExists()

        composeRule.runOnUiThread { active = vmB }
        composeRule.waitForIdle()

        composeRule.onNode(hasContentDescription("tab-B-only", substring = true)).assertExists()
        composeRule.onNode(hasContentDescription("tab-A-only", substring = true)).assertDoesNotExist()

        vmA.dispose()
        vmB.dispose()
    }

    @Test
    fun switching_single_line_to_multiline_does_not_keep_line_zero() {
        val textA = """{"from":"tab-A-only"}"""
        val textB = """{"from":"tab-B-only"}
{"second":true}"""
        val vmA = EditorViewModel(textA, LanguageMode.JSON)
        val vmB = EditorViewModel(textB, LanguageMode.JSON)
        assertEquals(0, vmA.state.value.version)
        assertEquals(0, vmB.state.value.version)

        var active by mutableStateOf(vmA)
        composeRule.setContent {
            Box(Modifier.size(600.dp, 160.dp)) {
                EditorRenderer(
                    viewModel = active,
                    isReadOnly = false,
                    language = LanguageMode.JSON,
                    testTagPrefix = "tabswitch_ml",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { active = vmB }
        composeRule.waitForIdle()

        composeRule.onNode(hasContentDescription("tab-B-only", substring = true)).assertExists()
        composeRule.onNode(hasContentDescription("tab-A-only", substring = true)).assertDoesNotExist()
        composeRule.onNode(hasContentDescription("second", substring = true)).assertExists()

        vmA.dispose()
        vmB.dispose()
    }

    @Test
    fun click_after_tab_switch_moves_cursor_on_new_viewmodel() {
        val textA = """{"from":"tab-A-only"}"""
        val textB = """{"from":"tab-B-only-XXXX"}"""
        val vmA = EditorViewModel(textA, LanguageMode.JSON)
        val vmB = EditorViewModel(textB, LanguageMode.JSON)

        var active by mutableStateOf(vmA)
        composeRule.setContent {
            Box(Modifier.size(600.dp, 120.dp)) {
                EditorRenderer(
                    viewModel = active,
                    isReadOnly = false,
                    language = LanguageMode.JSON,
                    testTagPrefix = "tabswitch_click",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { active = vmB }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { vmB.moveCursorTo(0) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tabswitch_click-line-numbers")
            .performTouchInput {
                click(Offset(220.dp.toPx(), 12f))
            }
        composeRule.waitForIdle()

        val cursor = vmB.state.value.cursorOffset
        assertTrue(
            cursor in 1..textB.length,
            "Click on B must move cursor into the visible line, got $cursor",
        )
        assertEquals(
            0,
            vmA.state.value.cursorOffset,
            "Click after switch must not move the previous tab's cursor",
        )

        vmA.dispose()
        vmB.dispose()
    }

    @Test
    fun switching_viewmodels_resets_vertical_scroll_on_new_tab() {
        val vmA = EditorViewModel((0..80).joinToString("\n") { "A-line-$it" }, LanguageMode.PLAIN_TEXT)
        val vmB = EditorViewModel("B-only", LanguageMode.PLAIN_TEXT)
        var active by mutableStateOf(vmA)
        val listByVm = mutableMapOf<EditorViewModel, LazyListState>()

        composeRule.setContent {
            Box(Modifier.size(400.dp, 160.dp)) {
                EditorRenderer(
                    viewModel = active,
                    isReadOnly = false,
                    language = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "tabscroll",
                    onListStateReady = { state -> listByVm[active] = state },
                )
            }
        }
        composeRule.waitForIdle()

        val listA = requireNotNull(listByVm[vmA])
        runBlocking { listA.scrollToItem(40) }
        composeRule.waitForIdle()
        assertTrue(listA.firstVisibleItemIndex >= 20, "fixture: A must be scrolled")

        composeRule.runOnUiThread { active = vmB }
        composeRule.waitForIdle()

        val listB = requireNotNull(listByVm[vmB])
        assertEquals(0, listB.firstVisibleItemIndex, "New tab must start at the top")

        composeRule.runOnUiThread { active = vmA }
        composeRule.waitForIdle()
        val restoredA = requireNotNull(listByVm[vmA])
        assertTrue(
            restoredA.firstVisibleItemIndex >= 20,
            "Switching back to A must keep A's scroll position, got ${restoredA.firstVisibleItemIndex}",
        )

        vmA.dispose()
        vmB.dispose()
    }
}
