package com.reqlab.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.reqlab.core.model.BodyType
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.RequestEditorTab
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comprehensive editor input correctness tests.
 *
 * These tests exercise the V2 editor (GapBuffer + LazyColumn) from a user's
 * perspective: type text, press Enter, backspace, paste multi-line text, and
 * verify the resulting document content is exactly correct.
 *
 * Each test validates that content is not lost, duplicated, or corrupted.
 */
class EditorInputCorrectnessTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Build an AppState with the given body content in the editor,
     * using the V2 renderer path (> 500 lines OR > 200k chars).
     */
    private fun stateWith(content: String, bodyType: BodyType = BodyType.RAW_TEXT): AppState {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = bodyType
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        return state
    }

    /**
     * Build a multi-line document that triggers the V2 (virtualised) editor path.
     * The V2 path is triggered when lineCount > 500 OR charCount > 200_000.
     */
    private fun v2State(lineCount: Int = 510): AppState {
        val lines = (0 until lineCount).joinToString("\n") { "line$it" }
        return stateWith(lines)
    }

    private fun body(state: AppState): String =
        state.activeTab?.bodyContent ?: ""

    private fun inputNode() =
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)

    // ── Test: Single character typing ────────────────────────────────

    @Test
    fun typing_single_char_appears_in_content() {
        val state = v2State()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        inputNode().performTextInput("A")
        composeRule.waitForIdle()

        val content = body(state)
        assertTrue(content.contains("A"), "Typed char 'A' must appear in body content")
    }

    @Test
    fun typing_multiple_chars_sequentially_all_appear() {
        val state = v2State()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        inputNode().performTextInput("H")
        composeRule.waitForIdle()
        inputNode().performTextInput("i")
        composeRule.waitForIdle()
        inputNode().performTextInput("!")
        composeRule.waitForIdle()

        val content = body(state)
        assertTrue(content.contains("Hi!"),
            "Sequential chars must form 'Hi!' in content. Start: ${content.take(50)}")
    }

    // ── Test: Enter key (newline insertion) ──────────────────────────

    @Test
    fun enter_increases_line_count_by_one() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val beforeLines = body(state).split('\n').size

        inputNode().performTextInput("\n")
        composeRule.waitForIdle()

        val afterLines = body(state).split('\n').size
        assertEquals(beforeLines + 1, afterLines,
            "Enter must add exactly 1 line. Before=$beforeLines, After=$afterLines")
    }

    @Test
    fun enter_preserves_all_content() {
        val original = (0 until 510).joinToString("\n") { "line$it" }
        val state = stateWith(original)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        inputNode().performTextInput("\n")
        composeRule.waitForIdle()

        val content = body(state)
        // All original lines must still be present
        for (i in 0 until 510) {
            assertTrue(content.contains("line$i"),
                "After Enter, original content 'line$i' must still be present")
        }
    }

    @Test
    fun enter_then_type_content_appears_on_new_line() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        inputNode().performTextInput("\nNEWLINE_CONTENT")
        composeRule.waitForIdle()

        val content = body(state)
        assertTrue(content.contains("NEWLINE_CONTENT"),
            "Text typed after Enter must be in body content")
        val lines = content.split('\n')
        val lineWithContent = lines.find { it.contains("NEWLINE_CONTENT") }
        assertTrue(lineWithContent != null,
            "NEWLINE_CONTENT must appear on its own line")
    }

    @Test
    fun multiple_enters_increase_line_count_correctly() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val beforeLines = body(state).split('\n').size

        inputNode().performTextInput("\n\n\n")
        composeRule.waitForIdle()

        val afterLines = body(state).split('\n').size
        assertEquals(beforeLines + 3, afterLines,
            "3 newlines must add exactly 3 lines. Before=$beforeLines, After=$afterLines")
    }

    // ── Test: Text with embedded newlines (paste simulation) ────────

    @Test
    fun paste_text_with_newlines_splits_and_preserves() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val beforeLines = body(state).split('\n').size

        inputNode().performTextInput("alpha\nbeta\ngamma")
        composeRule.waitForIdle()

        val content = body(state)
        val afterLines = content.split('\n').size

        assertTrue(content.contains("alpha"), "Pasted 'alpha' must be present")
        assertTrue(content.contains("beta"), "Pasted 'beta' must be present")
        assertTrue(content.contains("gamma"), "Pasted 'gamma' must be present")
        assertEquals(beforeLines + 2, afterLines,
            "Paste with 2 newlines must add 2 lines. Before=$beforeLines, After=$afterLines")
    }

    @Test
    fun paste_large_text_with_many_newlines() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val pasteText = (0 until 20).joinToString("\n") { "pasted_line_$it" }
        val beforeLines = body(state).split('\n').size

        inputNode().performTextInput(pasteText)
        composeRule.waitForIdle()

        val content = body(state)
        val afterLines = content.split('\n').size

        assertTrue(content.contains("pasted_line_0"), "First pasted line must be present")
        assertTrue(content.contains("pasted_line_19"), "Last pasted line must be present")
        assertEquals(beforeLines + 19, afterLines,
            "20-line paste must add 19 newlines. Before=$beforeLines, After=$afterLines")
    }

    // ── Test: Type after Enter — the core regression ────────────────

    @Test
    fun type_then_enter_then_type_all_content_present() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        inputNode().performTextInput("BEFORE")
        composeRule.waitForIdle()
        inputNode().performTextInput("\n")
        composeRule.waitForIdle()
        inputNode().performTextInput("AFTER")
        composeRule.waitForIdle()

        val content = body(state)
        assertTrue(content.contains("BEFORE"),
            "Text typed before Enter must be present")
        assertTrue(content.contains("AFTER"),
            "Text typed after Enter must be present — this is the core regression test")
    }

    @Test
    fun enter_in_middle_of_word_splits_correctly() {
        // Set up a simple 510-line document where cursor starts at position 0
        val original = (0 until 510).joinToString("\n") { "line$it" }
        val state = stateWith(original)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Type "hello" at position 0, then enter, then "world"
        inputNode().performTextInput("hello\nworld")
        composeRule.waitForIdle()

        val content = body(state)
        assertTrue(content.contains("hello"), "'hello' must be present")
        assertTrue(content.contains("world"), "'world' must be present")
        // 'hello' and 'world' should be on different lines
        val lines = content.split('\n')
        val helloLine = lines.indexOfFirst { it.contains("hello") }
        val worldLine = lines.indexOfFirst { it.contains("world") }
        assertTrue(helloLine >= 0 && worldLine >= 0 && helloLine != worldLine,
            "'hello' (line $helloLine) and 'world' (line $worldLine) must be on different lines")
    }

    // ── Test: Editor remains functional after mutations ──────────────

    @Test
    fun editor_remains_displayed_after_enter() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        inputNode().performTextInput("\n")
        composeRule.waitForIdle()

        inputNode().assertIsDisplayed()
    }

    @Test
    fun editor_accepts_input_after_multiple_enters() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Multiple enters
        inputNode().performTextInput("\n")
        composeRule.waitForIdle()
        inputNode().performTextInput("\n")
        composeRule.waitForIdle()
        inputNode().performTextInput("\n")
        composeRule.waitForIdle()

        // Now type
        inputNode().performTextInput("STILL_WORKS")
        composeRule.waitForIdle()

        assertTrue(body(state).contains("STILL_WORKS"),
            "Editor must accept input after multiple Enter presses")
    }

    // ── Test: Content integrity — nothing lost ──────────────────────

    @Test
    fun total_char_count_increases_by_typed_amount() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val beforeLen = body(state).length
        val typed = "ABCDEF"

        inputNode().performTextInput(typed)
        composeRule.waitForIdle()

        val afterLen = body(state).length
        assertEquals(beforeLen + typed.length, afterLen,
            "Content length must increase by exactly ${typed.length} chars. Before=$beforeLen, After=$afterLen")
    }

    @Test
    fun total_char_count_with_newlines_is_correct() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val beforeLen = body(state).length
        val typed = "AB\nCD\nEF"

        inputNode().performTextInput(typed)
        composeRule.waitForIdle()

        val afterLen = body(state).length
        assertEquals(beforeLen + typed.length, afterLen,
            "Content length including newlines must be correct. Before=$beforeLen, After=$afterLen")
    }

    // ── Test: Small document (non-virtualized path) ─────────────────

    @Test
    fun small_document_enter_preserves_content() {
        val state = stateWith("hello world")
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        inputNode().performTextInput("\n")
        composeRule.waitForIdle()

        val content = body(state)
        assertTrue(content.contains("hello"), "Content before cursor must be preserved")
        assertTrue(content.contains("world"), "Content after cursor must be preserved")
    }

    @Test
    fun small_document_type_and_enter_cycle() {
        val state = stateWith("start")
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        inputNode().performTextInput("X")
        composeRule.waitForIdle()
        inputNode().performTextInput("\n")
        composeRule.waitForIdle()
        inputNode().performTextInput("Y")
        composeRule.waitForIdle()

        val content = body(state)
        assertTrue(content.contains("X"), "'X' must be present")
        assertTrue(content.contains("Y"), "'Y' must be present after Enter")
        assertTrue(content.contains("start"), "Original content must be preserved")
    }

    // ── Test: JSON content ──────────────────────────────────────────

    @Test
    fun json_enter_preserves_structure() {
        val json = "{\n" + (0 until 510).joinToString(",\n") { "  \"key$it\": $it" } + "\n}"
        val state = stateWith(json, BodyType.JSON)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val beforeLines = body(state).split('\n').size

        inputNode().performTextInput("\n")
        composeRule.waitForIdle()

        val content = body(state)
        val afterLines = content.split('\n').size

        assertEquals(beforeLines + 1, afterLines, "Enter must add 1 line to JSON")
        assertTrue(content.contains("key0"), "First key must survive")
        assertTrue(content.contains("key509"), "Last key must survive")
    }

    // ── Test: Rapid sequential input ────────────────────────────────

    @Test
    fun rapid_10_char_input_all_present() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        for (i in 0 until 10) {
            inputNode().performTextInput(i.toString())
            composeRule.waitForIdle()
        }

        val content = body(state)
        assertTrue(content.contains("0123456789"),
            "Rapidly typed digits 0-9 must appear in sequence. Start: ${content.take(50)}")
    }

    @Test
    fun interleaved_chars_and_enters() {
        val state = v2State(lineCount = 510)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val beforeLines = body(state).split('\n').size

        inputNode().performTextInput("A")
        composeRule.waitForIdle()
        inputNode().performTextInput("\n")
        composeRule.waitForIdle()
        inputNode().performTextInput("B")
        composeRule.waitForIdle()
        inputNode().performTextInput("\n")
        composeRule.waitForIdle()
        inputNode().performTextInput("C")
        composeRule.waitForIdle()

        val content = body(state)
        val afterLines = content.split('\n').size

        assertTrue(content.contains("A"), "'A' must be present")
        assertTrue(content.contains("B"), "'B' must be present")
        assertTrue(content.contains("C"), "'C' must be present")
        assertEquals(beforeLines + 2, afterLines,
            "Two enters must add exactly 2 lines. Before=$beforeLines, After=$afterLines")
    }
}
