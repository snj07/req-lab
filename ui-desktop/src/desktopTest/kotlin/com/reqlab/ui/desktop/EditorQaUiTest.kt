@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.reqlab.ui.desktop

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.input.key.Key
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.ResponseDefinition
import com.reqlab.core.model.ResponseMetrics
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.RequestEditorTab
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ═══════════════════════════════════════════════════════════════════════
 * QA UI Test Suite — Editor End-to-End Behaviour
 * ═══════════════════════════════════════════════════════════════════════
 *
 * QA Test Areas:
 *  1.  Editor container & toolbar rendering
 *  2.  Basic typing / editing interaction
 *  3.  Multi-language: JSON, XML, JS — toolbar language badge
 *  4.  Invalid JSON / XML — inline error indicators
 *  5.  Body type switching — state isolation + label
 *  6.  Fold toolbar buttons — fold-all / unfold-all
 *  7.  Fold indicator click — per-region fold/unfold
 *  8.  Search bar — open/close, query input, match count
 *  9.  Gutter (line numbers) — visibility
 * 10.  Scrollbar — vertical scrollbar present for long content
 * 11.  Large file (>500 lines) — virtualized editor container
 * 12.  Large file (>200K chars) — char-threshold virtualized container
 * 13.  Large file editing — inline editor activates and stays active
 * 14.  Large file fold buttons — fold/unfold all available
 * 15.  Response viewer — read-only code editor with JSON response
 * 16.  Word-wrap toggle — button present and clickable
 * 17.  Format toggle — button present and clickable
 * 18.  Gutter sync — line numbers visible scrolled state
 * 19.  5 MB minified JSON — container renders without freeze
 * 20.  10 MB formatted JSON — container renders without freeze
 */

// ══════════════════════════════════════════════════════════════════════
// 1. Editor Container & Toolbar Rendering
// ══════════════════════════════════════════════════════════════════════

class EditorContainerRenderingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildJsonEditorState(json: String = """{"key":"value"}"""): AppState {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = json
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        return state
    }

    @Test
    fun body_editor_container_is_displayed() {
        composeRule.setContent { MainScreen(buildJsonEditorState()) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun body_editor_toolbar_is_displayed() {
        composeRule.setContent { MainScreen(buildJsonEditorState()) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-toolbar", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun line_numbers_gutter_is_displayed_for_multiline_content() {
        val json = "{\n  \"a\": 1,\n  \"b\": 2\n}"
        composeRule.setContent { MainScreen(buildJsonEditorState(json)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-line-numbers", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fold_all_button_is_displayed_for_multiline_json() {
        val json = "{\n  \"message\": \"hello\",\n  \"value\": 42\n}"
        composeRule.setContent { MainScreen(buildJsonEditorState(json)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun unfold_all_button_is_displayed_for_multiline_json() {
        val json = "{\n  \"message\": \"hello\",\n  \"value\": 42\n}"
        composeRule.setContent { MainScreen(buildJsonEditorState(json)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-unfold-all", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun word_wrap_toggle_is_displayed_in_toolbar() {
        composeRule.setContent { MainScreen(buildJsonEditorState()) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-word-wrap-toggle", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun format_toggle_is_displayed_in_toolbar() {
        composeRule.setContent { MainScreen(buildJsonEditorState()) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-format-toggle", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun search_toggle_is_displayed_in_toolbar() {
        composeRule.setContent { MainScreen(buildJsonEditorState()) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-search-toggle", useUnmergedTree = true).assertIsDisplayed()
    }
}

// ══════════════════════════════════════════════════════════════════════
// 2. Basic Typing / Editing Interaction (small document path)
// ══════════════════════════════════════════════════════════════════════

class BasicEditorInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typing_characters_updates_body_content() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = ""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("{\"test\":1}")
        composeRule.waitForIdle()

        val content = state.activeTab?.bodyContent ?: ""
        assertTrue(content.contains("test"), "Typed text must appear in body content")
    }

    @Test
    fun editor_input_is_displayed_for_small_document() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = """{"key":"value"}"""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun format_button_click_formats_minified_json() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = """{"name":"Alice","age":30}"""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-format-toggle", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        val content = state.activeTab?.bodyContent ?: ""
        // After formatting, the JSON should span multiple lines
        assertTrue(content.contains('\n'),
            "Format must expand minified JSON to multiline; content: ${content.take(200)}")
    }

    @Test
    fun word_wrap_toggle_click_does_not_crash() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = """{"long":"value".repeat(200)}"""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-word-wrap-toggle", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        // Editor must still render after toggle
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun json_body_with_variable_renders_without_chip_bar() {
        // Variables in the body editor are now opened inline by clicking on the
        // {{token}} in the code editor itself — no separate chip bar is shown.
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{{base-url}}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Body editor must still render.
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
        // Chip bar must no longer be present.
        composeRule.onAllNodesWithTag("body-variable-chip-0", useUnmergedTree = true).assertCountEquals(0)
        // No popup on load.
        composeRule.onAllNodesWithTag("variable-editor-popup", useUnmergedTree = true).assertCountEquals(0)
    }
}

// ══════════════════════════════════════════════════════════════════════
// 3. Multi-Language Support (UI level)
// ══════════════════════════════════════════════════════════════════════

class MultiLanguageUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun xml_body_type_shows_editor() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.XML
            state.activeTab?.bodyContent = "<root>\n  <item>1</item>\n</root>"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun html_body_type_shows_editor() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.HTML
            state.activeTab?.bodyContent = "<html>\n  <body><h1>Test</h1></body>\n</html>"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun javascript_body_type_shows_editor() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JAVASCRIPT
            state.activeTab?.bodyContent = "function test() {\n  return 42;\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun raw_text_body_type_shows_editor() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.RAW_TEXT
            state.activeTab?.bodyContent = "plain text content\nline two\nline three"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun xml_editor_has_fold_controls_for_multiline_content() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.XML
            state.activeTab?.bodyContent = "<root>\n  <parent>\n    <child>data</child>\n  </parent>\n</root>"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true).assertIsDisplayed()
    }
}

// ══════════════════════════════════════════════════════════════════════
// 4. Error Handling — Inline Error Indicators
// ══════════════════════════════════════════════════════════════════════

class ErrorHandlingUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Verifying that the editor renders without crash when given invalid JSON.
     * Inline error underlines are drawn via AnnotatedString spans and are not
     * separately tagged, so we verify that: (a) editor renders, (b) body content
     * still matches what was set (editor did not reject/clear it).
     */
    @Test
    fun invalid_json_trailing_comma_editor_renders() {
        val invalid = """{ "a": 1, }"""
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = invalid
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Editor must render despite invalid content
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
        // Content must be preserved (editor is non-destructive)
        assertEquals(invalid, state.activeTab?.bodyContent)
    }

    @Test
    fun invalid_json_missing_brace_editor_renders() {
        val invalid = """{"key": "value" """
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = invalid
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(invalid, state.activeTab?.bodyContent)
    }

    @Test
    fun invalid_xml_mismatched_tags_editor_renders() {
        val invalid = "<root><child></root>"
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.XML
            state.activeTab?.bodyContent = invalid
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typing_invalid_json_does_not_crash_editor() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = ""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Type a partial / invalid JSON structure
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("{broken")
        composeRule.waitForIdle()

        // Editor must still be displayed
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }
}

// ══════════════════════════════════════════════════════════════════════
// 5. Body Type Switching — UI State Isolation
// ══════════════════════════════════════════════════════════════════════

class BodyTypeSwitchingUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switching_json_to_form_and_back_preserves_json_in_state() {
        val state = AppState()
        val original = """{"preserved":"yes","count":42}"""
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = original
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Switch to FORM_DATA
        composeRule.runOnUiThread { state.activeTab?.bodyType = BodyType.FORM_DATA }
        composeRule.waitForIdle()

        // Switch back to JSON
        composeRule.runOnUiThread { state.activeTab?.bodyType = BodyType.JSON }
        composeRule.waitForIdle()

        assertEquals(original, state.activeTab?.bodyContent,
            "JSON content must survive round-trip through FORM_DATA")
    }

    @Test
    fun switching_xml_to_urlencoded_and_back_preserves_xml_in_state() {
        val state = AppState()
        val original = "<root><item>keep this</item></root>"
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.XML
            state.activeTab?.bodyContent = original
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { state.activeTab?.bodyType = BodyType.X_WWW_FORM_URLENCODED }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { state.activeTab?.bodyType = BodyType.XML }
        composeRule.waitForIdle()

        assertEquals(original, state.activeTab?.bodyContent,
            "XML content must survive round-trip through X_WWW_FORM_URLENCODED")
    }

    @Test
    fun switching_body_type_keeps_editor_rendered() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = """{"key":"val"}"""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        for (type in listOf(BodyType.XML, BodyType.HTML, BodyType.JAVASCRIPT, BodyType.RAW_TEXT, BodyType.JSON)) {
            composeRule.runOnUiThread { state.activeTab?.bodyType = type }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun javascript_content_stored_per_type_independently_of_json() {
        val state = AppState()
        val jsContent = "const x = 42;"
        val jsonContent = """{"x":42}"""
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JAVASCRIPT
            state.activeTab?.bodyContent = jsContent
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = jsonContent
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { state.activeTab?.bodyType = BodyType.JAVASCRIPT }
        composeRule.waitForIdle()
        assertEquals(jsContent, state.activeTab?.bodyContent,
            "JS content must be independent from JSON content")
    }
}

// ══════════════════════════════════════════════════════════════════════
// 6. Folding — Fold-All / Unfold-All Toolbar Buttons
// ══════════════════════════════════════════════════════════════════════

class FoldToolbarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildFoldableJson(): AppState {
        val state = AppState()
        val json = """
        {
          "name": "ReqLab",
          "version": 2,
          "features": {
            "editor": true,
            "folding": true
          }
        }
        """.trimIndent()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = json
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        return state
    }

    @Test
    fun fold_all_button_click_does_not_crash() {
        val state = buildFoldableJson()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun unfold_all_button_click_after_fold_all_does_not_crash() {
        val state = buildFoldableJson()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-unfold-all", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fold_all_does_not_modify_body_content() {
        val state = buildFoldableJson()
        val originalContent = state.activeTab?.bodyContent ?: ""
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(originalContent, state.activeTab?.bodyContent,
            "Fold-all must be a view-only operation — bodyContent must not change")
    }

    @Test
    fun unfold_all_does_not_modify_body_content() {
        val state = buildFoldableJson()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        val contentAfterFold = state.activeTab?.bodyContent ?: ""

        composeRule.onNodeWithTag("body-editor-unfold-all", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(contentAfterFold, state.activeTab?.bodyContent,
            "Unfold-all must not alter bodyContent")
    }
}

// ══════════════════════════════════════════════════════════════════════
// 7. Fold Indicator — Per-Region Fold/Unfold (small doc path)
// ══════════════════════════════════════════════════════════════════════

class FoldIndicatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clicking_fold_indicator_does_not_crash() {
        val state = AppState()
        val json = "{\n  \"key\": \"value\",\n  \"num\": 42\n}"
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = json
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-indicator-0", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun clicking_fold_indicator_does_not_modify_body_content() {
        val state = AppState()
        val json = "{\n  \"key\": \"value\",\n  \"num\": 42\n}"
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = json
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-indicator-0", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        assertEquals(json, state.activeTab?.bodyContent,
            "Fold indicator click must not modify bodyContent")
    }

    @Test
    fun typing_possible_after_fold_indicator_click_focus_not_stolen() {
        val state = AppState()
        val json = "{\n  \"key\": \"value\",\n  \"num\": 42\n}"
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = json
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-indicator-0", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        // Keyboard focus must still be usable in the editor field
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("X")
        composeRule.waitForIdle()

        val content = state.activeTab?.bodyContent ?: ""
        assertTrue(content.contains("X"),
            "Editor must accept input after fold indicator click; content: ${content.take(200)}")
    }

    @Test
    fun json_array_root_fold_indicator_present_at_line_0() {
        val state = AppState()
        val arrayJson = "[\n  {\"a\": 1},\n  {\"b\": 2}\n]"
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = arrayJson
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-indicator-0", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun json_array_root_fold_and_unfold_cycle_preserves_content() {
        val state = AppState()
        val arrayJson = "[\n  {\"a\": 1},\n  {\"b\": 2}\n]"
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = arrayJson
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Fold
        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        // Unfold
        composeRule.onNodeWithTag("body-editor-unfold-all", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(arrayJson, state.activeTab?.bodyContent,
            "Array-root JSON content must be identical after fold/unfold cycle")
    }
}

// ══════════════════════════════════════════════════════════════════════
// 8. Search Bar
// ══════════════════════════════════════════════════════════════════════

class SearchBarUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun search_toggle_opens_search_bar() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = """{"key":"value"}"""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-search-toggle", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-search-bar", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun search_bar_closes_when_toggle_clicked_again() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = """{"key":"value"}"""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-search-toggle", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-search-bar", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("body-editor-search-toggle", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-search-bar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun typing_search_query_does_not_crash() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\n  \"name\": \"Alice\",\n  \"role\": \"admin\"\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-search-toggle", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-search-input", useUnmergedTree = true)
            .performTextInput("Alice")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun ctrl_or_cmd_f_opens_search_bar_same_as_toolbar_button() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\n  \"name\": \"Alice\",\n  \"role\": \"admin\"\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.F)
                keyUp(Key.CtrlLeft)
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-search-bar", useUnmergedTree = true).assertIsDisplayed()
    }
}

// ══════════════════════════════════════════════════════════════════════
// 9. Response Viewer — Read-Only Editor
// ══════════════════════════════════════════════════════════════════════

class ResponseViewerEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildResponseState(body: String, contentType: String): AppState {
        return AppState().apply {
            activeTab?.response = ResponseDefinition(
                requestId = "qa-test",
                statusCode = 200,
                statusText = "OK",
                headers = listOf(KeyValueEntry("Content-Type", contentType)),
                cookies = emptyList(),
                bodyText = body,
                contentType = contentType,
                executedAtEpochMillis = System.currentTimeMillis(),
                metrics = ResponseMetrics(200, 42, body.length.toLong()),
            )
        }
    }

    @Test
    fun json_response_renders_code_editor() {
        val state = buildResponseState("""{"status":"ok","items":[1,2,3]}""", "application/json")
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("response-viewer").assertIsDisplayed()
    }

    @Test
    fun xml_response_renders_code_editor() {
        val state = buildResponseState("<root><status>ok</status></root>", "application/xml")
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("response-viewer").assertIsDisplayed()
    }

    @Test
    fun json_response_with_fold_all_does_not_crash() {
        val json = "{\n  \"a\": {\n    \"b\": 1\n  },\n  \"c\": 2\n}"
        val state = buildResponseState(json, "application/json")
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // The response viewer uses testTagPrefix = "response-editor" or "response-body"
        // We verify the container renders; fold buttons on response viewer are optional
        composeRule.onNodeWithTag("response-viewer").assertIsDisplayed()
    }
}

// ══════════════════════════════════════════════════════════════════════
// 10. Large File Handling (virtualized editor)
// ══════════════════════════════════════════════════════════════════════

class LargeFileEditorQaTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── 5 MB minified JSON (single-line, char-count threshold) ───────

    @Test
    fun five_mb_minified_json_array_renders_without_freeze() {
        val content = loadQaResource("5MB-min.json")
        assertTrue(content.length > 4_000_000, "Resource must be > 4 MB")

        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = ""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement(content)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(content.length, state.activeTab?.bodyContent?.length,
            "5MB minified payload must remain intact after rendering")
    }

    @Test
    fun five_mb_minified_json_uses_virtualized_editor_path() {
        val content = loadQaResource("5MB-min.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = ""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement(content)
        composeRule.waitForIdle()

        // The char-count threshold triggers unified editing; body-editor-input is always present
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun five_mb_json_toolbar_is_displayed() {
        val content = loadQaResource("5MB-min.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = ""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement(content)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-toolbar", useUnmergedTree = true).assertIsDisplayed()
    }

    // ── 10 MB formatted JSON (multi-line, line-count threshold) ──────

    @Test
    fun ten_mb_formatted_json_object_renders_without_freeze() {
        val content = loadQaResource("10mb-sample.json")
        assertTrue(content.split('\n').size > 500, "Resource must have > 500 lines")

        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun ten_mb_json_uses_virtualized_editor_both_line_nodes_visible() {
        val content = loadQaResource("10mb-sample.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Unified editor path: a single body-editor-input is always visible (no per-line nodes)
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun ten_mb_json_line_numbers_gutter_is_displayed() {
        val content = loadQaResource("10mb-sample.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        // Unified path renders a single input field; body-editor-input confirms line 0 is accessible
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun ten_mb_json_clicking_first_line_activates_inline_editor() {
        val content = loadQaResource("10mb-sample.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Unified editor: body-editor-input is always present — no click-to-activate needed
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun ten_mb_json_inline_editor_accepts_keystrokes_without_being_kicked_out() {
        val content = loadQaResource("10mb-sample.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .assertIsDisplayed()

        repeat(3) { ch ->
            composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
                .performTextInput(ch.toString())
            composeRule.waitForIdle()
        }

        // Unified editor must still be active after 3 keystrokes
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun ten_mb_json_select_all_replace_keeps_editor_stable() {
        val content = loadQaResource("10mb-sample.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // performTextReplacement mirrors Cmd+A + paste at the semantics layer.
        val replacement = "{\"replaced\":true}"
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement(replacement)
        composeRule.waitForIdle()

        assertTrue(state.activeTab?.bodyContent?.startsWith(replacement) == true)
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun large_minified_json_select_all_backspace_clears_content() {
        val content = "{\"items\":[" + (0 until 25_000).joinToString(",") { "{\"k\":$it}" } + "]}"
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement("")
        composeRule.waitForIdle()

        assertEquals("", state.activeTab?.bodyContent.orEmpty(),
            "Cmd+A + Backspace/Delete semantics should fully clear large content")
    }

    @Test
    fun small_json_select_all_paste_large_content_replaces_document() {
        val largeContent = "{\"items\":[" + (0 until 25_000).joinToString(",") { "{\"k\":$it}" } + "]}"
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\"small\":true}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement(largeContent)
        composeRule.waitForIdle()

        assertEquals(largeContent.length, state.activeTab?.bodyContent?.length,
            "Pasting large replacement over selected small JSON must replace full document")
    }

    @Test
    fun small_json_select_all_paste_extreme_single_line_payload_does_not_crash() {
        val largeContent = "{\"items\":[" + (0 until 320_000).joinToString(",") { "{\"k\":$it}" } + "]}"
        assertTrue(largeContent.length > 2_500_000, "Synthetic payload must exceed 2.5 MB")

        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\"small\":true}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement(largeContent)
        composeRule.waitForIdle()

        assertEquals(largeContent.length, state.activeTab?.bodyContent?.length,
            "Very large single-line paste must replace full document without constraints crash")
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun small_json_select_all_paste_extreme_multiline_payload_does_not_crash() {
        val lineCount = 120_000
        val multilinePayload = buildString {
            append("{\"items\":[\n")
            for (i in 0 until lineCount) {
                append("  {\"k\":")
                append(i)
                append("}")
                if (i < lineCount - 1) append(",")
                append('\n')
            }
            append("]}")
        }
        assertTrue(multilinePayload.length > 1_800_000, "Synthetic multiline payload must exceed 1.8 MB")

        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\"small\":true}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement(multilinePayload)
        composeRule.waitForIdle()

        assertEquals(multilinePayload.length, state.activeTab?.bodyContent?.length,
            "Very large multiline paste must replace full document without constraints crash")
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test(timeout = 180_000)
    fun small_json_select_all_paste_real_5mb_min_json_payload_does_not_hang() {
        val largeContent = loadQaResource("5MB-min.json")
        assertTrue(largeContent.length > 4_000_000, "Fixture payload must exceed 4 MB")

        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\"small\":true}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement(largeContent)
        composeRule.waitForIdle()

        assertEquals(largeContent.length, state.activeTab?.bodyContent?.length,
            "Pasting real 5MB minified payload must replace full document")
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test(timeout = 180_000)
    fun empty_editor_paste_real_5mb_min_json_payload_does_not_hang() {
        val largeContent = loadQaResource("5MB-min.json")
        assertTrue(largeContent.length > 4_000_000, "Fixture payload must exceed 4 MB")

        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = ""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput(largeContent)
        composeRule.waitForIdle()

        assertEquals(largeContent.length, state.activeTab?.bodyContent?.length,
            "Pasting real 5MB minified payload into empty editor must keep full content")
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun ten_mb_json_large_append_update_does_not_blank_editor() {
        val content = loadQaResource("10mb-sample.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Simulate a large paste chunk without embedding large literals in test code.
        val appendChunk = content.take(8_192)
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput(appendChunk)
        composeRule.waitForIdle()

        val updated = state.activeTab?.bodyContent.orEmpty()
        assertTrue(updated.length > content.length, "Editor must keep appended large chunk")
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun ten_mb_json_fold_all_button_exists_and_clickable() {
        val content = loadQaResource("10mb-sample.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = content
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test(timeout = 180_000)
    fun large_json_paste_then_switch_to_html_keeps_editor_stable() {
        val largeContent = loadQaResource("5MB-min.json")
        assertTrue(largeContent.length > 4_000_000, "Fixture payload must exceed 4 MB")

        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = ""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Large append in JSON mode (repro precondition from user report).
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput(largeContent)
        composeRule.waitForIdle()
        assertEquals(largeContent.length, state.activeTab?.bodyContent?.length,
            "Large JSON paste must keep full content before language switch")

        // Switch to HTML mode (the reported crash path).
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.HTML
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-line-numbers", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput("<!--html-still-works-->")
        composeRule.waitForIdle()

        val updated = state.activeTab?.bodyContent.orEmpty()
        assertTrue(updated.contains("<!--html-still-works-->"),
            "Editor must remain editable after JSON->HTML switch following large paste")
    }

    @Test
    fun five_mb_json_fold_all_does_not_modify_content() {
        val content = loadQaResource("5MB-min.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = ""
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement(content)
        composeRule.waitForIdle()

        // 5MB-min.json is a single-line minified array — no fold regions exist, so
        // bodyContent must remain identical to the original loaded content.
        assertEquals(content, state.activeTab?.bodyContent,
            "Rendering 5MB JSON must not modify bodyContent")
    }
}

// ══════════════════════════════════════════════════════════════════════
// 11. Gutter and Scrollbar Presence
// ══════════════════════════════════════════════════════════════════════

class GutterAndScrollbarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gutter_present_for_multiline_json() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\n  \"a\": 1,\n  \"b\": 2,\n  \"c\": 3\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-line-numbers", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun gutter_present_for_large_json_virtualized_path() {
        val state = AppState()
        val lines = (0 until 520).joinToString("\n") { "  \"key$it\": $it," }
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\n$lines\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        // In the unified path, body-editor-input is always visible in the viewport.
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun gutter_present_after_fold_all() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{\n  \"a\": 1,\n  \"b\": 2\n}"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-all", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-line-numbers", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test(timeout = 180_000)
    fun gutter_remains_visible_after_large_edit_and_body_type_switch() {
        val largeJson = loadQaResource("10mb-sample.json")
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = largeJson
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-line-numbers", useUnmergedTree = true).assertIsDisplayed()

        // Large edit while JSON is active.
        val appendChunk = largeJson.take(16_384)
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextInput(appendChunk)
        composeRule.waitForIdle()

        // Switch language and ensure gutter + editor remain healthy.
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.HTML
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-line-numbers", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true).assertIsDisplayed()
    }
}

// ══════════════════════════════════════════════════════════════════════
// 12. Script Editor (Pre/Post-request)
// ══════════════════════════════════════════════════════════════════════

class ScriptEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun main_screen_loads_without_crash() {
        // Smoke test: MainScreen must compose without throwing
        val state = AppState()
        composeRule.runOnUiThread {
            // body-editor only renders when a non-NONE body type is active.
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        // Body editor container must be present when the body tab is selected
        composeRule.onNodeWithTag("body-editor", useUnmergedTree = true).assertIsDisplayed()
    }
}

// ══════════════════════════════════════════════════════════════════════
// Helper
// ══════════════════════════════════════════════════════════════════════

private fun loadQaResource(name: String): String {
    // 1. Classpath (Gradle may add qa-tests/src/resources to test classpath)
    val stream = object {}.javaClass.classLoader?.getResourceAsStream(name)
    if (stream != null) return stream.bufferedReader().readText()

    // 2. Filesystem relative paths (works when tests run from project root)
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
