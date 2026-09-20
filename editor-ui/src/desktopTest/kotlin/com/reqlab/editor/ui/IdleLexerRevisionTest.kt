package com.reqlab.editor.ui

import com.reqlab.editor.core.LanguageMode
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdleLexerRevisionTest {
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
