package com.reqlab.editor.ui

import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.LanguageModeProvider
import com.reqlab.editor.core.PlainTextMode
import com.reqlab.editor.core.StyleBuffer
import com.reqlab.editor.core.TokenType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdleLexerRevisionTest {
    @Test
    fun obsolete_blocked_revision_cannot_publish_after_new_revision_is_scheduled() = runBlocking {
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val provider = object : LanguageModeProvider by PlainTextMode {
            override fun tokenizeRangeIntoBuffer(
                document: com.reqlab.editor.core.DocumentModel,
                fromChar: Int,
                toChar: Int,
                buffer: StyleBuffer,
            ) {
                if (document.length > 0 && document.buffer.charAt(0) == 'A') {
                    firstEntered.countDown()
                    releaseFirst.await(5, TimeUnit.SECONDS)
                    buffer.applyStyle(fromChar, toChar, TokenType.STRING)
                } else {
                    buffer.applyStyle(fromChar, toChar, TokenType.NUMBER)
                }
            }
        }
        val styles = StyleBuffer(4)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lexer = IdleLexer(styles, provider) {}
        try {
            lexer.scheduleFrom(0, "AAAA", scope)
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS), "First revision never started")
            lexer.scheduleFrom(0, "BBBB", scope)
            releaseFirst.countDown()
            withTimeout(5_000) {
                while (styles.endStyled < 4 || styles.styleAt(0) != TokenType.NUMBER) delay(10)
            }
            assertEquals(TokenType.NUMBER, styles.styleAt(0))
        } finally {
            releaseFirst.countDown()
            lexer.cancelAndWait()
            scope.cancel()
        }
    }

    @Test
    fun concurrent_large_replacements_and_typing_never_publish_obsolete_styles() {
        val failure = AtomicReference<Throwable?>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> failure.compareAndSet(null, error) }
        val editor = EditorViewModel("{\"items\":[" + "1,".repeat(100_000) + "0]}", LanguageMode.JSON)
        try {
            repeat(18) { index ->
                SwingUtilities.invokeAndWait {
                    editor.onExternalTextChanged(
                        "{\"revision\":$index,\"items\":[" + "$index,".repeat(65_000) + "0]}"
                    )
                    editor.moveCursorTo(editor.document.length - 2)
                    editor.insertAtCursor(" ")
                    editor.onVisibleRangeChanged(0, 1)
                }
            }
            val deadline = System.currentTimeMillis() + 10_000L
            while (editor.styleBuffer.endStyled < editor.document.length &&
                System.currentTimeMillis() < deadline && failure.get() == null
            ) Thread.sleep(20)
            assertNull(failure.get(), "Background lexer threw an exception")
            assertEquals(editor.document.version, editor.state.value.version)
            assertTrue(editor.getFullText().contains("\"revision\":17"))
            assertNotEquals("", editor.getFullText())
            assertEquals(editor.document.length, editor.styleBuffer.endStyled)
        } finally {
            editor.dispose()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}
