package com.reqlab.ui.shared.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

// ── Syntax token colours ────────────────────────────────────────

object SyntaxColors {
    // JSON
    val jsonKey      = Color(0xFF9CDCFE)   // light-blue  – property names
    val jsonString   = Color(0xFFCE9178)   // warm orange – string values
    val jsonNumber   = Color(0xFFB5CEA8)   // soft green  – numbers
    val jsonBoolean  = Color(0xFF569CD6)   // blue        – true / false
    val jsonNull     = Color(0xFF569CD6)   // blue        – null
    val jsonBrace    = Color(0xFFD4D4D4)   // light gray  – { } [ ] : ,

    // XML / HTML
    val xmlTagName   = Color(0xFF569CD6)   // blue
    val xmlAttrName  = Color(0xFF9CDCFE)   // light-blue
    val xmlAttrValue = Color(0xFFCE9178)   // warm orange
    val xmlBracket   = Color(0xFF808080)   // gray        – < > / =
    val xmlContent   = Color(0xFFD4D4D4)   // light gray  – text content
    val xmlComment   = Color(0xFF6A9955)   // green       – <!-- -->
    val xmlDoctype   = Color(0xFF569CD6)   // blue        – <!DOCTYPE ...>

    // GraphQL
    val gqlKeyword   = Color(0xFFC678DD)   // purple      – query, mutation, subscription, fragment
    val gqlType      = Color(0xFF4EC9B0)   // teal        – type names
    val gqlField     = Color(0xFF9CDCFE)   // light-blue  – field names
    val gqlDirective = Color(0xFFE5C07B)   // gold        – @directives
    val gqlComment   = Color(0xFF6A9955)   // green       – # comments

    // General
    val plain        = Color(0xFFD4D4D4)   // default text
    val searchMatch  = Color(0x44FFEB3B)   // yellow tint – search highlight
    val searchActive = Color(0x88FFEB3B)   // brighter    – active match
}

// ── Content-type detection ──────────────────────────────────────

enum class SyntaxLanguage {
    JSON, XML, HTML, GRAPHQL, PLAIN
}

fun detectLanguage(contentType: String?): SyntaxLanguage = when {
    contentType == null -> SyntaxLanguage.PLAIN
    contentType.contains("json", ignoreCase = true) -> SyntaxLanguage.JSON
    contentType.contains("graphql", ignoreCase = true) -> SyntaxLanguage.GRAPHQL
    contentType.contains("xml", ignoreCase = true)  -> SyntaxLanguage.XML
    contentType.contains("html", ignoreCase = true) -> SyntaxLanguage.HTML
    contentType.contains("svg", ignoreCase = true)  -> SyntaxLanguage.XML
    else -> SyntaxLanguage.PLAIN
}

// ── JSON Highlighter ────────────────────────────────────────────

