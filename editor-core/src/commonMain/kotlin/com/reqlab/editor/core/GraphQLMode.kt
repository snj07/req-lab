package com.reqlab.editor.core

object GraphQLMode : LanguageModeProvider {
    override val mode = LanguageMode.GRAPHQL
    override val displayName = "GraphQL"
    override val fileExtensions = listOf("graphql", "gql")
    override val mimeTypes = listOf("application/graphql")
    override val foldingStyle = FoldingStyle.BRACE

    private val KEYWORDS = setOf(
        "query", "mutation", "subscription", "fragment", "on", "type", "input",
        "enum", "interface", "union", "scalar", "extend", "schema", "directive",
        "implements", "repeatable",
    )

    override fun tokenizeLine(line: String, lineNumber: Int, state: Any?): Pair<List<Token>, Any?> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val len = line.length

        while (i < len) {
            val ch = line[i]
            when {
                ch == '#' -> {
                    tokens.add(Token(i, len, TokenType.COMMENT))
                    i = len
                }
                ch == '"' -> {
                    val end = findStringEnd(line, i + 1)
                    tokens.add(Token(i, end, TokenType.STRING))
                    i = end
                }
                ch == '@' -> {
                    val end = findWordEnd(line, i + 1)
                    tokens.add(Token(i, end, TokenType.ATTRIBUTE))
                    i = end
                }
                ch == '$' -> {
                    val end = findWordEnd(line, i + 1)
                    tokens.add(Token(i, end, TokenType.PROPERTY))
                    i = end
                }
                ch.isDigit() || (ch == '-' && i + 1 < len && line[i + 1].isDigit()) -> {
                    val end = scanNumber(line, i)
                    tokens.add(Token(i, end, TokenType.NUMBER))
                    i = end
                }
                ch.isLetter() || ch == '_' -> {
                    val end = findWordEnd(line, i)
                    val word = line.substring(i, end)
                    val type = when {
                        word in KEYWORDS -> TokenType.KEYWORD
                        word == "true" || word == "false" || word == "null" -> TokenType.KEYWORD
                        word[0].isUpperCase() -> TokenType.TAG
                        else -> TokenType.PROPERTY
                    }
                    tokens.add(Token(i, end, type))
                    i = end
                }
                ch in "{}()[]!:=|&" -> {
                    tokens.add(Token(i, i + 1, TokenType.PUNCTUATION))
                    i++
                }
                ch.isWhitespace() -> i++
                else -> {
                    tokens.add(Token(i, i + 1, TokenType.PLAIN))
                    i++
                }
            }
        }
        return tokens to null
    }

    private fun findStringEnd(line: String, start: Int): Int {
        var i = start
        while (i < line.length) {
            when (line[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        return line.length
    }

    private fun findWordEnd(line: String, start: Int): Int {
        var i = start
        while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) i++
        return i
    }

    private fun scanNumber(line: String, start: Int): Int {
        var i = start
        if (i < line.length && line[i] == '-') i++
        while (i < line.length && line[i].isDigit()) i++
        if (i < line.length && line[i] == '.') { i++; while (i < line.length && line[i].isDigit()) i++ }
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
                '{', '(' -> stack.addLast(line)
                '}', ')' -> {
                    if (stack.isNotEmpty()) {
                        val s = stack.removeLast()
                        if (line > s) regions.add(FoldRegion(s, line))
                    }
                }
                '\n' -> line++
            }
        }
        return regions.sortedBy { it.startLine }
    }

    override fun validate(text: String): List<InlineEditorError> {
        if (text.isBlank()) return emptyList()
        val errors = mutableListOf<InlineEditorError>()
        val stack = ArrayDeque<Triple<Char, Int, Int>>()
        var line = 1; var col = 0; var inStr = false; var escaped = false
        for (ch in text) {
            if (ch == '\n') { line++; col = 0 } else col++
            if (escaped) { escaped = false; continue }
            if (ch == '\\' && inStr) { escaped = true; continue }
            if (ch == '"') { inStr = !inStr; continue }
            if (inStr) continue
            when (ch) {
                '{', '(' -> stack.addLast(Triple(ch, line, col))
                '}', ')' -> {
                    val expected = if (ch == '}') '{' else '('
                    if (stack.isEmpty()) {
                        errors += InlineEditorError(line, col, "Unexpected '$ch'")
                    } else if (stack.last().first != expected) {
                        val exp = if (stack.last().first == '{') '}' else ')'
                        errors += InlineEditorError(line, col, "Expected '$exp' but got '$ch'")
                        stack.removeLast()
                    } else {
                        stack.removeLast()
                    }
                }
            }
        }
        for ((open, l, c) in stack.asReversed()) {
            val close = if (open == '{') '}' else ')'
            errors += InlineEditorError(l, c, "Unclosed '$open' — expected '$close'")
        }
        return errors
    }
}
