package com.reqlab.editor.ui

import com.reqlab.editor.core.JsonMode
import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultSyntaxHighlighterTest {

    @Test
    fun mode_always_matches_the_provider() {
        val highlighter = DefaultSyntaxHighlighter(JsonMode)
        assertEquals(LanguageMode.JSON, highlighter.mode)
    }
}
