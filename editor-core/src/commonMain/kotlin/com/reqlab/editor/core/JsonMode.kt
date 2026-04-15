package com.reqlab.editor.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object JsonMode : LanguageModeProvider {
    override val mode = LanguageMode.JSON
    override val displayName = "JSON"
    override val fileExtensions = listOf("json", "jsonc", "geojson")
    override val mimeTypes = listOf("application/json", "text/json")
    override val foldingStyle = FoldingStyle.BRACE
    private const val LARGE_FILE_THRESHOLD = 1_000_000

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun format(text: String): String = try {
        val element = prettyJson.decodeFromString(JsonElement.serializer(), text)
        prettyJson.encodeToString(JsonElement.serializer(), element)
    } catch (_: Throwable) { text }

    override fun tokenizeLine(line: String, lineNumber: Int, state: Any?): Pair<List<Token>, Any?> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val inStringFromPrev = (state as? Boolean) ?: false
        var inString = inStringFromPrev

        if (inString) {
            val endQuote = findStringEnd(line, 0)
            tokens.add(Token(0, endQuote, TokenType.STRING))
            i = endQuote
            if (endQuote < line.length) inString = false
        }

        while (i < line.length) {
            val c = line[i]
            when {
                c.isWhitespace() -> i++
                c == '"' -> {
                    val end = findStringEnd(line, i + 1)
                    tokens.add(Token(i, end, TokenType.STRING))
                    i = end
                    inString = end >= line.length && line.lastOrNull() != '"'
                }
                c == ':' || c == ',' -> { tokens.add(Token(i, i + 1, TokenType.PUNCTUATION)); i++ }
                c == '{' || c == '}' || c == '[' || c == ']' -> { tokens.add(Token(i, i + 1, TokenType.PUNCTUATION)); i++ }
                c == 't' || c == 'f' -> {
                    val word = if (c == 't') "true" else "false"
                    if (line.startsWith(word, i)) { tokens.add(Token(i, i + word.length, TokenType.KEYWORD)); i += word.length }
                    else { tokens.add(Token(i, i + 1, TokenType.ERROR)); i++ }
                }
                c == 'n' -> {
                    if (line.startsWith("null", i)) { tokens.add(Token(i, i + 4, TokenType.KEYWORD)); i += 4 }
                    else { tokens.add(Token(i, i + 1, TokenType.ERROR)); i++ }
                }
                c == '-' || c.isDigit() -> { val end = scanNumber(line, i); tokens.add(Token(i, end, TokenType.NUMBER)); i = end }
                else -> { tokens.add(Token(i, i + 1, TokenType.ERROR)); i++ }
            }
        }
        return tokens to inString
    }

    private fun findStringEnd(line: String, start: Int): Int {
        var i = start
        while (i < line.length) { when (line[i]) { '\\' -> i += 2; '"' -> return i + 1; else -> i++ } }
        return line.length
    }

    private fun scanNumber(line: String, start: Int): Int {
        var i = start
        if (i < line.length && line[i] == '-') i++
        while (i < line.length && line[i].isDigit()) i++
        if (i < line.length && line[i] == '.') { i++; while (i < line.length && line[i].isDigit()) i++ }
        if (i < line.length && (line[i] == 'e' || line[i] == 'E')) {
            i++; if (i < line.length && (line[i] == '+' || line[i] == '-')) i++
            while (i < line.length && line[i].isDigit()) i++
        }
        return i
    }

    override fun foldingRegions(document: EditorDocument): List<FoldRegion> {
        val regions = mutableListOf<FoldRegion>()
        val stack = ArrayDeque<Int>()
        val text = document.text
        var line = 1; var inStr = false; var escaped = false
        for (ch in text) {
            if (escaped) { escaped = false; continue }
            if (ch == '\\' && inStr) { escaped = true; continue }
            if (ch == '"') { inStr = !inStr; continue }
            if (inStr) { if (ch == '\n') line++; continue }
            when (ch) {
                '{', '[' -> stack.addLast(line)
                '}', ']' -> { if (stack.isNotEmpty()) { val s = stack.removeLast(); if (line > s) regions.add(FoldRegion(s, line)) } }
                '\n' -> line++
            }
        }
        return regions.sortedBy { it.startLine }
    }

    override fun validate(text: String): List<InlineEditorError> {
        if (text.isBlank()) return emptyList()
        return if (text.length > LARGE_FILE_THRESHOLD) validateLightweight(text) else validateFull(text)
    }

    private fun validateFull(text: String): List<InlineEditorError> {
        return try { Json.parseToJsonElement(text); emptyList() }
        catch (e: Throwable) {
            val offset = extractOffset(e.message.orEmpty())?.coerceIn(0, text.length) ?: 0
            val pos = offsetToLineCol(text, offset)
            listOf(InlineEditorError(pos.first, pos.second, e.message ?: "Invalid JSON"))
        }
    }

    private fun validateLightweight(text: String): List<InlineEditorError> {
        val errors = mutableListOf<InlineEditorError>()
        val stack = ArrayDeque<Triple<Char, Int, Int>>()
        var line = 1; var col = 0; var inStr = false; var escaped = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') { line++; col = 0 } else col++
            if (escaped) { escaped = false; i++; continue }
            if (c == '\\' && inStr) { escaped = true; i++; continue }
            if (c == '"') { inStr = !inStr; i++; continue }
            if (inStr) { i++; continue }
            if (c.isWhitespace() || c == ':' || c == ',') { i++; continue }
            when (c) {
                '{', '[' -> stack.addLast(Triple(c, line, col))
                '}', ']' -> {
                    val m = if (c == '}') '{' else '['
                    if (stack.isEmpty()) { errors += InlineEditorError(line, col, "Unexpected '$c'"); return errors }
                    else if (stack.last().first != m) {
                        val exp = if (stack.last().first == '{') '}' else ']'
                        errors += InlineEditorError(line, col, "Expected '$exp' but got '$c' (opened at line ${stack.last().second})")
                        return errors
                    } else stack.removeLast()
                }
            }
            i++
        }
        if (inStr) errors += InlineEditorError(line, col, "Unterminated string")
        for ((open, l, c2) in stack.asReversed()) {
            errors += InlineEditorError(l, c2, "Unclosed '$open' — expected '${if (open == '{') '}' else ']'}'")
        }
        return errors
    }

    private fun extractOffset(message: String): Int? {
        val idx = message.indexOf("offset ")
        if (idx == -1) return null
        val start = idx + 7
        var end = start; while (end < message.length && message[end].isDigit()) end++
        return message.substring(start, end).toIntOrNull()
    }

    private fun offsetToLineCol(text: String, offset: Int): Pair<Int, Int> {
        var line = 1; var col = 1
        for (i in 0 until minOf(offset, text.length)) { if (text[i] == '\n') { line++; col = 1 } else col++ }
        return line to col
    }
}
