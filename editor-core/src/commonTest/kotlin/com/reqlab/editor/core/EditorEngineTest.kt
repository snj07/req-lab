package com.reqlab.editor.core

import kotlin.test.*

class EditorEngineTest {

    private val engine = EditorEngine()

    @Test
    fun createStateFromText() {
        val state = engine.createState("hello world", LanguageMode.PLAIN_TEXT)
        assertEquals("hello world", state.text)
        assertEquals(LanguageMode.PLAIN_TEXT, state.languageMode)
        assertEquals(1, state.lineCount)
    }

    @Test
    fun createStateValidatesJson() {
        val state = engine.createState("""{"key": "value"}""", LanguageMode.JSON)
        assertTrue(state.diagnostics.isEmpty())
    }

    @Test
    fun createStateReportsJsonErrors() {
        val state = engine.createState("""{"key":}""", LanguageMode.JSON)
        assertTrue(state.diagnostics.isNotEmpty())
    }

    @Test
    fun createStateDetectsFoldRegions() {
        val json = "{\n  \"a\": 1\n}"
        val state = engine.createState(json, LanguageMode.JSON, foldingEnabled = true)
        assertTrue(state.folding.regions.isNotEmpty())
    }

    @Test
    fun createStateNoFoldRegionsWhenDisabled() {
        val json = "{\n  \"a\": 1\n}"
        val state = engine.createState(json, LanguageMode.JSON, foldingEnabled = false)
        assertTrue(state.folding.regions.isEmpty())
    }

    @Test
    fun updateText() {
        val state = engine.createState("old", LanguageMode.PLAIN_TEXT)
        val updated = engine.updateText(state, "new text")
        assertEquals("new text", updated.text)
    }

    @Test
    fun updateTextRevalidates() {
        val state = engine.createState("""{"key": "value"}""", LanguageMode.JSON)
        val broken = engine.updateText(state, """{"key":}""")
        assertTrue(broken.diagnostics.isNotEmpty())
    }

    @Test
    fun insertText() {
        val state = engine.createState("hello world", LanguageMode.PLAIN_TEXT)
        val updated = engine.insertText(state, CursorPosition(1, 5), " beautiful")
        assertEquals("hello beautiful world", updated.text)
    }

    @Test
    fun deleteRange() {
        val state = engine.createState("hello world", LanguageMode.PLAIN_TEXT)
        val range = TextRange(CursorPosition(1, 5), CursorPosition(1, 11))
        val updated = engine.deleteRange(state, range)
        assertEquals("hello", updated.text)
    }

    @Test
    fun replaceRange() {
        val state = engine.createState("hello world", LanguageMode.PLAIN_TEXT)
        val range = TextRange(CursorPosition(1, 6), CursorPosition(1, 11))
        val updated = engine.replaceRange(state, range, "earth")
        assertEquals("hello earth", updated.text)
    }

    @Test
    fun switchMode() {
        val state = engine.createState("{}", LanguageMode.PLAIN_TEXT)
        val switched = engine.switchMode(state, LanguageMode.JSON)
        assertEquals(LanguageMode.JSON, switched.languageMode)
        assertTrue(switched.diagnostics.isEmpty()) // valid json
    }

    @Test
    fun switchModeRevalidates() {
        val state = engine.createState("{invalid", LanguageMode.PLAIN_TEXT)
        assertEquals(0, state.diagnostics.size) // plain text has no validation
        val switched = engine.switchMode(state, LanguageMode.JSON)
        assertTrue(switched.diagnostics.isNotEmpty()) // now validated as JSON
    }

    @Test
    fun tokenizeRange() {
        val state = engine.createState("""{"key": "value"}""", LanguageMode.JSON)
        val tokens = engine.tokenizeRange(state, 1, 1)
        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens[1]!!.isNotEmpty())
    }

    @Test
    fun tokenizeLine() {
        val state = engine.createState("""const x = "hello";""", LanguageMode.JAVASCRIPT)
        val tokens = engine.tokenizeLine(state, 1)
        assertTrue(tokens.isNotEmpty())
    }

    @Test
    fun toggleFold() {
        val json = "{\n  \"a\": 1\n}"
        val state = engine.createState(json, LanguageMode.JSON)
        assertTrue(state.folding.regions.isNotEmpty())
        val startLine = state.folding.regions[0].startLine
        val folded = engine.toggleFold(state, startLine)
        assertTrue(folded.folding.isCollapsed(startLine))
        val unfolded = engine.toggleFold(folded, startLine)
        assertFalse(unfolded.folding.isCollapsed(startLine))
    }

    @Test
    fun foldAllUnfoldAll() {
        val json = "{\n  \"a\": {\n    \"b\": 1\n  }\n}"
        val state = engine.createState(json, LanguageMode.JSON)
        val allFolded = engine.foldAll(state)
        assertTrue(allFolded.folding.hasFolds)
        val allUnfolded = engine.unfoldAll(allFolded)
        assertFalse(allUnfolded.folding.hasFolds)
    }

    @Test
    fun selectAll() {
        val state = engine.createState("abc\ndef", LanguageMode.PLAIN_TEXT)
        val selected = engine.selectAll(state)
        assertTrue(selected.selection.hasSelection)
        val range = selected.selection.primaryRange!!
        assertEquals(CursorPosition(1, 0), range.start)
        assertEquals(CursorPosition(2, 3), range.end)
    }

    @Test
    fun clearSelection() {
        val state = engine.createState("abc", LanguageMode.PLAIN_TEXT)
        val selected = engine.selectAll(state)
        val cleared = engine.clearSelection(selected)
        assertFalse(cleared.selection.hasSelection)
    }

    @Test
    fun moveCursor() {
        val state = engine.createState("abc\ndef", LanguageMode.PLAIN_TEXT)
        val down = engine.moveCursorDown(state)
        assertEquals(2, down.cursor.position.line)
        val up = engine.moveCursorUp(down)
        assertEquals(1, up.cursor.position.line)
        val right = engine.moveCursorRight(state)
        assertEquals(1, right.cursor.position.column)
    }

    @Test
    fun validateBackwardCompatible() {
        val errors = engine.validate("""{"key":}""", LanguageMode.JSON)
        assertTrue(errors.isNotEmpty())
        val noErrors = engine.validate("""{"key": 1}""", LanguageMode.JSON)
        assertTrue(noErrors.isEmpty())
    }

    @Test
    fun validateBlankReturnsEmpty() {
        val errors = engine.validate("   ", LanguageMode.JSON)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun visibleLines() {
        val json = "{\n  \"a\": 1,\n  \"b\": 2\n}"
        val state = engine.createState(json, LanguageMode.JSON)
        val visible = engine.visibleLines(state)
        assertEquals(4, visible.size) // all lines visible
    }

    @Test
    fun visibleLinesWithFolds() {
        val json = "{\n  \"a\": 1,\n  \"b\": 2\n}"
        val state = engine.createState(json, LanguageMode.JSON)
        val foldLine = state.folding.regions[0].startLine
        val folded = engine.toggleFold(state, foldLine)
        val visible = engine.visibleLines(folded)
        assertTrue(visible.size < 4) // some lines hidden
    }

    @Test
    fun editorStateFromTextFactory() {
        val state = EditorState.fromText("hello\nworld", LanguageMode.JSON)
        assertEquals("hello\nworld", state.text)
        assertEquals(2, state.lineCount)
        assertEquals(LanguageMode.JSON, state.languageMode)
    }
}
