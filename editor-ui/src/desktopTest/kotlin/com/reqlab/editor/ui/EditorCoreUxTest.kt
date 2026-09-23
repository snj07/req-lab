package com.reqlab.editor.ui

import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorCoreUxTest {

    private fun vm(text: String, mode: LanguageMode = LanguageMode.JSON) =
        EditorViewModel(text, mode)

    @Test
    fun replace_all_is_one_undoable_edit() {
        val v = vm("foo bar foo")
        v.replaceAllMatches("foo", "baz")
        assertEquals("baz bar baz", v.getFullText())
        v.undo()
        assertEquals("foo bar foo", v.getFullText())
    }

    @Test
    fun replace_range_replaces_one_match() {
        val v = vm("aaa bbb aaa")
        v.replaceRange(0, 3, "xx")
        assertEquals("xx bbb aaa", v.getFullText())
    }

    @Test
    fun duplicate_line_inserts_copy_below() {
        val v = vm("one\ntwo")
        v.moveCursorTo(0)
        v.duplicateLine()
        assertEquals("one\none\ntwo", v.getFullText())
    }

    @Test
    fun move_line_down_and_up() {
        val v = vm("a\nb\nc")
        v.moveCursorTo(0)
        v.moveLine(down = true)
        assertEquals("b\na\nc", v.getFullText())
        v.moveLine(down = false)
        assertEquals("a\nb\nc", v.getFullText())
    }

    @Test
    fun toggle_slash_comment_and_uncomment() {
        val v = vm("  alpha", LanguageMode.JSON)
        v.toggleComment()
        assertEquals("  // alpha", v.getFullText())
        v.toggleComment()
        assertEquals("  alpha", v.getFullText())
    }

    @Test
    fun toggle_xml_comment() {
        val v = vm("<item/>", LanguageMode.XML)
        v.toggleComment()
        assertEquals("<!-- <item/> -->", v.getFullText())
        v.toggleComment()
        assertEquals("<item/>", v.getFullText())
    }

    @Test
    fun select_line_includes_trailing_newline() {
        val v = vm("ab\ncd")
        v.moveCursorTo(1)
        v.selectLine()
        assertEquals("ab\n", v.getSelectedText())
    }

    @Test
    fun go_to_line_clamps() {
        val v = vm("a\nb\nc")
        v.goToLine(2)
        assertEquals(v.document.lineStart(1), v.state.value.cursorOffset)
        v.goToLine(99)
        assertEquals(v.document.lineStart(2), v.state.value.cursorOffset)
    }

    @Test
    fun matching_brackets_skip_strings() {
        val text = """{ "a": [1] }"""
        val pair = matchingBracketOffsets(text, 0)
        assertNotNull(pair)
        assertEquals(0, pair.first)
        assertEquals(text.lastIndex, pair.second)
        assertNull(matchingBracketOffsets("no brackets", 0))
    }

    @Test
    fun indent_guide_columns_every_two_spaces() {
        assertEquals(listOf(2), indentGuideColumns("    x"))
        assertEquals(emptyList(), indentGuideColumns(" x"))
        assertEquals(listOf(2, 4), indentGuideColumns("      y"))
    }

    @Test
    fun replace_all_occurrences_ignore_case() {
        assertEquals("X X", replaceAllOccurrences("a A", "a", "X"))
    }

    @Test
    fun can_undo_after_duplicate() {
        val v = vm("x")
        assertFalse(v.canUndo())
        v.duplicateLine()
        assertTrue(v.canUndo())
        v.undo()
        assertEquals("x", v.getFullText())
    }
}
