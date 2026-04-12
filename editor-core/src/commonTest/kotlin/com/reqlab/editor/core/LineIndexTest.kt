package com.reqlab.editor.core

import kotlin.test.*

class LineIndexTest {

    private fun indexFrom(text: String): LineIndex {
        val buf = GapBuffer()
        if (text.isNotEmpty()) buf.insert(0, text)
        val idx = LineIndex()
        idx.rebuild(buf)
        return idx
    }

    // ── rebuild ───────────────────────────────────────────────────

    @Test
    fun emptyDocumentHasOneLine() {
        val idx = indexFrom("")
        assertEquals(1, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
    }

    @Test
    fun singleLineNoNewline() {
        val idx = indexFrom("hello")
        assertEquals(1, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
    }

    @Test
    fun twoLinesWithNewline() {
        val idx = indexFrom("hello\nworld")
        assertEquals(2, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(6, idx.lineStart(1))  // 'w' is at offset 6
    }

    @Test
    fun trailingNewlineCreatesExtraLine() {
        val idx = indexFrom("hello\n")
        assertEquals(2, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(6, idx.lineStart(1))
    }

    @Test
    fun multipleLines() {
        val idx = indexFrom("a\nb\nc\nd")
        assertEquals(4, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(2, idx.lineStart(1))
        assertEquals(4, idx.lineStart(2))
        assertEquals(6, idx.lineStart(3))
    }

    // ── lineAt ────────────────────────────────────────────────────

    @Test
    fun lineAtFirstChar() {
        val idx = indexFrom("hello\nworld")
        assertEquals(0, idx.lineAt(0))
        assertEquals(0, idx.lineAt(4))
    }

    @Test
    fun lineAtNewlineChar() {
        val idx = indexFrom("hello\nworld")
        // '\n' is at offset 5 — belongs to line 0
        assertEquals(0, idx.lineAt(5))
    }

    @Test
    fun lineAtSecondLineStart() {
        val idx = indexFrom("hello\nworld")
        assertEquals(1, idx.lineAt(6))
        assertEquals(1, idx.lineAt(10))
    }

    @Test
    fun lineAtSingleCharLines() {
        val idx = indexFrom("a\nb\nc")
        assertEquals(0, idx.lineAt(0))  // 'a'
        assertEquals(0, idx.lineAt(1))  // '\n'
        assertEquals(1, idx.lineAt(2))  // 'b'
        assertEquals(1, idx.lineAt(3))  // '\n'
        assertEquals(2, idx.lineAt(4))  // 'c'
    }

    // ── onInsert no-newline (lazy step) ───────────────────────────

    @Test
    fun onInsertNoNewlineSingleChar() {
        val buf = GapBuffer()
        buf.insert(0, "hello\nworld")
        val idx = LineIndex()
        idx.rebuild(buf)

        // Insert 'X' at position 2 → "heXllo\nworld"
        buf.insert(2, "X")
        idx.onInsert(2, "X")

        assertEquals(2, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(7, idx.lineStart(1))  // was 6, now shifted +1
    }

    @Test
    fun onInsertNoNewlineAtLineStart() {
        val buf = GapBuffer()
        buf.insert(0, "a\nb")
        val idx = LineIndex()
        idx.rebuild(buf)

        buf.insert(2, "X")  // Insert at line 1 start → "a\nXb"
        idx.onInsert(2, "X")

        assertEquals(2, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(2, idx.lineStart(1))  // line 1 start unchanged (insert was AT it)
    }

    @Test
    fun sequentialSingleCharInsertsAreConsistent() {
        // 3-line doc: "aa\nbb\ncc"
        val buf = GapBuffer()
        buf.insert(0, "aa\nbb\ncc")
        val idx = LineIndex()
        idx.rebuild(buf)
        // lineStarts = [0, 3, 6]

        // Simulate typing 3 extra chars on line 0 (before '\n')
        buf.insert(2, "X"); idx.onInsert(2, "X")  // "aaX\nbb\ncc"
        buf.insert(3, "X"); idx.onInsert(3, "X")  // "aaXX\nbb\ncc"
        buf.insert(4, "X"); idx.onInsert(4, "X")  // "aaXXX\nbb\ncc"

        // Line 0 starts at 0; line 1 at 3+3=6; line 2 at 6+3=9
        assertEquals(3, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(6, idx.lineStart(1))
        assertEquals(9, idx.lineStart(2))

        // lineAt verifications
        assertEquals(0, idx.lineAt(5))  // 'X' on line 0
        assertEquals(1, idx.lineAt(6))  // 'b' on line 1
        assertEquals(2, idx.lineAt(9))  // 'c' on line 2
    }

    // ── onInsert with newlines ────────────────────────────────────

    @Test
    fun onInsertSingleNewlineInMiddle() {
        val buf = GapBuffer()
        buf.insert(0, "helloworld")
        val idx = LineIndex()
        idx.rebuild(buf)
        assertEquals(1, idx.lineCount)

        buf.insert(5, "\n")
        idx.onInsert(5, "\n")

        assertEquals(2, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(6, idx.lineStart(1))
    }

    @Test
    fun onInsertMultilineString() {
        val buf = GapBuffer()
        buf.insert(0, "ab")
        val idx = LineIndex()
        idx.rebuild(buf)

        buf.insert(2, "\ncd\nef")
        idx.onInsert(2, "\ncd\nef")

        assertEquals(3, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(3, idx.lineStart(1))  // "cd" starts at offset 3
        assertEquals(6, idx.lineStart(2))  // "ef" starts at offset 6
    }

    // ── onDelete ─────────────────────────────────────────────────

    @Test
    fun onDeleteSingleCharNoNewline() {
        val buf = GapBuffer()
        buf.insert(0, "hello\nworld")
        val idx = LineIndex()
        idx.rebuild(buf)

        buf.delete(2, 3)   // delete 'l' → "helo\nworld"
        idx.onDelete(2, 3)

        assertEquals(2, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(5, idx.lineStart(1))  // was 6, now shifted -1
    }

    @Test
    fun onDeleteNewlineJoinsLines() {
        val buf = GapBuffer()
        buf.insert(0, "hello\nworld")
        val idx = LineIndex()
        idx.rebuild(buf)

        buf.delete(5, 6)   // delete '\n' → "helloworld"
        idx.onDelete(5, 6)

        assertEquals(1, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
    }

    @Test
    fun onDeleteMultipleLines() {
        val buf = GapBuffer()
        buf.insert(0, "a\nb\nc\nd")
        val idx = LineIndex()
        idx.rebuild(buf)

        // Delete "b\nc\n" → "a\nd"
        buf.delete(2, 6)
        idx.onDelete(2, 6)

        assertEquals(2, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(2, idx.lineStart(1))  // 'd' is now at offset 2
    }

    // ── rebuild idempotency ───────────────────────────────────────

    @Test
    fun rebuildAfterManyEditsIsConsistent() {
        val buf = GapBuffer()
        buf.insert(0, "line1\nline2\nline3")
        val idx = LineIndex()
        idx.rebuild(buf)

        // A series of edits
        buf.insert(0, "preamble\n")
        idx.rebuild(buf)  // full rebuild

        assertEquals(4, idx.lineCount)
        assertEquals(0, idx.lineStart(0))
        assertEquals(9, idx.lineStart(1))  // "line1" starts at 9
        assertEquals(15, idx.lineStart(2))
        assertEquals(21, idx.lineStart(3))
    }
}
