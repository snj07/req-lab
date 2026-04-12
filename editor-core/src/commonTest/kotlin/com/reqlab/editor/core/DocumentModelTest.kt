package com.reqlab.editor.core

import kotlin.test.*

class DocumentModelTest {

    // ── Construction ─────────────────────────────────────────────

    @Test
    fun emptyDocumentHasOneLineAndZeroLength() {
        val doc = DocumentModel("")
        assertEquals(1, doc.lineCount)
        assertEquals(0, doc.length)
        assertEquals("", doc.toFullString())
    }

    @Test
    fun singleLineConstruction() {
        val doc = DocumentModel("hello world")
        assertEquals(1, doc.lineCount)
        assertEquals(11, doc.length)
        assertEquals("hello world", doc.lineText(0))
    }

    @Test
    fun multiLineConstruction() {
        val doc = DocumentModel("line1\nline2\nline3")
        assertEquals(3, doc.lineCount)
        assertEquals("line1", doc.lineText(0))
        assertEquals("line2", doc.lineText(1))
        assertEquals("line3", doc.lineText(2))
    }

    @Test
    fun trailingNewlineCreatesExtraLine() {
        val doc = DocumentModel("a\nb\n")
        assertEquals(3, doc.lineCount)
        assertEquals("a", doc.lineText(0))
        assertEquals("b", doc.lineText(1))
        assertEquals("", doc.lineText(2))
    }

    // ── lineStart ─────────────────────────────────────────────────

    @Test
    fun lineStartFirstLine() {
        val doc = DocumentModel("hello\nworld")
        assertEquals(0, doc.lineStart(0))
    }

    @Test
    fun lineStartSecondLine() {
        val doc = DocumentModel("hello\nworld")
        assertEquals(6, doc.lineStart(1))
    }

    @Test
    fun lineStartThreeLines() {
        val doc = DocumentModel("a\nbb\nccc")
        assertEquals(0, doc.lineStart(0))
        assertEquals(2, doc.lineStart(1))
        assertEquals(5, doc.lineStart(2))
    }

    // ── lineAt ────────────────────────────────────────────────────

    @Test
    fun lineAtReturnsCorrectLine() {
        val doc = DocumentModel("hello\nworld")
        assertEquals(0, doc.lineAt(0))
        assertEquals(0, doc.lineAt(4))
        assertEquals(0, doc.lineAt(5))  // the '\n' belongs to line 0
        assertEquals(1, doc.lineAt(6))
        assertEquals(1, doc.lineAt(10))
    }

    // ── insert ────────────────────────────────────────────────────

    @Test
    fun insertSingleCharIncrementsVersion() {
        val doc = DocumentModel("hello")
        val v0 = doc.version
        doc.insert(5, "!")
        assertEquals(v0 + 1, doc.version)
    }

    @Test
    fun insertSingleCharAtEnd() {
        val doc = DocumentModel("hello")
        doc.insert(5, " world")
        assertEquals("hello world", doc.toFullString())
    }

    @Test
    fun insertNewlineIncreasesLineCount() {
        val doc = DocumentModel("helloworld")
        doc.insert(5, "\n")
        assertEquals(2, doc.lineCount)
        assertEquals("hello", doc.lineText(0))
        assertEquals("world", doc.lineText(1))
    }

    @Test
    fun insertAtBeginning() {
        val doc = DocumentModel("world")
        doc.insert(0, "hello ")
        assertEquals("hello world", doc.toFullString())
    }

    @Test
    fun insertMultilineText() {
        val doc = DocumentModel("ab")
        doc.insert(2, "\ncd\nef")
        assertEquals(3, doc.lineCount)
        assertEquals("ab", doc.lineText(0))
        assertEquals("cd", doc.lineText(1))
        assertEquals("ef", doc.lineText(2))
    }

    // ── delete ────────────────────────────────────────────────────

    @Test
    fun deleteCharDecrementsLength() {
        val doc = DocumentModel("hello")
        doc.delete(4, 5)
        assertEquals(4, doc.length)
        assertEquals("hell", doc.toFullString())
    }

    @Test
    fun deleteNewlineJoinsLines() {
        val doc = DocumentModel("hello\nworld")
        doc.delete(5, 6)
        assertEquals(1, doc.lineCount)
        assertEquals("helloworld", doc.toFullString())
    }

    @Test
    fun deleteMultipleLinesReducesLineCount() {
        val doc = DocumentModel("a\nb\nc\nd")
        // Delete "b\nc\n"
        doc.delete(2, 6)
        assertEquals(2, doc.lineCount)
        assertEquals("a", doc.lineText(0))
        assertEquals("d", doc.lineText(1))
    }

    @Test
    fun deleteZeroRangeIsNoOp() {
        val doc = DocumentModel("abc")
        val v0 = doc.version
        doc.delete(1, 1)
        // version may or may not increment for no-op — just check content is unchanged
        assertEquals("abc", doc.toFullString())
    }

    // ── replaceAll ────────────────────────────────────────────────

    @Test
    fun replaceAllUpdatesContent() {
        val doc = DocumentModel("old content")
        doc.replaceAll("new\ncontent")
        assertEquals(2, doc.lineCount)
        assertEquals("new", doc.lineText(0))
        assertEquals("content", doc.lineText(1))
    }

    @Test
    fun replaceAllWithEmptyString() {
        val doc = DocumentModel("some text")
        doc.replaceAll("")
        assertEquals(1, doc.lineCount)
        assertEquals(0, doc.length)
        assertEquals("", doc.toFullString())
    }

    @Test
    fun replaceAllIncrementsVersion() {
        val doc = DocumentModel("x")
        val v0 = doc.version
        doc.replaceAll("y")
        assertEquals(v0 + 1, doc.version)
    }

    // ── linesText ─────────────────────────────────────────────────

    @Test
    fun linesTextReturnsCorrectRange() {
        val doc = DocumentModel("a\nbb\nccc\ndddd")
        assertEquals("bb\nccc", doc.linesText(1, 2))
    }

    @Test
    fun linesTextSingleLine() {
        val doc = DocumentModel("a\nb\nc")
        assertEquals("b", doc.linesText(1, 1))
    }

    @Test
    fun linesTextOutOfBoundsClamps() {
        val doc = DocumentModel("a\nb")
        // toLine beyond lineCount-1 should clamp
        assertEquals("a\nb", doc.linesText(0, 100))
    }

    // ── version counter ───────────────────────────────────────────

    @Test
    fun versionStartsAtZero() {
        assertEquals(0, DocumentModel("").version)
    }

    @Test
    fun versionIncrementsPerEdit() {
        val doc = DocumentModel("hello")
        assertEquals(0, doc.version)
        doc.insert(5, "!")
        assertEquals(1, doc.version)
        doc.delete(5, 6)
        assertEquals(2, doc.version)
        doc.replaceAll("new")
        assertEquals(3, doc.version)
    }

    // ── Large document edge cases ─────────────────────────────────

    @Test
    fun thousandLinesConsistency() {
        val sb = StringBuilder()
        repeat(1_000) { i -> sb.appendLine("line $i") }
        val text = sb.toString()
        val doc = DocumentModel(text)

        // All lines are accessible correctly
        for (i in 0 until doc.lineCount - 1) {
            val expected = "line $i"
            assertEquals(expected, doc.lineText(i), "Mismatch at line $i")
        }
    }

    @Test
    fun insertAndDeleteRoundTrip() {
        val original = "hello\nworld"
        val doc = DocumentModel(original)
        doc.insert(5, " beautiful")
        doc.delete(5, 15)
        assertEquals(original, doc.toFullString())
    }
}
