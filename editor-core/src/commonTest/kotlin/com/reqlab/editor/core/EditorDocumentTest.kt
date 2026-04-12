package com.reqlab.editor.core

import kotlin.test.*

class EditorDocumentTest {

    @Test
    fun createEmptyDocument() {
        val doc = EditorDocument.empty()
        assertEquals("", doc.text)
        assertEquals(1, doc.lineCount)
        assertEquals(0, doc.length)
    }

    @Test
    fun createSingleLineDocument() {
        val doc = EditorDocument.create("hello world")
        assertEquals(1, doc.lineCount)
        assertEquals("hello world", doc.lineText(1))
        assertEquals(11, doc.lineLength(1))
    }

    @Test
    fun createMultiLineDocument() {
        val doc = EditorDocument.create("line1\nline2\nline3")
        assertEquals(3, doc.lineCount)
        assertEquals("line1", doc.lineText(1))
        assertEquals("line2", doc.lineText(2))
        assertEquals("line3", doc.lineText(3))
    }

    @Test
    fun lineTextExcludesNewline() {
        val doc = EditorDocument.create("abc\ndef\n")
        assertEquals(3, doc.lineCount)
        assertEquals("abc", doc.lineText(1))
        assertEquals("def", doc.lineText(2))
        assertEquals("", doc.lineText(3)) // empty line after trailing newline
    }

    @Test
    fun positionToOffset() {
        val doc = EditorDocument.create("abc\ndef\nghi")
        assertEquals(0, doc.positionToOffset(CursorPosition(1, 0)))
        assertEquals(2, doc.positionToOffset(CursorPosition(1, 2)))
        assertEquals(4, doc.positionToOffset(CursorPosition(2, 0)))
        assertEquals(8, doc.positionToOffset(CursorPosition(3, 0)))
    }

    @Test
    fun offsetToPosition() {
        val doc = EditorDocument.create("abc\ndef\nghi")
        assertEquals(CursorPosition(1, 0), doc.offsetToPosition(0))
        assertEquals(CursorPosition(1, 2), doc.offsetToPosition(2))
        assertEquals(CursorPosition(2, 0), doc.offsetToPosition(4))
        assertEquals(CursorPosition(3, 2), doc.offsetToPosition(10))
    }

    @Test
    fun insertText() {
        val doc = EditorDocument.create("hello world")
        val updated = doc.insert(5, " beautiful")
        assertEquals("hello beautiful world", updated.text)
    }

    @Test
    fun deleteRange() {
        val doc = EditorDocument.create("hello world")
        val updated = doc.delete(5, 11)
        assertEquals("hello", updated.text)
    }

    @Test
    fun replaceRange() {
        val doc = EditorDocument.create("hello world")
        val updated = doc.replace(6, 11, "earth")
        assertEquals("hello earth", updated.text)
    }

    @Test
    fun replaceAll() {
        val doc = EditorDocument.create("old text")
        val updated = doc.replaceAll("new text")
        assertEquals("new text", updated.text)
    }

    @Test
    fun getTextRange() {
        val doc = EditorDocument.create("hello world")
        assertEquals("world", doc.getText(6, 11))
    }

    @Test
    fun getTextByTextRange() {
        val doc = EditorDocument.create("abc\ndef\nghi")
        val range = TextRange(CursorPosition(1, 0), CursorPosition(2, 3))
        assertEquals("abc\ndef", doc.getText(range))
    }

    @Test
    fun linesReturnsAllLines() {
        val doc = EditorDocument.create("a\nb\nc")
        assertEquals(listOf("a", "b", "c"), doc.lines())
    }

    @Test
    fun lineOutOfRangeThrows() {
        val doc = EditorDocument.create("hello")
        assertFailsWith<IllegalArgumentException> { doc.lineText(0) }
        assertFailsWith<IllegalArgumentException> { doc.lineText(2) }
    }

    @Test
    fun insertAtBoundary() {
        val doc = EditorDocument.create("abc")
        assertEquals("abc!", doc.insert(3, "!").text)
        assertEquals("!abc", doc.insert(0, "!").text)
    }

    @Test
    fun equalityByContent() {
        val doc1 = EditorDocument.create("same")
        val doc2 = EditorDocument.create("same")
        assertEquals(doc1, doc2)
    }
}
