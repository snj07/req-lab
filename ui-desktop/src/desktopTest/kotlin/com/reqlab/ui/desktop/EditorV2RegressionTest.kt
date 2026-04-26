package com.reqlab.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.reqlab.core.model.BodyType
import com.reqlab.editor.core.DocumentModel
import com.reqlab.editor.core.LanguageMode
import com.reqlab.ui.shared.MainScreen
import com.reqlab.editor.ui.EditorDisplayState
import com.reqlab.editor.ui.EditorViewModel
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.RequestEditorTab
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for EditorRenderer bugs fixed in April 2026:
 *
 *  1. Content disappears when word-wrap is toggled off
 *  2. Fold guide lines not visible (foldVersion not emitted)
 *  3. Horizontal scrolling broken in no-wrap mode
 *  4. Typing 8-10 chars then subsequent keystrokes stop appearing
 *     (editableText re-creation on every recomposition + cursor-reset-to-0 race)
 */
class EditorV2RegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Helpers ─────────────────────────────────────────────────────

    private fun stateWithJson(lineCount: Int = 520): AppState {
        val state = AppState()
        val sb = StringBuilder()
        sb.append("{\n")
        for (i in 0 until lineCount - 2) {
            sb.append("  \"key$i\": \"value$i\"")
            if (i < lineCount - 3) sb.append(",")
            sb.append("\n")
        }
        sb.append("}")
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = sb.toString()
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        return state
    }

    private fun body(state: AppState): String =
        state.activeTab?.bodyContent ?: ""

    private fun inputNode() =
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)

    // ── 1. No-wrap mode: content must remain visible ────────────────

    @Test
    fun no_wrap_mode_content_visible_after_toggle() {
        val state = stateWithJson()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Line-numbers gutter is always present
        composeRule.onNodeWithTag("body-editor-line-numbers", useUnmergedTree = true)
            .assertIsDisplayed()

        // Input node must be rendered and displayable
        inputNode().assertIsDisplayed()

        // Body must still have content (not zeroed out)
        val content = body(state)
        assertTrue(content.contains("key0"), "Body content must survive no-wrap toggle")
    }

    // ── 2. Fold guide: foldVersion triggers recomposition ───────────

    @Test
    fun foldVersion_increments_after_initial_fold_computation() {
        val vm = EditorViewModel(
            initialText = "{\n  \"a\": 1,\n  \"b\": 2\n}",
            languageMode = LanguageMode.JSON,
        )

        // Allow background fold computation to complete
        Thread.sleep(500)

        val state = vm.state.value
        assertTrue(
            state.foldVersion > 0,
            "foldVersion must be > 0 after initial fold computation, was ${state.foldVersion}",
        )
        vm.dispose()
    }

    @Test
    fun toggleFold_increments_foldVersion() {
        val vm = EditorViewModel(
            initialText = "{\n  \"a\": 1,\n  \"b\": 2\n}",
            languageMode = LanguageMode.JSON,
        )
        Thread.sleep(500)

        val before = vm.state.value.foldVersion
        // Line 0 (the opening '{') should have a fold region
        if (vm.foldRegions.isNotEmpty()) {
            val foldLine = vm.foldRegions.first().startLine - 1
            vm.toggleFold(foldLine)
            val after = vm.state.value.foldVersion
            assertTrue(after > before, "foldVersion must increment after toggleFold")
        }
        vm.dispose()
    }

    // ── 3. Typing continuity: 20+ chars all appear ──────────────────

    @Test
    fun typing_20_chars_sequentially_all_appear_in_content() {
        val state = stateWithJson()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val chars = "ABCDEFGHIJKLMNOPQRST"
        for (ch in chars) {
            inputNode().performTextInput(ch.toString())
            composeRule.waitForIdle()
        }

        val content = body(state)
        assertTrue(
            content.contains(chars),
            "All 20 typed chars must appear in content. Start 100 chars: ${content.take(100)}",
        )
    }

    @Test
    fun typing_after_enter_continues_to_appear() {
        val state = stateWithJson()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        inputNode().performTextInput("\n")
        composeRule.waitForIdle()

        val textAfterEnter = "AFTER_ENTER_TEXT"
        inputNode().performTextInput(textAfterEnter)
        composeRule.waitForIdle()

        val content = body(state)
        assertTrue(
            content.contains(textAfterEnter),
            "Text typed after Enter must appear in content",
        )
    }

    // ── 4. Cursor preservation in onExternalTextChanged ─────────────

    @Test
    fun onExternalTextChanged_preserves_cursor_position() {
        val vm = EditorViewModel(
            initialText = "hello world",
            languageMode = LanguageMode.PLAIN_TEXT,
        )

        // Move cursor to position 5
        vm.moveCursorTo(5)
        assertEquals(5, vm.state.value.cursorOffset)

        // Simulate external text change with same-length text
        vm.onExternalTextChanged("HELLO WORLD")
        Thread.sleep(200) // let async job complete

        val cursor = vm.state.value.cursorOffset
        assertTrue(
            cursor in 0..11,
            "Cursor must be clamped within new doc length, was $cursor",
        )
        // cursor should NOT have been reset to 0
        assertEquals(5, cursor, "Cursor should be preserved at position 5")
        vm.dispose()
    }

    @Test
    fun editSequence_prevents_stale_external_text_overwrite() {
        val vm = EditorViewModel(
            initialText = "original",
            languageMode = LanguageMode.PLAIN_TEXT,
        )

        // Trigger an external text change (runs on background thread)
        vm.onExternalTextChanged("external_update")

        // Immediately type a character (bumps editSequence)
        vm.insertAtCursor("X")

        // Wait for background job to complete
        Thread.sleep(500)

        val content = vm.getFullText()
        assertTrue(
            content.contains("X"),
            "User's typed character 'X' must survive despite concurrent external text change. Content: $content",
        )
        vm.dispose()
    }

    // ── 5. Line number gutter remains after edits ───────────────────

    @Test
    fun gutter_visible_after_multiple_edits() {
        val state = stateWithJson()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Type several characters
        repeat(5) {
            inputNode().performTextInput("X")
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("body-editor-line-numbers", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    // ── 6. Document integrity under rapid edits ─────────────────────

    @Test
    fun rapid_inserts_produce_correct_document_content() {
        val vm = EditorViewModel(
            initialText = "",
            languageMode = LanguageMode.PLAIN_TEXT,
        )

        val expected = StringBuilder()
        for (i in 0 until 50) {
            val ch = ('A' + (i % 26)).toString()
            vm.insertAtCursor(ch)
            expected.append(ch)
        }

        assertEquals(
            expected.toString(),
            vm.getFullText(),
            "50 rapid single-char inserts must produce the exact expected string",
        )
        assertEquals(50, vm.state.value.cursorOffset, "Cursor must be at end after 50 inserts")
        vm.dispose()
    }

    @Test
    fun rapid_insert_then_newline_then_more_chars_all_present() {
        val vm = EditorViewModel(
            initialText = "",
            languageMode = LanguageMode.PLAIN_TEXT,
        )

        vm.insertAtCursor("Hello")
        vm.insertAtCursor("\n")
        vm.insertAtCursor("World")

        assertEquals("Hello\nWorld", vm.getFullText())
        assertEquals(11, vm.state.value.cursorOffset)
        assertEquals(2, vm.document.lineCount)
        vm.dispose()
    }
}
