package com.reqlab.editor.core

import com.reqlab.core.model.json.Json5
import com.reqlab.core.model.json.Json5ParseException

/**
 * JSON5 editor adapter. Used only when the JSON5 setting is on.
 * [JsonMode] remains the strict JSON provider.
 */
object Json5EditorSupport : LanguageModeProvider {
    override val mode = LanguageMode.JSON
    override val displayName = "JSON5"
    override val fileExtensions = listOf("json5", "jsonc")
    override val mimeTypes = listOf("application/json", "text/json")
    override val foldingStyle = FoldingStyle.BRACE

    data class TokenState(
        val inBlockComment: Boolean = false,
        val inString: Char? = null,
    )

    override fun tokenizeLine(line: String, lineNumber: Int, state: Any?): Pair<List<Token>, Any?> {
        val tokens = mutableListOf<Token>()
        val prev = state as? TokenState ?: TokenState()
        var i = 0
        var inBlock = prev.inBlockComment
        var inString = prev.inString

        if (inBlock) {
            val endIdx = line.indexOf("*/")
            if (endIdx >= 0) {
                tokens.add(Token(0, endIdx + 2, TokenType.COMMENT))
                i = endIdx + 2
                inBlock = false
            } else {
                tokens.add(Token(0, line.length, TokenType.COMMENT))
                return tokens to TokenState(true, inString)
            }
        }

        if (inString != null) {
            val end = findUnescaped(line, inString, 0)
            if (end >= 0) {
                tokens.add(Token(0, end + 1, TokenType.STRING))
                i = end + 1
                inString = null
            } else {
                tokens.add(Token(0, line.length, TokenType.STRING))
                return tokens to TokenState(false, inString)
            }
        }

        while (i < line.length) {
            val c = line[i]
            when {
                c.isWhitespace() -> i++
                c == '/' && i + 1 < line.length && line[i + 1] == '/' -> {
                    tokens.add(Token(i, line.length, TokenType.COMMENT))
                    i = line.length
                }
                c == '/' && i + 1 < line.length && line[i + 1] == '*' -> {
                    val endIdx = line.indexOf("*/", i + 2)
                    if (endIdx >= 0) {
                        tokens.add(Token(i, endIdx + 2, TokenType.COMMENT))
                        i = endIdx + 2
                    } else {
                        tokens.add(Token(i, line.length, TokenType.COMMENT))
                        inBlock = true
                        i = line.length
                    }
                }
                c == '"' || c == '\'' -> {
                    val end = findUnescaped(line, c, i + 1)
                    if (end >= 0) {
                        tokens.add(Token(i, end + 1, TokenType.STRING))
                        i = end + 1
                    } else {
                        tokens.add(Token(i, line.length, TokenType.STRING))
                        inString = c
                        i = line.length
                    }
                }
                c == ':' || c == ',' || c == '{' || c == '}' || c == '[' || c == ']' -> {
                    tokens.add(Token(i, i + 1, TokenType.PUNCTUATION)); i++
                }
                c == 't' || c == 'f' -> {
                    val word = if (c == 't') "true" else "false"
                    if (line.startsWith(word, i) && !continuesIdent(line, i + word.length)) {
                        tokens.add(Token(i, i + word.length, TokenType.KEYWORD)); i += word.length
                    } else {
                        val end = scanIdent(line, i)
                        tokens.add(Token(i, end, TokenType.PROPERTY)); i = end
                    }
                }
                c == 'n' -> {
                    if (line.startsWith("null", i) && !continuesIdent(line, i + 4)) {
                        tokens.add(Token(i, i + 4, TokenType.KEYWORD)); i += 4
                    } else {
                        val end = scanIdent(line, i)
                        tokens.add(Token(i, end, TokenType.PROPERTY)); i = end
                    }
                }
                c == '+' || c == '-' || c == '.' || c.isDigit() -> {
                    val end = scanNumber(line, i)
                    tokens.add(Token(i, end, TokenType.NUMBER)); i = end
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    val end = scanIdent(line, i)
                    tokens.add(Token(i, end, TokenType.PROPERTY)); i = end
                }
                else -> { tokens.add(Token(i, i + 1, TokenType.ERROR)); i++ }
            }
        }
        return tokens to TokenState(inBlock, inString)
    }