private val JSON_STRING_REGEX = Regex(""""(?:[^"\\]|\\.)*"""")
private val JSON_NUMBER_REGEX = Regex("""-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b""")
private val JSON_BOOL_REGEX   = Regex("""\b(true|false)\b""")
private val JSON_NULL_REGEX   = Regex("""\bnull\b""")

fun highlightJson(text: String): AnnotatedString = buildAnnotatedString {
    // Tokenize character-by-character for accurate highlighting
    var i = 0
    val len = text.length
    var expectingKey = true  // after { or , in object context

    while (i < len) {
        val ch = text[i]
        when {
            ch == '"' -> {
                // Find end of string
                val end = findStringEnd(text, i)
                val str = text.substring(i, end)
                // Determine if this is a key (followed by ':')
                val afterStr = text.indexOfFirstNonWhitespace(end)
                val isKey = afterStr < len && text[afterStr] == ':'
                withStyle(SpanStyle(color = if (isKey) SyntaxColors.jsonKey else SyntaxColors.jsonString)) {
                    append(str)
                }
                i = end
            }
            ch == '-' || ch.isDigit() -> {
                val match = JSON_NUMBER_REGEX.find(text, i)
                if (match != null && match.range.first == i) {
                    withStyle(SpanStyle(color = SyntaxColors.jsonNumber)) {
                        append(match.value)
                    }
                    i = match.range.last + 1
                } else {
                    withStyle(SpanStyle(color = SyntaxColors.plain)) { append(ch) }
                    i++
                }
            }
            ch == 't' || ch == 'f' -> {
                val match = JSON_BOOL_REGEX.find(text, i)
                if (match != null && match.range.first == i) {
                    withStyle(SpanStyle(color = SyntaxColors.jsonBoolean, fontWeight = FontWeight.SemiBold)) {
                        append(match.value)
                    }
                    i = match.range.last + 1
                } else {
                    withStyle(SpanStyle(color = SyntaxColors.plain)) { append(ch) }
                    i++
                }
            }
            ch == 'n' -> {
                val match = JSON_NULL_REGEX.find(text, i)
                if (match != null && match.range.first == i) {
                    withStyle(SpanStyle(color = SyntaxColors.jsonNull, fontStyle = FontStyle.Italic)) {
                        append(match.value)
                    }
                    i = match.range.last + 1
                } else {
                    withStyle(SpanStyle(color = SyntaxColors.plain)) { append(ch) }
                    i++
                }
            }
            ch in "{}[]:," -> {
                withStyle(SpanStyle(color = SyntaxColors.jsonBrace)) { append(ch) }
                i++
            }
            else -> {
                append(ch)
                i++
            }
        }
    }
}

private fun findStringEnd(text: String, start: Int): Int {
    var i = start + 1
    while (i < text.length) {
        when (text[i]) {
            '\\' -> i += 2  // skip escaped char
            '"'  -> return i + 1
            else -> i++
        }
    }
    return text.length
}

private fun String.indexOfFirstNonWhitespace(from: Int): Int {
    var i = from
    while (i < length && this[i].isWhitespace()) i++
    return i
}

// ── XML / HTML Highlighter ──────────────────────────────────────

fun highlightXml(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val len = text.length

    while (i < len) {
        when {
            // Comment: <!-- ... -->
            text.startsWith("<!--", i) -> {
                val end = text.indexOf("-->", i + 4).let { if (it < 0) len else it + 3 }
                withStyle(SpanStyle(color = SyntaxColors.xmlComment, fontStyle = FontStyle.Italic)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // DOCTYPE: <!DOCTYPE ...>
            text.startsWith("<!", i) && !text.startsWith("<!--", i) -> {
                val end = text.indexOf('>', i).let { if (it < 0) len else it + 1 }
                withStyle(SpanStyle(color = SyntaxColors.xmlDoctype)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // CDATA: <![CDATA[ ... ]]>
            text.startsWith("<![CDATA[", i) -> {
                val end = text.indexOf("]]>", i + 9).let { if (it < 0) len else it + 3 }
                withStyle(SpanStyle(color = SyntaxColors.xmlContent)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // Processing instruction: <?...?>
            text.startsWith("<?", i) -> {
                val end = text.indexOf("?>", i + 2).let { if (it < 0) len else it + 2 }
                withStyle(SpanStyle(color = SyntaxColors.xmlDoctype)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // Tag: <tagname ... > or </tagname>
            text[i] == '<' -> {
                i = highlightXmlTag(text, i, this)
            }
            else -> {
                // Text content
                val nextTag = text.indexOf('<', i).let { if (it < 0) len else it }
                withStyle(SpanStyle(color = SyntaxColors.xmlContent)) {
                    append(text.substring(i, nextTag))
                }
                i = nextTag
            }
        }
    }
}

private fun highlightXmlTag(text: String, start: Int, builder: AnnotatedString.Builder): Int {
    val len = text.length
    var i = start

    // Opening bracket '<' or '</'
    val isClosing = i + 1 < len && text[i + 1] == '/'
    builder.apply {
        withStyle(SpanStyle(color = SyntaxColors.xmlBracket)) {
            append(if (isClosing) "</" else "<")
        }
    }
    i += if (isClosing) 2 else 1

    // Tag name
    val nameStart = i
    while (i < len && text[i] != ' ' && text[i] != '>' && text[i] != '/' && text[i] != '\n' && text[i] != '\t') i++
    if (i > nameStart) {
        builder.apply {
            withStyle(SpanStyle(color = SyntaxColors.xmlTagName, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(nameStart, i))
            }
        }
    }

    // Attributes and closing
    while (i < len && text[i] != '>') {
        when {
            text[i].isWhitespace() -> {
                builder.append(text[i])
                i++
            }
            text[i] == '/' -> {
                builder.apply {
                    withStyle(SpanStyle(color = SyntaxColors.xmlBracket)) { append('/') }
                }
                i++
            }
            text[i] == '=' -> {
                builder.apply {
                    withStyle(SpanStyle(color = SyntaxColors.xmlBracket)) { append('=') }
                }
                i++
            }
            text[i] == '"' || text[i] == '\'' -> {
                val quote = text[i]
                val end = text.indexOf(quote, i + 1).let { if (it < 0) len else it + 1 }
                builder.apply {
                    withStyle(SpanStyle(color = SyntaxColors.xmlAttrValue)) {
                        append(text.substring(i, end))
                    }
                }
                i = end
            }
            else -> {
                // Attribute name
                val attrStart = i
                while (i < len && text[i] != '=' && text[i] != '>' && !text[i].isWhitespace() && text[i] != '/') i++
                builder.apply {
                    withStyle(SpanStyle(color = SyntaxColors.xmlAttrName)) {
                        append(text.substring(attrStart, i))
                    }
                }
            }
        }
    }

    // Closing bracket '>'
    if (i < len && text[i] == '>') {
        builder.apply {
            withStyle(SpanStyle(color = SyntaxColors.xmlBracket)) { append('>') }
        }
        i++
    }

    return i
}

// ── GraphQL Highlighter ─────────────────────────────────────────

private val GQL_KEYWORDS = setOf(
    "query", "mutation", "subscription", "fragment", "on", "type", "input",
    "enum", "interface", "union", "scalar", "extend", "schema", "directive",
    "implements", "repeatable"
)

fun highlightGraphql(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val len = text.length

    while (i < len) {
        val ch = text[i]
        when {
            // Comment
            ch == '#' -> {
                val end = text.indexOf('\n', i).let { if (it < 0) len else it }
                withStyle(SpanStyle(color = SyntaxColors.gqlComment, fontStyle = FontStyle.Italic)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // String
            ch == '"' -> {
                val end = findStringEnd(text, i)
                withStyle(SpanStyle(color = SyntaxColors.jsonString)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // Directive
            ch == '@' -> {
                val end = findWordEnd(text, i + 1)
                withStyle(SpanStyle(color = SyntaxColors.gqlDirective)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // Variable
            ch == '$' -> {
                val end = findWordEnd(text, i + 1)
                withStyle(SpanStyle(color = SyntaxColors.jsonKey)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // Number
            ch.isDigit() || (ch == '-' && i + 1 < len && text[i + 1].isDigit()) -> {
                val match = JSON_NUMBER_REGEX.find(text, i)
                if (match != null && match.range.first == i) {
                    withStyle(SpanStyle(color = SyntaxColors.jsonNumber)) { append(match.value) }
                    i = match.range.last + 1
                } else {
                    append(ch)
                    i++
                }
            }
            // Word (keyword, type, or field)
            ch.isLetter() || ch == '_' -> {
                val end = findWordEnd(text, i)
                val word = text.substring(i, end)
                val color = when {
                    word in GQL_KEYWORDS -> SyntaxColors.gqlKeyword
                    word == "true" || word == "false" || word == "null" -> SyntaxColors.jsonBoolean
                    word[0].isUpperCase() -> SyntaxColors.gqlType
                    else -> SyntaxColors.gqlField
                }
                val style = if (word in GQL_KEYWORDS) SpanStyle(color = color, fontWeight = FontWeight.SemiBold) else SpanStyle(color = color)
                withStyle(style) { append(word) }
                i = end
            }
            // Braces and punctuation
            ch in "{}()[]!:=|&" -> {
                withStyle(SpanStyle(color = SyntaxColors.jsonBrace)) { append(ch) }
                i++
            }
            else -> {
                append(ch)
                i++
            }
        }
    }
}

private fun findWordEnd(text: String, start: Int): Int {
    var i = start
    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
    return i
}

// ── Highlight a single line with the appropriate language ────────

fun highlightLine(line: String, language: SyntaxLanguage): AnnotatedString = when (language) {
    SyntaxLanguage.JSON    -> highlightJson(line)
    SyntaxLanguage.XML     -> highlightXml(line)
    SyntaxLanguage.HTML    -> highlightXml(line)  // reuse XML highlighter for HTML
    SyntaxLanguage.GRAPHQL -> highlightGraphql(line)
    SyntaxLanguage.PLAIN   -> AnnotatedString(line)
}

/** Highlight the entire text at once (used for small responses). */
fun highlightText(text: String, language: SyntaxLanguage): AnnotatedString = when (language) {
    SyntaxLanguage.JSON    -> highlightJson(text)
    SyntaxLanguage.XML     -> highlightXml(text)
    SyntaxLanguage.HTML    -> highlightXml(text)
    SyntaxLanguage.GRAPHQL -> highlightGraphql(text)
    SyntaxLanguage.PLAIN   -> AnnotatedString(text)
}

// ── Search match highlighting ───────────────────────────────────

data class SearchMatch(val lineIndex: Int, val startOffset: Int, val endOffset: Int)

/**
 * Finds all occurrences of [query] in [lines], returns a list of [SearchMatch].
 * [ignoreCase] controls case sensitivity.
 */
fun findSearchMatches(lines: List<String>, query: String, ignoreCase: Boolean = true): List<SearchMatch> {
    if (query.isEmpty()) return emptyList()
    return buildList {
        lines.forEachIndexed { lineIndex, line ->
            var searchFrom = 0
            while (true) {
                val pos = line.indexOf(query, searchFrom, ignoreCase = ignoreCase)
                if (pos < 0) break
                add(SearchMatch(lineIndex, pos, pos + query.length))
                searchFrom = pos + 1
            }
        }
    }
}

/**
 * Overlays search highlight spans on top of an already-highlighted [AnnotatedString].
 * [matches] should be the matches for this specific line.
 * [activeMatchIndex] is the globally active match index, [lineMatches] maps to global indices.
 */
fun applySearchHighlights(
    base: AnnotatedString,
    matches: List<SearchMatch>,
    activeGlobalIndex: Int,
    globalStartIndex: Int,
): AnnotatedString {
    if (matches.isEmpty()) return base
    return buildAnnotatedString {
        append(base)
        matches.forEachIndexed { localIndex, match ->
            val isActive = (globalStartIndex + localIndex) == activeGlobalIndex
            addStyle(
                SpanStyle(
                    background = if (isActive) SyntaxColors.searchActive else SyntaxColors.searchMatch
                ),
                match.startOffset,
                match.endOffset.coerceAtMost(base.length),
            )
        }
    }
}

// ── XML / HTML formatting ───────────────────────────────────────

/**
 * Simple XML/HTML indentation formatter.
 * Splits by `><` boundaries and applies indentation.
 */
fun formatXml(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed

    // Insert newlines between >< boundaries
    val withBreaks = trimmed.replace(Regex(">(\\s*)<")) { ">\n<" }
    val lines = withBreaks.split('\n')
    val sb = StringBuilder()
    var indent = 0
    val indentStr = "  "

    for (line in lines) {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) continue

        // Decrease indent for closing tags
        if (trimmedLine.startsWith("</")) {
            indent = (indent - 1).coerceAtLeast(0)
        }

        sb.append(indentStr.repeat(indent))
        sb.appendLine(trimmedLine)

        // Increase indent for opening tags (not self-closing, not closing)
        if (trimmedLine.startsWith("<") &&
            !trimmedLine.startsWith("</") &&
            !trimmedLine.startsWith("<!") &&
            !trimmedLine.startsWith("<?") &&
            !trimmedLine.endsWith("/>") &&
            trimmedLine.endsWith(">")
        ) {
            indent++
        }
    }

    return sb.toString().trimEnd()
}

// ── File extension from content type ────────────────────────────

fun fileExtensionForContentType(contentType: String?): String = when {
    contentType == null -> "txt"
    contentType.contains("json", ignoreCase = true) -> "json"
    contentType.contains("xml", ignoreCase = true) -> "xml"
    contentType.contains("html", ignoreCase = true) -> "html"
    contentType.contains("javascript", ignoreCase = true) -> "js"
    contentType.contains("css", ignoreCase = true) -> "css"
    contentType.contains("csv", ignoreCase = true) -> "csv"
    contentType.contains("yaml", ignoreCase = true) || contentType.contains("yml", ignoreCase = true) -> "yaml"
    contentType.contains("svg", ignoreCase = true) -> "svg"
    contentType.contains("pdf", ignoreCase = true) -> "pdf"
    contentType.contains("png", ignoreCase = true) -> "png"
    contentType.contains("jpeg", ignoreCase = true) || contentType.contains("jpg", ignoreCase = true) -> "jpg"
    contentType.contains("gif", ignoreCase = true) -> "gif"
    contentType.contains("text/plain", ignoreCase = true) -> "txt"
    else -> "txt"
}
