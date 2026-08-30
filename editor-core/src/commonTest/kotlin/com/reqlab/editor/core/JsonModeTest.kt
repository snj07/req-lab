package com.reqlab.editor.core

import kotlin.test.*

class JsonModeTest {

    @BeforeTest
    fun setup() {
        LanguageRegistry.registerBuiltins()
    }

    @Test
    fun validJsonNoErrors() {
        val errors = JsonMode.validate("""{"key": "value", "num": 42}""")
        assertEquals(0, errors.size)
    }

    @Test
    fun invalidJsonReportsError() {
        val errors = JsonMode.validate("""{"key": "value",}""")
        assertTrue(errors.isNotEmpty())
        assertEquals(InlineErrorSeverity.ERROR, errors[0].severity)
    }

    @Test
    fun emptyObjectValid() {
        val errors = JsonMode.validate("{}")
        assertEquals(0, errors.size)
    }

    @Test
    fun emptyArrayValid() {
        val errors = JsonMode.validate("[]")
        assertEquals(0, errors.size)
    }

    @Test
    fun nestedJsonValid() {
        val json = """
        {
            "users": [
                {"name": "Alice", "age": 30},
                {"name": "Bob", "age": 25}
            ]
        }
        """.trimIndent()
        val errors = JsonMode.validate(json)
        assertEquals(0, errors.size)
    }

    @Test
    fun unmatchedBraceReportsError() {
        val errors = JsonMode.validate("""{"key": "value" """)
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun tokenizeJsonLine() {
        val (tokens, _) = JsonMode.tokenizeLine(""""key": "value",""", 1, null)
        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens.any { it.type == TokenType.STRING })
    }

    @Test
    fun tokenizeJsonNumber() {
        val (tokens, _) = JsonMode.tokenizeLine("42", 1, null)
        assertTrue(tokens.any { it.type == TokenType.NUMBER })
    }

    @Test
    fun tokenizeJsonKeyword() {
        val (tokens, _) = JsonMode.tokenizeLine("true", 1, null)
        assertTrue(tokens.any { it.type == TokenType.KEYWORD })
    }

    @Test
    fun foldingRegionsForNestedJson() {
        val json = """
{
    "outer": {
        "inner": [1, 2, 3]
    }
}
        """.trimIndent()
        val doc = EditorDocument.create(json)
        val regions = JsonMode.foldingRegions(doc)
        assertTrue(regions.isNotEmpty(), "Should detect fold regions for nested JSON")
    }

    @Test
    fun foldingRegionsObjectBraces() {
        val json = "{\n  \"a\": 1\n}"
        val doc = EditorDocument.create(json)
        val regions = JsonMode.foldingRegions(doc)
        assertTrue(regions.isNotEmpty())
        assertEquals(1, regions[0].startLine)
        assertEquals(3, regions[0].endLine)
    }

    @Test
    fun foldingRegionsArrayBrackets() {
        val json = "[\n  1,\n  2\n]"
        val doc = EditorDocument.create(json)
        val regions = JsonMode.foldingRegions(doc)
        assertTrue(regions.isNotEmpty())
        assertEquals(1, regions[0].startLine)
        assertEquals(4, regions[0].endLine)
    }

    @Test
    fun formatUnwrapsJsonEncodedObjectString() {
        val input = """{"text":"{\"jsonrpc\":\"2.0\",\"id\":\"srv-sample\"}"}"""
        val result = JsonMode.format(input)
        assertTrue(result.contains("\"text\": {") || result.contains("\"text\":{"), result)
        assertTrue(result.contains("\"srv-sample\""), result)
        assertTrue(!result.contains("\\\""), result)
    }

    @Test
    fun formatUnwrapsJsonEncodedArrayString() {
        val result = JsonMode.format("""{"items":"[1,2]"}""")
        assertTrue(result.contains("\"items\": [") || result.contains("\"items\":["), result)
        assertTrue(result.contains("1"), result)
    }

    @Test
    fun formatLeavesPlainString() {
        val result = JsonMode.format("""{"text":"hello"}""")
        assertTrue(result.contains("\"hello\""), result)
        assertTrue(!result.contains("\"text\": {") && !result.contains("\"text\":{"), result)
    }

    @Test
    fun formatPreservesNumericValue() {
        val result = JsonMode.format("""{"text":6}""")
        assertTrue(result.contains("\"text\": 6") || result.contains("\"text\":6"), result)
    }

    @Test
    fun formatLeavesBooleanAndNumberStrings() {
        val boolResult = JsonMode.format("""{"text":"true"}""")
        assertTrue(boolResult.contains("\"true\""), boolResult)
        val numResult = JsonMode.format("""{"text":"6"}""")
        assertTrue(numResult.contains("\"6\""), numResult)
        assertTrue(!numResult.contains("\"text\": 6") && !numResult.contains("\"text\":6"), numResult)
    }
}