    override fun foldingRegions(document: EditorDocument): List<FoldRegion> {
        val regions = mutableListOf<FoldRegion>()
        val stack = ArrayDeque<Int>()
        val text = document.text
        var line = 1
        var inStr: Char? = null
        var escaped = false
        var inLineComment = false
        var inBlockComment = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inLineComment) {
                if (ch == '\n') { inLineComment = false; line++ }
                i++; continue
            }
            if (inBlockComment) {
                if (ch == '*' && i + 1 < text.length && text[i + 1] == '/') {
                    inBlockComment = false; i += 2; continue
                }
                if (ch == '\n') line++
                i++; continue
            }
            if (escaped) { escaped = false; i++; continue }
            if (inStr != null) {
                if (ch == '\\') { escaped = true; i++; continue }
                if (ch == inStr) inStr = null
                if (ch == '\n') line++
                i++; continue
            }
            when {
                ch == '/' && i + 1 < text.length && text[i + 1] == '/' -> { inLineComment = true; i += 2 }
                ch == '/' && i + 1 < text.length && text[i + 1] == '*' -> { inBlockComment = true; i += 2 }
                ch == '"' || ch == '\'' -> { inStr = ch; i++ }
                ch == '{' || ch == '[' -> { stack.addLast(line); i++ }
                ch == '}' || ch == ']' -> {
                    if (stack.isNotEmpty()) {
                        val s = stack.removeLast()
                        if (line > s) regions.add(FoldRegion(s, line))
                    }
                    i++
                }
                ch == '\n' -> { line++; i++ }
                else -> i++
            }
        }
        return regions.sortedBy { it.startLine }
    }

    override fun validate(text: String): List<InlineEditorError> {
        if (text.isBlank()) return emptyList()
        return Json5.parseToJsonElement(text).fold(
            onSuccess = { emptyList() },
            onFailure = { e ->
                val offset = (e as? Json5ParseException)?.offset
                    ?: extractOffset(e.message.orEmpty())
                    ?: 0
                val pos = offsetToLineCol(text, offset.coerceIn(0, text.length))
                listOf(InlineEditorError(pos.first, pos.second, e.message ?: "Invalid JSON5"))
            },
        )
    }

    override fun format(text: String): String {
        val strict = JsonMode.format(text)
        if (strict != text) return strict
        if (Json5.parseToJsonElement(text).isFailure) return text
        val indented = indentJson5(text)
        return if (Json5.parseToJsonElement(indented).isFailure) text else indented
    }

    /**
     * Pretty-print JSON5 without rewriting quotes, keys, or comments.
     * Structural whitespace only: 2-space indent, newline after `{` `[` `,`,
     * space after `:`.
     */
    private fun indentJson5(text: String): String {
        if (text.isBlank()) return text
        val input = text.replace("\r\n", "\n").replace('\r', '\n')
        val out = StringBuilder(input.length + 64)
        var i = 0
        var indent = 0
        var atLineStart = true
        var pendingSpace = false

        fun appendIndentIfNeeded() {
            if (!atLineStart) return
            repeat(indent.coerceAtLeast(0)) { out.append("  ") }
            atLineStart = false
        }

        fun appendNewLine() {
            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
            atLineStart = true
            pendingSpace = false
        }

        fun flushPendingSpace() {
            if (pendingSpace && out.isNotEmpty() && out.last() != '\n' && out.last() != ' ') {
                out.append(' ')
            }
            pendingSpace = false
        }

        fun peek(offset: Int = 1): Char? = input.getOrNull(i + offset)

        fun isWs(c: Char) = c.isWhitespace() || c == '\uFEFF'

        while (i < input.length) {
            val c = input[i]
            val n = peek()

            if (isWs(c)) {
                i++
                continue
            }

            if (c == '/' && n == '/') {
                appendIndentIfNeeded()
                flushPendingSpace()
                out.append("//")
                i += 2
                while (i < input.length && input[i] != '\n') {
                    out.append(input[i])
                    i++
                }
                if (i < input.length && input[i] == '\n') {
                    out.append('\n')
                    atLineStart = true
                    pendingSpace = false
                    i++
                }
                continue
            }

            if (c == '/' && n == '*') {
                appendIndentIfNeeded()
                flushPendingSpace()
                out.append("/*")
                i += 2
                while (i < input.length) {
                    val ch = input[i]
                    out.append(ch)
                    if (ch == '\n') {
                        atLineStart = true
                        pendingSpace = false
                    } else {
                        atLineStart = false
                    }
                    if (ch == '*' && peek() == '/') {
                        out.append('/')
                        i += 2
                        pendingSpace = true
                        break
                    }
                    i++
                }
                continue
            }

            if (c == '"' || c == '\'') {
                appendIndentIfNeeded()
                flushPendingSpace()
                out.append(c)
                i++
                var escaped = false
                while (i < input.length) {
                    val ch = input[i]
                    out.append(ch)
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == c) {
                        i++
                        break
                    }
                    i++
                }
                atLineStart = false
                continue
            }

            when (c) {
                '{', '[' -> {
                    appendIndentIfNeeded()
                    flushPendingSpace()
                    out.append(c)
                    indent++
                    pendingSpace = false
                    appendNewLine()
                }
                '}', ']' -> {
                    indent = (indent - 1).coerceAtLeast(0)
                    if (!atLineStart) appendNewLine()
                    appendIndentIfNeeded()
                    out.append(c)
                    pendingSpace = false
                    atLineStart = false
                }
                ',' -> {
                    while (out.isNotEmpty() && out.last() == ' ') out.deleteAt(out.lastIndex)
                    out.append(',')
                    pendingSpace = false
                    appendNewLine()
                }
                ':' -> {
                    out.append(':')
                    pendingSpace = true
                }
                else -> {
                    appendIndentIfNeeded()
                    flushPendingSpace()
                    while (i < input.length) {
                        val ch = input[i]
                        if (isWs(ch) || ch == '{' || ch == '}' || ch == '[' || ch == ']' ||
                            ch == ',' || ch == ':' || ch == '"' || ch == '\'' ||
                            (ch == '/' && (peek() == '/' || peek() == '*'))
                        ) break
                        out.append(ch)
                        i++
                    }
                    atLineStart = false
                    continue
                }
            }
            i++
        }
        return out.toString().trimEnd()
    }

    private fun findUnescaped(line: String, quote: Char, start: Int): Int {
        var i = start
        while (i < line.length) {
            when (line[i]) {
                '\\' -> i += 2
                quote -> return i
                else -> i++
            }
        }
        return -1
    }

    private fun scanIdent(line: String, start: Int): Int {
        var i = start
        while (i < line.length) {
            val c = line[i]
            if (c.isLetterOrDigit() || c == '_' || c == '$') i++ else break
        }
        return i
    }

    private fun continuesIdent(line: String, index: Int): Boolean {
        if (index >= line.length) return false
        val c = line[index]
        return c.isLetterOrDigit() || c == '_' || c == '$'
    }

    private fun scanNumber(line: String, start: Int): Int {
        var i = start
        if (i < line.length && (line[i] == '+' || line[i] == '-')) i++
        if (i < line.length && line[i] == '0' && i + 1 < line.length && (line[i + 1] == 'x' || line[i + 1] == 'X')) {
            i += 2
            while (i < line.length && (line[i].isDigit() || line[i] in 'a'..'f' || line[i] in 'A'..'F')) i++
            return i
        }
        if (i < line.length && line[i] == '.') i++
        while (i < line.length && line[i].isDigit()) i++
        if (i < line.length && line[i] == '.') { i++; while (i < line.length && line[i].isDigit()) i++ }
        if (i < line.length && (line[i] == 'e' || line[i] == 'E')) {
            i++; if (i < line.length && (line[i] == '+' || line[i] == '-')) i++
            while (i < line.length && line[i].isDigit()) i++
        }
        return i
    }

    private fun extractOffset(message: String): Int? {
        val idx = message.indexOf("offset ")
        if (idx == -1) return null
        val start = idx + 7
        var end = start
        while (end < message.length && message[end].isDigit()) end++
        return message.substring(start, end).toIntOrNull()
    }

    private fun offsetToLineCol(text: String, offset: Int): Pair<Int, Int> {
        var line = 1
        var col = 1
        for (i in 0 until minOf(offset, text.length)) {
            if (text[i] == '\n') { line++; col = 1 } else col++
        }
        return line to col
    }
}
