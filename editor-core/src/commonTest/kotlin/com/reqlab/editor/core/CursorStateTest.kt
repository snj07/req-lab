package com.reqlab.editor.core

import kotlin.test.*

class CursorStateTest {

    private val doc = EditorDocument.create("abc\ndef\nghij")

    @Test
    fun defaultCursorPosition() {
        val cursor = CursorState()
        assertEquals(1, cursor.position.line)
        assertEquals(0, cursor.position.column)
    }

    @Test
    fun moveUp() {
        val cursor = CursorState(CursorPosition(2, 1))
        val moved = cursor.moveUp(doc)
        assertEquals(1, moved.position.line)
        assertEquals(1, moved.position.column)
    }

    @Test
    fun moveUpAtTopStays() {
        val cursor = CursorState(CursorPosition(1, 2))
        val moved = cursor.moveUp(doc)
        assertEquals(1, moved.position.line)
        assertEquals(2, moved.position.column)
    }

    @Test
    fun moveDown() {
        val cursor = CursorState(CursorPosition(1, 1))
        val moved = cursor.moveDown(doc)
        assertEquals(2, moved.position.line)
        assertEquals(1, moved.position.column)
    }

    @Test
    fun moveDownAtBottomStays() {
        val cursor = CursorState(CursorPosition(3, 0))
        val moved = cursor.moveDown(doc)
        assertEquals(3, moved.position.line)
    }

    @Test
    fun moveDownClampsColumn() {
        // Line 3 has 4 chars "ghij", but if cursor is at col 5 on a longer line
        val bigDoc = EditorDocument.create("abcdef\ngh")
        val cursor = CursorState(CursorPosition(1, 5))
        val moved = cursor.moveDown(bigDoc)
        assertEquals(2, moved.position.line)
        assertEquals(2, moved.position.column) // clamped to length of "gh"
    }

    @Test
    fun moveLeft() {
        val cursor = CursorState(CursorPosition(1, 2))
        val moved = cursor.moveLeft(doc)
        assertEquals(1, moved.position.line)
        assertEquals(1, moved.position.column)
    }

    @Test
    fun moveLeftWrapsToEndOfPreviousLine() {
        val cursor = CursorState(CursorPosition(2, 0))
        val moved = cursor.moveLeft(doc)
        assertEquals(1, moved.position.line)
        assertEquals(3, moved.position.column) // end of "abc"
    }

    @Test
    fun moveLeftAtDocStartStays() {
        val cursor = CursorState(CursorPosition(1, 0))
        val moved = cursor.moveLeft(doc)
        assertEquals(1, moved.position.line)
        assertEquals(0, moved.position.column)
    }

    @Test
    fun moveRight() {
        val cursor = CursorState(CursorPosition(1, 1))
        val moved = cursor.moveRight(doc)
        assertEquals(1, moved.position.line)
        assertEquals(2, moved.position.column)
    }

    @Test
    fun moveRightWrapsToStartOfNextLine() {
        val cursor = CursorState(CursorPosition(1, 3)) // end of "abc"
        val moved = cursor.moveRight(doc)
        assertEquals(2, moved.position.line)
        assertEquals(0, moved.position.column)
    }

    @Test
    fun moveRightAtDocEndStays() {
        val cursor = CursorState(CursorPosition(3, 4)) // end of "ghij"
        val moved = cursor.moveRight(doc)
        assertEquals(3, moved.position.line)
        assertEquals(4, moved.position.column)
    }

    @Test
    fun moveToLineStart() {
        val cursor = CursorState(CursorPosition(2, 2))
        val moved = cursor.moveToLineStart()
        assertEquals(2, moved.position.line)
        assertEquals(0, moved.position.column)
    }

    @Test
    fun moveToLineEnd() {
        val cursor = CursorState(CursorPosition(1, 0))
        val moved = cursor.moveToLineEnd(doc)
        assertEquals(1, moved.position.line)
        assertEquals(3, moved.position.column)
    }

    @Test
    fun moveToDocumentStart() {
        val cursor = CursorState(CursorPosition(3, 2))
        val moved = cursor.moveToDocumentStart()
        assertEquals(1, moved.position.line)
        assertEquals(0, moved.position.column)
    }

    @Test
    fun moveToDocumentEnd() {
        val cursor = CursorState(CursorPosition(1, 0))
        val moved = cursor.moveToDocumentEnd(doc)
        assertEquals(3, moved.position.line)
        assertEquals(4, moved.position.column)
    }

    @Test
    fun cursorPositionRejectsInvalidLine() {
        assertFailsWith<IllegalArgumentException> { CursorPosition(0, 0) }
        assertFailsWith<IllegalArgumentException> { CursorPosition(-1, 0) }
    }

    @Test
    fun cursorPositionRejectsNegativeColumn() {
        assertFailsWith<IllegalArgumentException> { CursorPosition(1, -1) }
    }
}
