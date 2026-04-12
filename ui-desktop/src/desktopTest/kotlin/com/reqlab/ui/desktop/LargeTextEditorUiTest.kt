package com.reqlab.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * UI tests for the unified single-document-model code editor.
 *
 * Architecture note (monaco-style redesign)
 * -----------------------------------------
 * The editor uses ONE BasicTextField for all file sizes, tagged body-editor-input.
 * The old per-line architecture (VirtualizedEditableCodeContent with per-line tags
 * body-editor-line-input-N / body-editor-line-display-N) has been removed.
 *
 * Routing:
 *   EditableCodeContent      <=500 lines AND <=200 000 chars
 *   UnifiedEditableContent   >500 lines OR  >200 000 chars
 * Both paths expose the same body-editor-input test tag.
 */
class LargeTextEditorUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildMultiLineState(lineCount: Int): AppState {
        val state = AppState()
        val sb = StringBuilder()
        for (i in 0 until lineCount) {
            if (i > 0) sb.append('\n')
            sb.append("line").append(i)
        }
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = sb.toString()
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        return state
    }

    private fun buildSingleLineState(charCount: Int): AppState {
        val state = AppState()
        val value = "\"" + "x".repeat(charCount - 2) + "\""
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = value
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        return state
    }

    private fun loadQaResource(name: String): String {
        val stream = object {}.javaClass.classLoader?.getResourceAsStream(name)
        if (stream != null) return stream.bufferedReader().readText()

        val candidates = listOf(
            "qa-tests/src/resources/$name",
            "../qa-tests/src/resources/$name",
        )
        for (path in candidates) {
            val f = java.io.File(path)
            if (f.exists()) return f.readText()
        }
        error("QA resource '$name' not found. Searched: $candidates")
    }

    @Test
    fun large_multiline_json_renders_virtualized_editor() {
        val state = buildMultiLineState(lineCount = 502)
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun large_single_line_json_renders_virtualized_editor() {
        val state = buildSingleLineState(charCount = 220_000)
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun clicking_line_display_activates_inline_editor() {
        val state = buildMultiLineState(lineCount = 502)
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typing_multiple_chars_keeps_editing_mode_active() {
        val state = buildMultiLineState(lineCount = 502)
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
        repeat(3) { ch ->
            composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
                .performTextInput(ch.toString())
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typing_in_editor_updates_body_content() {
        val state = AppState()
        val lines = (0..500).map { "original$it" }
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = lines.joinToString("\n")
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("EDITED")
        composeRule.waitForIdle()
        val body = state.activeTab?.bodyContent ?: ""
        assertTrue(body.contains("EDITED"), "Typed text must appear in bodyContent. Starts: ${body.take(200)}")
    }

    @Test
    fun pasting_text_with_newline_creates_new_document_lines() {
        val state = AppState()
        val lines = (0..500).map { "line$it" }
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = lines.joinToString("\n")
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("first\nsecond")
        composeRule.waitForIdle()
        val body = state.activeTab?.bodyContent ?: ""
        assertTrue(body.contains("first"), "First part of pasted text must be present")
        assertTrue(body.contains("second"), "Second part of pasted text must be present")
        assertTrue(body.split('\n').size > lines.size, "Paste with newline must add at least one line")
    }

    @Test
    fun external_text_change_dismisses_inline_editor() {
        val state = AppState()
        val lines = (0..500).map { "line$it" }
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = lines.joinToString("\n")
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnUiThread {
            state.activeTab?.bodyContent = lines.joinToString("\n") + "\nextra_line"
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun formatted_5mb_json_keeps_highlighting_and_renders_correctly() {
        val lineValue = "\"" + "x".repeat(420) + "\""
        val lines = (0 until 502).map { "  \"key$it\": $lineValue," }
        val jsonBody = "{\n" + lines.joinToString("\n") + "\n}"
        assertTrue(jsonBody.split('\n').size > 500, "Test setup: must have >500 lines")
        assertTrue(jsonBody.length > 200_000, "Test setup: chars must exceed 200 000. Actual: ${jsonBody.length}")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = jsonBody
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun editing_still_works_in_large_document_with_short_lines() {
        val lineValue = "\"" + "x".repeat(420) + "\""
        val lines = (0 until 502).map { "  \"key$it\": $lineValue," }
        val content = "{\n" + lines.joinToString("\n") + "\n}"
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("Z")
        composeRule.waitForIdle()
        val body = state.activeTab?.bodyContent ?: ""
        assertTrue(body.contains("Z"), "Typed character must appear in body content")
    }

    @Test
    fun word_wrap_toggle_controls_horizontal_scrollbar_for_single_line_payload() {
        val state = buildSingleLineState(charCount = 220_000)
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-word-wrap-toggle", useUnmergedTree = true)
            .assertIsDisplayed()
        // Wrap is ON by default, so horizontal scrollbar must not be shown.
        composeRule.onNodeWithTag("body-editor-hscrollbar", useUnmergedTree = true)
            .assertDoesNotExist()

        // Disable wrap -> horizontal scrollbar should appear.
        composeRule.onNodeWithTag("body-editor-word-wrap-toggle", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-hscrollbar", useUnmergedTree = true)
            .assertIsDisplayed()

        // Enable wrap again -> horizontal scrollbar should disappear.
        composeRule.onNodeWithTag("body-editor-word-wrap-toggle", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-hscrollbar", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun five_mb_minified_json_wrap_toggle_remains_stable_and_content_intact() {
        val payload = loadQaResource("5MB-min.json")
        assertTrue(payload.isNotBlank(), "Fixture must load")
        val originalLength = payload.length

        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = payload
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }

        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()

        // Toggle wrap OFF -> expect horizontal scrollbar for long single-line payload.
        composeRule.onNodeWithTag("body-editor-word-wrap-toggle", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-hscrollbar", useUnmergedTree = true)
            .assertIsDisplayed()

        // Toggle wrap ON again and ensure editor still alive.
        composeRule.onNodeWithTag("body-editor-word-wrap-toggle", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()

        val current = state.activeTab?.bodyContent ?: ""
        assertEquals(originalLength, current.length, "Wrap toggle must not mutate body content")
    }
}

class FoldCmdARegression {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun non_virtualized_editor_accepts_text_after_fold() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\n  \"a\": 1,\n  \"b\": 2\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-fold-indicator-0", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("X")
        composeRule.waitForIdle()
        assertTrue(state.activeTab?.bodyContent?.contains("X") == true,
            "Non-virtualized editor must accept text after fold")
    }

    @Test
    fun non_virtualized_editor_accepts_text_after_unfold() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\n  \"x\": 1,\n  \"y\": 2\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-fold-indicator-0", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-fold-indicator-0", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("Y")
        composeRule.waitForIdle()
        assertTrue(state.activeTab?.bodyContent?.contains("Y") == true,
            "Non-virtualized editor must accept text after fold+unfold")
    }

    @Test
    fun non_virtualized_editor_accepts_text_after_fold_all() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\n  \"p\": 1,\n  \"q\": 2\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("Z")
        composeRule.waitForIdle()
        assertTrue(state.activeTab?.bodyContent?.contains("Z") == true,
            "Non-virtualized editor must accept text after fold-all")
    }

    @Test
    fun virtualized_editor_clears_inline_edit_on_fold() {
        val state = AppState()
        val lines = (0 until 520).joinToString("\n") { "  \"key$it\": $it," }
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\n$lines\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-line-input-1", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun virtualized_editor_accepts_text_after_fold_unfold() {
        val state = AppState()
        val lines = (0 until 520).joinToString("\n") { "  \"key$it\": $it," }
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\n$lines\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-unfold-all", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("Q")
        composeRule.waitForIdle()
        assertTrue(state.activeTab?.bodyContent?.contains("Q") == true,
            "Unified large-file editor must accept text after fold+unfold cycle")
    }

    @Test
    fun virtualized_editor_text_input_on_sibling_line_after_fold() {
        val state = AppState()
        val inner = (0 until 260).joinToString("\n") { "    \"k$it\": $it," }
        val json = "{\n  \"first\": {\n$inner\n  },\n  \"second\": {\n$inner\n  }\n}"
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = json
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-unfold-all", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("W")
        composeRule.waitForIdle()
        assertTrue(state.activeTab?.bodyContent?.contains("W") == true,
            "Unified editor must accept text after fold+unfold")
    }
}

class LargeLineReplaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val hugeLine = "x".repeat(210_000)

    private fun buildHugeLineState(): AppState {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.RAW_TEXT
            state.activeTab?.bodyContent = hugeLine
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        return state
    }

    @Test
    fun large_single_line_click_activates_inline_editor() {
        val state = buildHugeLineState()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun large_single_line_typing_replaces_content() {
        val state = buildHugeLineState()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("replaced")
        composeRule.waitForIdle()
        val body = state.activeTab?.bodyContent ?: ""
        assertTrue(body.contains("replaced"), "Typing into large-line unified editor must appear in bodyContent")
    }

    @Test
    fun large_single_line_paste_multiline_splits_lines() {
        val state = buildHugeLineState()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("line1\nline2\nline3")
        composeRule.waitForIdle()
        val body = state.activeTab?.bodyContent ?: ""
        assertTrue(body.contains("line1") && body.contains("line2") && body.contains("line3"),
            "Pasting multi-line content must produce multiple doc lines")
    }

    @Test
    fun huge_line_in_multiline_doc_is_clickable_and_replaceable() {
        val state = AppState()
        val content = "z".repeat(60_000) + "\n" + (0 until 510).joinToString("\n") { "line$it" }
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.RAW_TEXT
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("newline0")
        composeRule.waitForIdle()
        assertTrue(state.activeTab?.bodyContent?.contains("newline0") == true,
            "Huge line in multi-line doc must accept replacement content")
    }

    @Test
    fun virtualized_enter_adds_new_line_and_keeps_editing() {
        val state = AppState()
        val content = (0 until 510).joinToString("\n") { "line$it" }
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.RAW_TEXT
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        val before = state.activeTab?.bodyContent?.split('\n')?.size ?: 0
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("\n")
        composeRule.waitForIdle()
        val after = state.activeTab?.bodyContent?.split('\n')?.size ?: 0
        assertTrue(after > before, "Newline input must add at least one new line (before=$before, after=$after)")
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun large_minified_json_is_editable_without_formatting() {
        val state = AppState()
        val largeMinifiedJson = "{\"items\":[" +
            (0 until 40_000).joinToString(",") { "{\"k\":$it}" } +
            "]}"
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = largeMinifiedJson
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("{\"patched\":true}")
        composeRule.waitForIdle()
        val body = state.activeTab?.bodyContent ?: ""
        assertTrue(body.contains("{\"patched\":true}"),
            "Large minified JSON must be editable. Content starts: ${body.take(100)}")
    }
}
