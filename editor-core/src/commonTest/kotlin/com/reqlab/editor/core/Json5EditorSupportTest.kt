package com.reqlab.editor.core

import com.reqlab.core.model.json.Json5
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Json5EditorSupportTest {

    @BeforeTest
    fun setup() {
        LanguageRegistry.registerBuiltins()
    }

    @Test
    fun tokenize_line_and_block_comments_as_comment() {
        val (line, _) = Json5EditorSupport.tokenizeLine("""{ a: 1, // skip""", 1, null)
        assertTrue(line.any { it.type == TokenType.COMMENT }, line.toString())
        val (block, _) = Json5EditorSupport.tokenizeLine("""{ /* x */ "k": 1 }""", 1, null)
        assertTrue(block.any { it.type == TokenType.COMMENT }, block.toString())
    }

    @Test
    fun tokenize_unquoted_key_as_property() {
        val (tokens, _) = Json5EditorSupport.tokenizeLine("name: 'Ada'", 1, null)
        assertTrue(tokens.any { it.type == TokenType.PROPERTY }, tokens.toString())
        assertTrue(tokens.any { it.type == TokenType.STRING }, tokens.toString())
    }

    @Test
    fun validate_accepts_trailing_comma_and_comments() {
        val errors = Json5EditorSupport.validate(
            """
            {
              "name": "Ada",
              // "role": "admin",
              "active": true,
            }
            """.trimIndent(),
        )
        assertEquals(0, errors.size, errors.toString())
    }

    @Test
    fun format_of_valid_json_matches_json_mode() {
        val compact = """{"name":"Alice","age":30}"""
        assertEquals(JsonMode.format(compact), Json5EditorSupport.format(compact))
    }

    @Test
    fun format_json5_preserves_comments_and_unquoted_keys() {
        val text = """
            {a:1, // keep
            b: 'Ada',}
        """.trimIndent()
        val formatted = Json5EditorSupport.format(text)
        assertTrue(formatted.contains("//"), formatted)
        assertTrue(formatted.contains("keep"), formatted)
        assertTrue(formatted.contains("a"), formatted)
        assertTrue(!formatted.contains("\"a\""), formatted)
        assertTrue(formatted.contains("'Ada'"), formatted)
        assertTrue(formatted.contains(","), formatted)
        assertTrue(formatted.lines().size > 1, formatted)
    }

    @Test
    fun format_json5_twice_still_parses_and_keeps_dialect() {
        val text = """{ a: 1, // c
b: 'x', }"""
        val once = Json5EditorSupport.format(text)
        val twice = Json5EditorSupport.format(once)
        assertTrue(once.contains("//"), once)
        assertTrue(once.contains("a"), once)
        assertTrue(!once.contains("\"a\""), once)
        assertTrue(Json5.parseToJsonElement(once).isSuccess, once)
        assertTrue(Json5.parseToJsonElement(twice).isSuccess, twice)
        assertTrue(twice.contains("//"), twice)
        assertTrue(twice.contains("'x'"), twice)
        assertEquals(once, twice)
    }

    @Test
    fun format_json5_invalid_returns_original() {
        val text = "{ a: "
        assertEquals(text, Json5EditorSupport.format(text))
    }

    @Test
    fun format_json5_preserves_wire_json_for_dialect_fixtures() {
        val fixtures = listOf(
            "{a:1, // keep\nb: 'Ada',}",
            "{a:1,}",
            """{a:"x{y}"}""",
            "{a:0xFF}",
            "{a:.5}",
            "[]",
            "{}",
        )
        for (input in fixtures) {
            val formatted = Json5EditorSupport.format(input)
            val originalWire = Json5.toCanonicalJson(input).getOrThrow()
            val formattedWire = Json5.toCanonicalJson(formatted).getOrThrow()
            assertEquals(originalWire, formattedWire, "format changed meaning for: $input\nformatted=$formatted")
            if (input.contains("x{y}")) {
                assertTrue(formatted.contains("x{y}"), formatted)
            }
            if (input.contains("0xFF")) {
                assertTrue(formatted.contains("0xFF"), formatted)
            }
        }
    }

    @Test
    fun folding_ignores_braces_inside_comments() {
        val text = """
            {
              // { not a fold
              "a": 1
            }
        """.trimIndent()
        val regions = Json5EditorSupport.foldingRegions(EditorDocument.create(text))
        assertEquals(1, regions.size)
        assertEquals(1, regions[0].startLine)
        assertEquals(4, regions[0].endLine)
    }
}
