package com.reqlab.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorRenderer
import com.reqlab.editor.ui.EditorViewModel
import org.junit.Rule
import org.junit.Test

class EditorLargePasteScrollUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Regression test: pasting a normal-sized (< 100 K chars) multi-line JSON into an
     * empty editor must NOT auto-scroll the viewport to the end of the inserted text.
     *
     * Bug: after paste the cursor lands at the last character → LaunchedEffect(cursorOffset)
     * in EditorRenderer scrolled the LazyColumn there → line-number-0 disappeared.
     *
     * The fix extends the existing large-edit scroll suppression to also fire when ≥ 2 new
     * lines are inserted in a single edit, covering all realistic paste operations.
     */
    @Test
    fun paste_into_empty_editor_keeps_first_line_visible() {
        val vm = EditorViewModel("", LanguageMode.JSON)

        composeRule.setContent {
            Box(Modifier.size(800.dp, 300.dp)) {
                EditorRenderer(
                    viewModel = vm,
                    isReadOnly = false,
                    language = LanguageMode.JSON,
                    wordWrap = true,
                    testTagPrefix = "paste_small",
                )
            }
        }
        composeRule.waitForIdle()

        // Sanity: line 0 visible in empty editor.
        composeRule.onNodeWithTag("paste_small-line-number-0", useUnmergedTree = true).assertIsDisplayed()

        // Paste a realistic 15-line JSON — well below the 100K char threshold that
        // previously was the only guard against auto-scroll.
        val json = buildString {
            appendLine("{")
            appendLine("  \"name\": \"ReqLab\",")
            appendLine("  \"version\": \"1.0\",")
            appendLine("  \"description\": \"API Testing Tool\",")
            appendLine("  \"items\": [")
            appendLine("    {\"id\": 1, \"label\": \"first\"},")
            appendLine("    {\"id\": 2, \"label\": \"second\"},")
            appendLine("    {\"id\": 3, \"label\": \"third\"},")
            appendLine("    {\"id\": 4, \"label\": \"fourth\"},")
            appendLine("    {\"id\": 5, \"label\": \"fifth\"}")
            appendLine("  ],")
            appendLine("  \"active\": true,")
            appendLine("  \"count\": 5,")
            appendLine("  \"tags\": [\"json\", \"test\", \"paste\"]")
            append("}")
        }

        composeRule.runOnUiThread { vm.insertAtCursor(json) }
        composeRule.waitForIdle()

        // After paste, the first line must still be in the viewport (no scroll-to-end).
        composeRule.onNodeWithTag("paste_small-line-number-0", useUnmergedTree = true).assertIsDisplayed()
        vm.dispose()
    }

    /**
     * Regression test: gutter width must not change when the line count crosses a
     * digit boundary (e.g. 9 → 10 lines).  Previously the gutter was sized from
     * lineCount.toString().length with no minimum, so it jumped wider at line 10,
     * shifting both line numbers and content to the right.
     *
     * The fix: always allocate gutterWidth for at least 2 digits.
     */
    @Test
    fun gutter_width_does_not_shift_at_line_10_boundary() {
        // Start with exactly 9 lines (the last single-digit line number)
        val nineLines = (1..9).joinToString("\n") { "line$it" }
        val vm = EditorViewModel(nineLines, LanguageMode.JSON)

        var gutterWidthBefore = 0f
        var gutterWidthAfter  = 0f

        composeRule.setContent {
            Box(Modifier.size(800.dp, 400.dp)) {
                EditorRenderer(
                    viewModel = vm,
                    isReadOnly = false,
                    language = LanguageMode.JSON,
                    wordWrap = true,
                    testTagPrefix = "gutter_shift",
                )
            }
        }
        composeRule.waitForIdle()

        // Capture the on-screen x-position of line-number-0 before adding a 10th line.
        val nodeBefore = composeRule
            .onNodeWithTag("gutter_shift-line-number-0", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        gutterWidthBefore = nodeBefore.boundsInRoot.right

        // Add a 10th line → line count crosses 1-digit → 2-digit boundary.
        composeRule.runOnUiThread { vm.insertAtCursor("\nline10") }
        composeRule.waitForIdle()

        val nodeAfter = composeRule
            .onNodeWithTag("gutter_shift-line-number-0", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        gutterWidthAfter = nodeAfter.boundsInRoot.right

        // The right-edge of line-number-0 (= the gutter/content divider x position)
        // must not have moved when crossing from single-digit to double-digit line count.
        kotlin.test.assertEquals(
            gutterWidthBefore, gutterWidthAfter,
            "Gutter width shifted at 9→10 line boundary (before=$gutterWidthBefore, after=$gutterWidthAfter)",
        )
        vm.dispose()
    }

        @Test
    fun large_paste_does_not_auto_scroll_to_bottom() {
        val vm = EditorViewModel("", LanguageMode.JSON)

        composeRule.setContent {
            Box(Modifier.size(800.dp, 260.dp)) {
                EditorRenderer(
                    viewModel = vm,
                    isReadOnly = false,
                    language = LanguageMode.JSON,
                    wordWrap = true,
                    testTagPrefix = "paste_scroll",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("paste_scroll-line-number-0", useUnmergedTree = true).assertIsDisplayed()

        val largeJson = buildString(220_000) {
            append('{')
            append("\"items\":[")
            repeat(15_000) { idx ->
                append("{\"id\":")
                append(idx)
                append(",\"name\":\"item-")
                append(idx)
                append("\"},")
            }
            append("{}]}")
        }

        composeRule.runOnUiThread {
            vm.insertAtCursor(largeJson)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("paste_scroll-line-number-0", useUnmergedTree = true).assertIsDisplayed()
        vm.dispose()
    }
}
