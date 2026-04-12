package com.reqlab.editor.core

import kotlin.test.*

class SelectionModelTest {

    @Test
    fun emptySelectionHasNoSelection() {
        val sel = SelectionModel.EMPTY
        assertFalse(sel.hasSelection)
        assertNull(sel.primaryRange)
    }

    @Test
    fun selectAll() {
        val doc = EditorDocument.create("abc\ndef\nghi")
        val sel = SelectionModel.EMPTY.selectAll(doc)
        assertTrue(sel.hasSelection)
        val range = sel.primaryRange!!
        assertEquals(CursorPosition(1, 0), range.start)
        assertEquals(CursorPosition(3, 3), range.end)
    }

    @Test
    fun clearSelection() {
        val doc = EditorDocument.create("abc")
        val sel = SelectionModel.EMPTY.selectAll(doc).clearSelection()
        assertFalse(sel.hasSelection)
    }

    @Test
    fun setSelection() {
        val range = TextRange(CursorPosition(1, 1), CursorPosition(1, 3))
        val sel = SelectionModel.EMPTY.setSelection(range)
        assertTrue(sel.hasSelection)
        assertEquals(range, sel.primaryRange)
    }

    @Test
    fun extendSelection() {
        val anchor = CursorPosition(1, 0)
        val head = CursorPosition(2, 3)
        val sel = SelectionModel.EMPTY.extendSelection(anchor, head)
        assertTrue(sel.hasSelection)
        assertEquals(anchor, sel.primaryRange!!.start)
        assertEquals(head, sel.primaryRange!!.end)
    }

    @Test
    fun textRangeNormalized() {
        val reversed = TextRange(CursorPosition(3, 0), CursorPosition(1, 0))
        val norm = reversed.normalized()
        assertEquals(CursorPosition(1, 0), norm.start)
        assertEquals(CursorPosition(3, 0), norm.end)
    }

    @Test
    fun textRangeNormalizedSameLine() {
        val reversed = TextRange(CursorPosition(1, 5), CursorPosition(1, 2))
        val norm = reversed.normalized()
        assertEquals(CursorPosition(1, 2), norm.start)
        assertEquals(CursorPosition(1, 5), norm.end)
    }

    @Test
    fun textRangeEmpty() {
        val range = TextRange.EMPTY
        assertTrue(range.isEmpty)
        assertFalse(range.isMultiLine)
    }

    @Test
    fun textRangeIsMultiLine() {
        val range = TextRange(CursorPosition(1, 0), CursorPosition(3, 0))
        assertTrue(range.isMultiLine)
    }
}
