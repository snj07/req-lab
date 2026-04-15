package com.reqlab.editor.core

object JavaScriptMode : LanguageModeProvider {
    override val mode = LanguageMode.JAVASCRIPT
    override val displayName = "JavaScript"
    override val fileExtensions = listOf("js", "mjs", "cjs", "jsx")
    override val mimeTypes = listOf("application/javascript", "text/javascript")
    override val foldingStyle = FoldingStyle.BRACE

    private val KEYWORDS = setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger",
        "default", "delete", "do", "else", "export", "extends", "false",
        "finally", "for", "function", "if", "import", "in", "instanceof",
        "let", "new", "null", "return", "super", "switch", "this", "throw",
        "true", "try", "typeof", "undefined", "var", "void", "while", "with",
        "yield", "async", "await", "of", "static", "get", "set",
    )

    data class JsTokenState(val inBlockComment: Boolean = false, val inTemplateLiteral: Boolean = false)

    override fun tokenizeLine(line: String, lineNumber: Int, state: Any?): Pair<List<Token>, Any?> {
        val tokens = mutableListOf<Token>()
        val prev = state as? JsTokenState ?: JsTokenState()
        var i = 0; var inBlock = prev.inBlockComment; var inTmpl = prev.inTemplateLiteral

        if (inBlock) {
            val endIdx = line.indexOf("*/")
            if (endIdx >= 0) { tokens.add(Token(0, endIdx + 2, TokenType.COMMENT)); i = endIdx + 2; inBlock = false }
            else { tokens.add(Token(0, line.length, TokenType.COMMENT)); return tokens to JsTokenState(true, inTmpl) }
        }
        if (inTmpl) {
            val endIdx = findUnescaped(line, '`', 0)
            if (endIdx >= 0) { tokens.add(Token(0, endIdx + 1, TokenType.STRING)); i = endIdx + 1; inTmpl = false }
            else { tokens.add(Token(0, line.length, TokenType.STRING)); return tokens to JsTokenState(false, true) }
        }

        while (i < line.length) {
            val c = line[i]
            when {
                c.isWhitespace() -> i++
                c == '/' && i + 1 < line.length && line[i + 1] == '/' -> { tokens.add(Token(i, line.length, TokenType.COMMENT)); i = line.length }
                c == '/' && i + 1 < line.length && line[i + 1] == '*' -> {
                    val endIdx = line.indexOf("*/", i + 2)
                    if (endIdx >= 0) { tokens.add(Token(i, endIdx + 2, TokenType.COMMENT)); i = endIdx + 2 }
                    else { tokens.add(Token(i, line.length, TokenType.COMMENT)); inBlock = true; i = line.length }
                }
                c == '"' || c == '\'' -> {
                    val end = findUnescaped(line, c, i + 1).let { if (it >= 0) it + 1 else line.length }
                    tokens.add(Token(i, end, TokenType.STRING)); i = end
                }
                c == '`' -> {
                    val endIdx = findUnescaped(line, '`', i + 1)
                    if (endIdx >= 0) { tokens.add(Token(i, endIdx + 1, TokenType.STRING)); i = endIdx + 1 }
                    else { tokens.add(Token(i, line.length, TokenType.STRING)); inTmpl = true; i = line.length }
                }
                c.isDigit() || (c == '.' && i + 1 < line.length && line[i + 1].isDigit()) -> {
                    val end = scanNumber(line, i); tokens.add(Token(i, end, TokenType.NUMBER)); i = end
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    val end = scanIdent(line, i); val word = line.substring(i, end)
                    tokens.add(Token(i, end, if (word in KEYWORDS) TokenType.KEYWORD else TokenType.PLAIN)); i = end
                }
                c in "{}[]()" -> { tokens.add(Token(i, i + 1, TokenType.PUNCTUATION)); i++ }
                c in "=+-*%!&|^~<>?:" -> { tokens.add(Token(i, i + 1, TokenType.OPERATOR)); i++ }
                c in ";,." -> { tokens.add(Token(i, i + 1, TokenType.PUNCTUATION)); i++ }
                else -> { tokens.add(Token(i, i + 1, TokenType.PLAIN)); i++ }
            }
        }
        return tokens to JsTokenState(inBlock, inTmpl)
    }

    private fun findUnescaped(line: String, target: Char, start: Int): Int {
        var i = start
        while (i < line.length) { if (line[i] == '\\') { i += 2; continue }; if (line[i] == target) return i; i++ }
        return -1
    }

    private fun scanNumber(line: String, start: Int): Int {
        var i = start
        if (i + 1 < line.length && line[i] == '0' && (line[i + 1] == 'x' || line[i + 1] == 'X')) {
            i += 2; while (i < line.length && line[i].isLetterOrDigit()) i++; return i
        }
        while (i < line.length && line[i].isDigit()) i++
        if (i < line.length && line[i] == '.') { i++; while (i < line.length && line[i].isDigit()) i++ }
        if (i < line.length && (line[i] == 'e' || line[i] == 'E')) {
            i++; if (i < line.length && (line[i] == '+' || line[i] == '-')) i++
            while (i < line.length && line[i].isDigit()) i++
        }; return i
    }

    private fun scanIdent(line: String, start: Int): Int {
        var i = start; while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$')) i++; return i
    }

    override fun foldingRegions(document: EditorDocument): List<FoldRegion> {
        val regions = mutableListOf<FoldRegion>()
        val stack = ArrayDeque<Int>()
        val text = document.text
        var line = 1; var inStr: Char? = null; var inLC = false; var inBC = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') { line++; inLC = false; i++; continue }
            if (inStr == null && !inLC && !inBC && c == '/' && i + 1 < text.length && text[i + 1] == '*') { inBC = true; i += 2; continue }
            if (inBC && c == '*' && i + 1 < text.length && text[i + 1] == '/') { inBC = false; i += 2; continue }
            if (inBC) { i++; continue }
            if (inStr == null && !inLC && c == '/' && i + 1 < text.length && text[i + 1] == '/') { inLC = true; i++; continue }
            if (inLC) { i++; continue }
            if (inStr == null && (c == '"' || c == '\'' || c == '`')) inStr = c
            else if (inStr != null && c == inStr && (i == 0 || text[i - 1] != '\\')) inStr = null
            if (inStr != null) { i++; continue }
            when (c) {
                '{' -> stack.addLast(line)
                '}' -> { if (stack.isNotEmpty()) { val s = stack.removeLast(); if (line > s) regions.add(FoldRegion(s, line)) } }
            }; i++
        }
        return regions.sortedBy { it.startLine }
    }

    override fun validate(text: String): List<InlineEditorError> {
        if (text.isBlank()) return emptyList()
        val errors = mutableListOf<InlineEditorError>()
        val stack = ArrayDeque<Triple<Char, Int, Int>>()
        var line = 1; var col = 0; var inLC = false; var inBC = false; var inStr: Char? = null; var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') { line++; col = 0; inLC = false } else col++
            if (inStr == null && !inLC && !inBC && c == '/' && i + 1 < text.length && text[i + 1] == '*') { inBC = true; i += 2; col++; continue }
            if (inBC && c == '*' && i + 1 < text.length && text[i + 1] == '/') { inBC = false; i += 2; col++; continue }
            if (inBC) { i++; continue }
            if (inStr == null && !inLC && c == '/' && i + 1 < text.length && text[i + 1] == '/') inLC = true
            if (inLC) { i++; continue }
            if (inStr == null && (c == '"' || c == '\'' || c == '`')) inStr = c
            else if (inStr != null && c == inStr && (i == 0 || text[i - 1] != '\\')) inStr = null
            if (inStr != null) { i++; continue }
            when (c) {
                '{', '[', '(' -> stack.addLast(Triple(c, line, col))
                '}', ']', ')' -> {
                    val m = when (c) { '}' -> '{'; ']' -> '['; else -> '(' }
                    if (stack.isEmpty()) errors += InlineEditorError(line, col, "Unexpected '$c'")
                    else if (stack.last().first != m) {
                        val exp = when (stack.last().first) { '{' -> '}'; '[' -> ']'; else -> ')' }
                        errors += InlineEditorError(line, col, "Expected '$exp' but got '$c' (opened at line ${stack.last().second})")
                        stack.removeLast()
                    } else stack.removeLast()
                }
            }; i++
        }
        for ((open, l, c2) in stack.asReversed()) {
            val exp = when (open) { '{' -> '}'; '[' -> ']'; else -> ')' }
            errors += InlineEditorError(l, c2, "Unclosed '$open' — expected '$exp'")
        }
        return errors
    }
}
