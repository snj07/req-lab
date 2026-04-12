package com.reqlab.editor.core

import kotlin.test.*

class GapBufferTest {

    // ── Construction ─────────────────────────────────────────────

    @Test
    fun emptyBufferHasZeroLength() {
        assertEquals(0, GapBuffer().length)
    }

    @Test
    fun emptyBufferToFullStringIsEmpty() {
        assertEquals("", GapBuffer().toFullString())
    }

    // ── Insert ────────────────────────────────────────────────────

    @Test
    fun insertIntoEmptyBuffer() {
        val buf = GapBuffer()
        buf.insert(0, "hello")
        assertEquals(5, buf.length)
        assertEquals("hello", buf.toFullString())
    }

    @Test
    fun insertAtBeginning() {
        val buf = GapBuffer()
        buf.insert(0, "world")
        buf.insert(0, "hello ")
        assertEquals("hello world", buf.toFullString())
    }

    @Test
    fun insertAtEnd() {
        val buf = GapBuffer()
        buf.insert(0, "hello")
        buf.insert(5, " world")
        assertEquals("hello world", buf.toFullString())
    }

    @Test
    fun insertInMiddle() {
        val buf = GapBuffer()
        buf.insert(0, "helo")
        buf.insert(3, "l")
        assertEquals("hello", buf.toFullString())
    }

    @Test
    fun sequentialAppendIsCorrect() {
        val buf = GapBuffer()
        for (i in 1..100) buf.insert(buf.length, i.toString())
        val expected = (1..100).joinToString("")
        assertEquals(expected, buf.toFullString())
    }

    @Test
    fun insertBeyondLengthClampsToEnd() {
        val buf = GapBuffer()
        buf.insert(0, "abc")
        buf.insert(1000, "d")  // should clamp to 3
        assertEquals("abcd", buf.toFullString())
    }

    @Test
    fun insertNegativeIndexClampsToZero() {
        val buf = GapBuffer()
        buf.insert(0, "bc")
        buf.insert(-5, "a")
        assertEquals("abc", buf.toFullString())
    }

    @Test
    fun insertEmptyStringIsNoOp() {
        val buf = GapBuffer()
        buf.insert(0, "abc")
        buf.insert(1, "")
        assertEquals("abc", buf.toFullString())
        assertEquals(3, buf.length)
    }

    @Test
    fun insertNewline() {
        val buf = GapBuffer()
        buf.insert(0, "ab")
        buf.insert(2, "\ncd")
        assertEquals("ab\ncd", buf.toFullString())
    }

    // ── Delete ────────────────────────────────────────────────────

    @Test
    fun deleteFromMiddle() {
        val buf = GapBuffer()
        buf.insert(0, "hello world")
        buf.delete(5, 11)
        assertEquals("hello", buf.toFullString())
    }

    @Test
    fun deleteFromStart() {
        val buf = GapBuffer()
        buf.insert(0, "hello")
        buf.delete(0, 3)
        assertEquals("lo", buf.toFullString())
    }

    @Test
    fun deleteAll() {
        val buf = GapBuffer()
        buf.insert(0, "abc")
        buf.delete(0, 3)
        assertEquals("", buf.toFullString())
        assertEquals(0, buf.length)
    }

    @Test
    fun deleteZeroLengthRangeIsNoOp() {
        val buf = GapBuffer()
        buf.insert(0, "abc")
        buf.delete(1, 1)
        assertEquals("abc", buf.toFullString())
    }

    @Test
    fun deleteReversedRangeIsNoOp() {
        val buf = GapBuffer()
        buf.insert(0, "abc")
        buf.delete(2, 1)
        assertEquals("abc", buf.toFullString())
    }

    // ── charAt / subSequence ──────────────────────────────────────

    @Test
    fun charAtFirstChar() {
        val buf = GapBuffer()
        buf.insert(0, "abc")
        assertEquals('a', buf.charAt(0))
    }

    @Test
    fun charAtLastChar() {
        val buf = GapBuffer()
        buf.insert(0, "abc")
        assertEquals('c', buf.charAt(2))
    }

    @Test
    fun charAtAfterInsertBeforeGap() {
        val buf = GapBuffer()
        buf.insert(0, "world")
        buf.insert(0, "hello ")
        // "hello world"[0..10]
        assertEquals('h', buf.charAt(0))
        assertEquals('w', buf.charAt(6))
        assertEquals('d', buf.charAt(10))
    }

    @Test
    fun subSequenceFullRange() {
        val buf = GapBuffer()
        buf.insert(0, "hello world")
        assertEquals("hello world", buf.subSequence(0, 11))
    }

    @Test
    fun subSequencePartial() {
        val buf = GapBuffer()
        buf.insert(0, "hello world")
        assertEquals("world", buf.subSequence(6, 11))
    }

    @Test
    fun subSequenceEmptyRange() {
        val buf = GapBuffer()
        buf.insert(0, "abc")
        assertEquals("", buf.subSequence(1, 1))
    }

    // ── indexOfFrom ───────────────────────────────────────────────

    @Test
    fun indexOfFromFindsFirstNewline() {
        val buf = GapBuffer()
        buf.insert(0, "ab\ncd\nef")
        assertEquals(2, buf.indexOfFrom('\n', 0))
    }

    @Test
    fun indexOfFromSkipsPastFirst() {
        val buf = GapBuffer()
        buf.insert(0, "ab\ncd\nef")
        assertEquals(5, buf.indexOfFrom('\n', 3))
    }

    @Test
    fun indexOfFromReturnsMinusOneIfNotFound() {
        val buf = GapBuffer()
        buf.insert(0, "abcdef")
        assertEquals(-1, buf.indexOfFrom('\n', 0))
    }

    // ── Growth (internal resize) ──────────────────────────────────

    @Test
    fun largeInsertTriggersInternalGrow() {
        val buf = GapBuffer(initialCapacity = 4)
        val large = "a".repeat(10_000)
        buf.insert(0, large)
        assertEquals(10_000, buf.length)
        assertEquals(large, buf.toFullString())
    }

    @Test
    fun manyAlternatingInsertsAndDeletes() {
        val buf = GapBuffer()
        repeat(200) { i ->
            buf.insert(0, "$i,")
            if (i % 3 == 0 && buf.length > 2) buf.delete(0, 2)
        }
        // Just verify the buffer is internally consistent
        assertEquals(buf.toFullString().length, buf.length)
    }
}
