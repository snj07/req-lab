package com.reqlab.editor.ui

import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.StyleBuffer
import com.reqlab.editor.core.TokenType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Behavioral regressions reported after the editor rendering changes. */
class EditorRenderingRegressionTest {

    @AfterTest
    fun resetRegistries() {
        TokenColorRegistry.clear()
    }

    @Test
    fun light_theme_string_tokens_use_the_light_theme_palette() {
        TokenColorRegistry.registerBuiltinSchemes()

        assertEquals(
            expected = EditorTheme.Light.tokenColors.getValue(TokenType.STRING),
            actual = colorForToken(TokenType.STRING, LanguageMode.JSON),
            message = "A light editor must not render JSON strings with the hard-coded dark palette",
        )
    }

    @Test
    fun invalidated_style_bytes_are_not_exposed_as_current_styles() {
        val buffer = StyleBuffer(32)
        buffer.applyStyle(0, 16, TokenType.STRING)
        buffer.applyStyle(6, 12, TokenType.NUMBER)
        buffer.invalidateFrom(6)

        assertEquals(6, buffer.endStyled)
        assertEquals(
            expected = TokenType.PLAIN,
            actual = buffer.styleAt(8),
            message = "Styles at or beyond endStyled are stale and must not be rendered",
        )
    }

    @Test
    fun json_string_content_with_dots_escapes_unicode_and_templates_stays_string_styled() {
        val values = listOf(
            "api.example.com/v1.2/items",
            "letters123.after.dot",
            "escaped \\\"quote\\\" and \\\\ slash",
            "Grüße-世界",
            "{{baseUrl}}/users/{{userId}}",
        )
        val json = values.joinToString(prefix = "[", postfix = "]") { value -> "\"$value\"" }
        val viewModel = EditorViewModel(json, LanguageMode.JSON)

        for (value in values) {
            val start = json.indexOf(value)
            for (offset in start until start + value.length) {
                assertEquals(
                    TokenType.STRING,
                    viewModel.styleBuffer.styleAt(offset),
                    "Character '${json[offset]}' at $offset unexpectedly lost its string style",
                )
            }
        }
        viewModel.dispose()
    }

    @Test
    fun editing_inside_a_json_string_restyles_the_complete_line_immediately() {
        val original = "{\"url\":\"https://api.example.com/v1\"}"
        val viewModel = EditorViewModel(original, LanguageMode.JSON)
        val insertionPoint = original.indexOf("example") + "example".length

        viewModel.moveCursorTo(insertionPoint)
        viewModel.insertAtCursor(".internal")

        val edited = viewModel.getFullText()
        val valueStart = edited.indexOf("https://")
        val valueEnd = edited.lastIndexOf('"')
        for (offset in valueStart until valueEnd) {
            assertEquals(TokenType.STRING, viewModel.styleBuffer.styleAt(offset))
        }
        viewModel.dispose()
    }

    @Test
    fun light_and_dark_themes_keep_distinct_non_plain_string_colors() {
        val lightString = colorForToken(TokenType.STRING, LanguageMode.JSON, EditorTheme.Light)
        val darkString = colorForToken(TokenType.STRING, LanguageMode.JSON, EditorTheme.Dark)

        assertNotEquals(EditorTheme.Light.foreground, lightString)
        assertNotEquals(EditorTheme.Dark.foreground, darkString)
        assertNotEquals(lightString, darkString)
    }
}
