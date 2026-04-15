package com.reqlab.editor.core

object XmlMode : LanguageModeProvider {
    override val mode = LanguageMode.XML
    override val displayName = "XML"
    override val fileExtensions = listOf("xml", "xsl", "xslt", "xsd", "svg")
    override val mimeTypes = listOf("application/xml", "text/xml")
    override val foldingStyle = FoldingStyle.XML

    val VOID_ELEMENTS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr",
    )

    data class XmlTokenState(val inComment: Boolean = false)

    override fun tokenizeLine(line: String, lineNumber: Int, state: Any?): Pair<List<Token>, Any?> {
        val tokens = mutableListOf<Token>()
        val prev = state as? XmlTokenState ?: XmlTokenState()
        var i = 0; var inComment = prev.inComment

        if (inComment) {
            val endIdx = line.indexOf("-->")
            if (endIdx >= 0) { tokens.add(Token(0, endIdx + 3, TokenType.COMMENT)); i = endIdx + 3; inComment = false }
            else { tokens.add(Token(0, line.length, TokenType.COMMENT)); return tokens to XmlTokenState(true) }
        }

        while (i < line.length) {
            when {
                line.startsWith("<!--", i) -> {
                    val endIdx = line.indexOf("-->", i + 4)
                    if (endIdx >= 0) { tokens.add(Token(i, endIdx + 3, TokenType.COMMENT)); i = endIdx + 3 }
                    else { tokens.add(Token(i, line.length, TokenType.COMMENT)); return tokens to XmlTokenState(true) }
                }
                line.startsWith("<?", i) -> {
                    val end = line.indexOf("?>", i + 2).let { if (it >= 0) it + 2 else line.length }
                    tokens.add(Token(i, end, TokenType.KEYWORD)); i = end
                }
                line.startsWith("</", i) -> {
                    val end = line.indexOf('>', i + 2).let { if (it >= 0) it + 1 else line.length }
                    tokens.add(Token(i, i + 2, TokenType.PUNCTUATION))
                    val nameEnd = findTagNameEnd(line, i + 2)
                    if (nameEnd > i + 2) tokens.add(Token(i + 2, nameEnd, TokenType.TAG))
                    if (end > nameEnd) tokens.add(Token(end - 1, end, TokenType.PUNCTUATION))
                    i = end
                }
                line[i] == '<' -> {
                    val end = line.indexOf('>', i + 1).let { if (it >= 0) it + 1 else line.length }
                    tokens.add(Token(i, i + 1, TokenType.PUNCTUATION))
                    val nameEnd = findTagNameEnd(line, i + 1)
                    if (nameEnd > i + 1) tokens.add(Token(i + 1, nameEnd, TokenType.TAG))
                    var ai = nameEnd
                    while (ai < end - 1) {
                        if (line[ai].isWhitespace()) { ai++; continue }
                        if (line[ai] == '/' || line[ai] == '>') break
                        val attrStart = ai
                        while (ai < end - 1 && line[ai] != '=' && !line[ai].isWhitespace() && line[ai] != '>' && line[ai] != '/') ai++
                        if (ai > attrStart) tokens.add(Token(attrStart, ai, TokenType.ATTRIBUTE))
                        if (ai < end - 1 && line[ai] == '=') { tokens.add(Token(ai, ai + 1, TokenType.OPERATOR)); ai++ }
                        if (ai < end - 1 && (line[ai] == '"' || line[ai] == '\'')) {
                            val q = line[ai]; val ve = line.indexOf(q, ai + 1).let { if (it >= 0) it + 1 else end - 1 }
                            tokens.add(Token(ai, ve, TokenType.STRING)); ai = ve
                        }
                    }
                    if (end > 1 && end - 2 < line.length && line.getOrNull(end - 2) == '/') tokens.add(Token(end - 2, end - 1, TokenType.PUNCTUATION))
                    if (end <= line.length) tokens.add(Token(end - 1, end, TokenType.PUNCTUATION))
                    i = end
                }
                else -> {
                    val nextTag = line.indexOf('<', i).let { if (it >= 0) it else line.length }
                    if (nextTag > i) tokens.add(Token(i, nextTag, TokenType.PLAIN))
                    i = nextTag
                }
            }
        }
        return tokens to XmlTokenState(inComment)
    }

    private fun findTagNameEnd(line: String, start: Int): Int {
        var i = start
        while (i < line.length && !line[i].isWhitespace() && line[i] != '>' && line[i] != '/') i++
        return i
    }

    override fun foldingRegions(document: EditorDocument): List<FoldRegion> {
        val regions = mutableListOf<FoldRegion>()
        val stack = ArrayDeque<Pair<String, Int>>()
        val text = document.text
        val stripped = stripComments(text)
        val tagPattern = Regex("<(/?)([A-Za-z][A-Za-z0-9_:\\-]*)([^>]*)>")
        var lineNumber = 1; var pos = 0
        for (match in tagPattern.findAll(stripped)) {
            while (pos < match.range.first) { if (stripped[pos] == '\n') lineNumber++; pos++ }
            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2].lowercase()
            val isSelfClose = match.groupValues[3].trimEnd().endsWith("/")
            if (isSelfClose || tagName in VOID_ELEMENTS) continue
            if (!isClosing) stack.addLast(tagName to lineNumber)
            else if (stack.isNotEmpty() && stack.last().first == tagName) {
                val (_, startLine) = stack.removeLast()
                if (lineNumber > startLine) regions.add(FoldRegion(startLine, lineNumber))
            } else if (stack.isNotEmpty()) stack.removeLast()
        }
        return regions.sortedBy { it.startLine }
    }

    override fun validate(text: String): List<InlineEditorError> {
        if (text.isBlank()) return emptyList()
        val trimmedText = text.trim()
        if (!trimmedText.startsWith("<") || !trimmedText.endsWith(">"))
            return listOf(InlineEditorError(1, 1, "XML must begin with '<' and end with '>'"))
        val errors = mutableListOf<InlineEditorError>()
        val stack = ArrayDeque<Pair<String, Int>>()
        val stripped = stripComments(text)
        val tagPattern = Regex("<(/?)([A-Za-z][A-Za-z0-9_:\\-]*)([^>]*)>")
        var lineNumber = 1; var pos = 0
        for (match in tagPattern.findAll(stripped)) {
            while (pos < match.range.first) { if (stripped[pos] == '\n') lineNumber++; pos++ }
            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2].lowercase()
            val isSelfClose = match.groupValues[3].trimEnd().endsWith("/")
            if (isSelfClose || tagName in VOID_ELEMENTS) continue
            if (!isClosing) stack.addLast(tagName to lineNumber)
            else if (stack.isEmpty()) errors += InlineEditorError(lineNumber, 1, "Unexpected closing tag </$tagName>")
            else if (stack.last().first != tagName) {
                val (expected, openLine) = stack.last()
                errors += InlineEditorError(lineNumber, 1, "Mismatched tag: expected </$expected> (opened at line $openLine) but got </$tagName>")
                stack.removeLast()
            } else stack.removeLast()
        }
        for ((tagName, openLine) in stack.asReversed()) {
            errors += InlineEditorError(openLine, 1, "Unclosed tag <$tagName>")
        }
        return errors
    }

    internal fun stripComments(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            if (i + 3 < text.length && text[i] == '<' && text[i+1] == '!' && text[i+2] == '-' && text[i+3] == '-') {
                val end = text.indexOf("-->", i + 4).let { if (it == -1) text.length else it + 3 }
                for (j in i until end) append(if (text[j] == '\n') '\n' else ' ')
                i = end
            } else { append(text[i]); i++ }
        }
    }

    override fun format(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        return try {
            val sb = StringBuilder(trimmed.length * 2)
            var indent = 0
            val maxIndent = 500
            var i = 0
            val len = trimmed.length

            while (i < len) {
                if (trimmed[i].isWhitespace()) { i++; continue }

                if (trimmed[i] != '<') {
                    val start = i
                    while (i < len && trimmed[i] != '<') i++
                    val txt = trimmed.substring(start, i).trim()
                    if (txt.isNotEmpty()) {
                        appendFormatIndent(sb, indent, maxIndent)
                        sb.append(txt).append('\n')
                    }
                    continue
                }

                val tagStart = i

                if (trimmed.startsWith("<![CDATA[", i)) {
                    val end = trimmed.indexOf("]]>", i + 9).let { if (it < 0) len else it + 3 }
                    appendFormatIndent(sb, indent, maxIndent)
                    sb.append(trimmed.substring(tagStart, end)).append('\n')
                    i = end; continue
                }

                if (trimmed.startsWith("<!--", i)) {
                    val end = trimmed.indexOf("-->", i + 4).let { if (it < 0) len else it + 3 }
                    appendFormatIndent(sb, indent, maxIndent)
                    sb.append(trimmed.substring(tagStart, end)).append('\n')
                    i = end; continue
                }

                i++
                while (i < len && trimmed[i] != '>') {
                    if (trimmed[i] == '"') { i++; while (i < len && trimmed[i] != '"') i++ }
                    else if (trimmed[i] == '\'') { i++; while (i < len && trimmed[i] != '\'') i++ }
                    i++
                }
                if (i < len) i++

                val tag = trimmed.substring(tagStart, i)
                val isClosing = tag.startsWith("</")
                val isSelfClosing = tag.endsWith("/>")
                val isProcInstr = tag.startsWith("<?") || tag.startsWith("<!")
                val tagName = tag.removePrefix("</").removePrefix("<")
                    .takeWhile { it.isLetterOrDigit() || it == ':' || it == '-' || it == '_' }
                    .lowercase()
                val isVoid = tagName in VOID_ELEMENTS

                if (isClosing) indent = (indent - 1).coerceAtLeast(0)

                // Inline element: <tag>text</tag> kept on one line
                if (!isClosing && !isSelfClosing && !isProcInstr && !isVoid) {
                    val nextLt = trimmed.indexOf('<', i)
                    if (nextLt >= i) {
                        val closingTag = "</$tagName>"
                        if (trimmed.regionMatches(nextLt, closingTag, 0, closingTag.length, ignoreCase = true)) {
                            val inlineText = trimmed.substring(i, nextLt)
                            appendFormatIndent(sb, indent, maxIndent)
                            sb.append(tag).append(inlineText).append(closingTag).append('\n')
                            i = nextLt + closingTag.length
                            continue
                        }
                    }
                }

                appendFormatIndent(sb, indent, maxIndent)
                sb.append(tag).append('\n')
                if (!isClosing && !isSelfClosing && !isProcInstr && !isVoid) indent++
            }

            sb.toString().trimEnd()
        } catch (_: Throwable) { text }
    }

    private fun appendFormatIndent(sb: StringBuilder, indent: Int, maxIndent: Int) {
        repeat(indent.coerceAtMost(maxIndent)) { sb.append("  ") }
    }
}
