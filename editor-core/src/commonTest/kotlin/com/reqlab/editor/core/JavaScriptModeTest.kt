package com.reqlab.editor.core

import kotlin.test.*

class JavaScriptModeTest {

    @BeforeTest
    fun setup() {
        LanguageRegistry.registerBuiltins()
    }

    @Test
    fun validJsNoErrors() {
        val js = "function hello() { return 42; }"
        val errors = JavaScriptMode.validate(js)
        assertEquals(0, errors.size)
    }

    @Test
    fun unmatchedBraceReportsError() {
        val js = "function hello() { return 42; "
        val errors = JavaScriptMode.validate(js)
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun nestedBracesValid() {
        val js = """
function outer() {
    if (true) {
        for (let i = 0; i < 10; i++) {
            console.log(i);
        }
    }
}
        """.trimIndent()
        val errors = JavaScriptMode.validate(js)
        assertEquals(0, errors.size)
    }

    @Test
    fun tokenizeKeyword() {
        val (tokens, _) = JavaScriptMode.tokenizeLine("const x = 42;", 1, null)
        assertTrue(tokens.any { it.type == TokenType.KEYWORD })
    }

    @Test
    fun tokenizeString() {
        val (tokens, _) = JavaScriptMode.tokenizeLine("""const s = "hello";""", 1, null)
        assertTrue(tokens.any { it.type == TokenType.STRING })
    }

    @Test
    fun tokenizeNumber() {
        val (tokens, _) = JavaScriptMode.tokenizeLine("const n = 3.14;", 1, null)
        assertTrue(tokens.any { it.type == TokenType.NUMBER })
    }

    @Test
    fun tokenizeComment() {
        val (tokens, _) = JavaScriptMode.tokenizeLine("// this is a comment", 1, null)
        assertTrue(tokens.any { it.type == TokenType.COMMENT })
    }

    @Test
    fun foldingRegionsForJs() {
        val js = "function test() {\n  if (true) {\n    return 1;\n  }\n}"
        val doc = EditorDocument.create(js)
        val regions = JavaScriptMode.foldingRegions(doc)
        assertTrue(regions.isNotEmpty(), "Should detect fold regions for JS blocks")
    }

    @Test
    fun bracketMatchingWithStrings() {
        // Braces inside strings should not be counted
        val js = """const s = "{ not a brace }";"""
        val errors = JavaScriptMode.validate(js)
        assertEquals(0, errors.size)
    }

    @Test
    fun bracketMatchingWithComments() {
        // Braces inside comments should not be counted
        val js = """
// { not a brace }
function f() {
    return 1;
}
        """.trimIndent()
        val errors = JavaScriptMode.validate(js)
        assertEquals(0, errors.size)
    }
}
