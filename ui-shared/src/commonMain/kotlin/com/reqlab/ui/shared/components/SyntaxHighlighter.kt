package com.reqlab.ui.shared.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import com.reqlab.editor.core.EditorDocument
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.LanguageRegistry
import com.reqlab.editor.core.TokenType

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

    // JavaScript / general scripting
    val jsKeyword   = Color(0xFFC678DD)   // purple      – keywords (var, let, const, …)
    val jsBuiltin   = Color(0xFF4EC9B0)   // teal        – built-in objects (console, Date, …)
    val jsString    = Color(0xFFCE9178)   // warm orange – string literals
    val jsComment   = Color(0xFF6A9955)   // green       – // and /* */ comments
    val jsOperator  = Color(0xFFD4D4D4)   // light gray  – operators and punctuation

    // General
    val plain        = Color(0xFFD4D4D4)   // default text
    val searchMatch  = Color(0x44FFEB3B)   // yellow tint – search highlight
    val searchActive = Color(0x88FFEB3B)   // brighter    – active match
}

// ── Content-type detection ──────────────────────────────────────

enum class SyntaxLanguage {
    JSON, XML, HTML, GRAPHQL, JAVASCRIPT, PLAIN
}

fun detectLanguage(contentType: String?): SyntaxLanguage = when {
    contentType == null -> SyntaxLanguage.PLAIN
    contentType.contains("json", ignoreCase = true) -> SyntaxLanguage.JSON
    contentType.contains("graphql", ignoreCase = true) -> SyntaxLanguage.GRAPHQL
    contentType.contains("xml", ignoreCase = true)  -> SyntaxLanguage.XML
    contentType.contains("html", ignoreCase = true) -> SyntaxLanguage.HTML
    contentType.contains("svg", ignoreCase = true)  -> SyntaxLanguage.XML
    contentType.contains("javascript", ignoreCase = true) -> SyntaxLanguage.JAVASCRIPT
    contentType.contains("ecmascript", ignoreCase = true) -> SyntaxLanguage.JAVASCRIPT
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

// ── JavaScript / scripting Highlighter ──────────────────────────

private val JS_KEYWORDS = setOf(
    "var", "let", "const", "function", "return", "if", "else", "for",
    "while", "do", "switch", "case", "break", "continue", "try",
    "catch", "finally", "throw", "new", "delete", "typeof",
    "instanceof", "in", "of", "class", "extends", "super", "import",
    "export", "default", "from", "as", "async", "await", "yield",
    "this", "void", "with",
)

private val JS_BUILTINS = setOf(
    "console", "Date", "Math", "JSON", "Object", "Array", "String",
    "Number", "Boolean", "RegExp", "Error", "Promise", "Map", "Set",
    "setTimeout", "setInterval", "clearTimeout", "clearInterval",
    "parseInt", "parseFloat", "isNaN", "isFinite", "require",
)

fun highlightJavaScript(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val len = text.length

    while (i < len) {
        val ch = text[i]
        when {
            // Single-line comment  //
            ch == '/' && i + 1 < len && text[i + 1] == '/' -> {
                val end = text.indexOf('\n', i).let { if (it < 0) len else it }
                withStyle(SpanStyle(color = SyntaxColors.jsComment, fontStyle = FontStyle.Italic)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // Multi-line comment  /* ... */
            ch == '/' && i + 1 < len && text[i + 1] == '*' -> {
                val end = text.indexOf("*/", i + 2).let { if (it < 0) len else it + 2 }
                withStyle(SpanStyle(color = SyntaxColors.jsComment, fontStyle = FontStyle.Italic)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // Double-quoted string
            ch == '"' -> {
                val end = findStringEnd(text, i)
                withStyle(SpanStyle(color = SyntaxColors.jsString)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // Single-quoted string
            ch == '\'' -> {
                val end = findCharStringEnd(text, i, '\'')
                withStyle(SpanStyle(color = SyntaxColors.jsString)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // Template literal
            ch == '`' -> {
                val end = findCharStringEnd(text, i, '`')
                withStyle(SpanStyle(color = SyntaxColors.jsString)) {
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
            // Word: keyword, builtin, boolean, null, or identifier
            ch.isLetter() || ch == '_' || ch == '$' -> {
                val end = findJsWordEnd(text, i)
                val word = text.substring(i, end)
                when {
                    word in JS_KEYWORDS ->
                        withStyle(SpanStyle(color = SyntaxColors.jsKeyword, fontWeight = FontWeight.SemiBold)) { append(word) }
                    word in JS_BUILTINS ->
                        withStyle(SpanStyle(color = SyntaxColors.jsBuiltin)) { append(word) }
                    word == "true" || word == "false" ->
                        withStyle(SpanStyle(color = SyntaxColors.jsonBoolean, fontWeight = FontWeight.SemiBold)) { append(word) }
                    word == "null" || word == "undefined" ->
                        withStyle(SpanStyle(color = SyntaxColors.jsonNull, fontStyle = FontStyle.Italic)) { append(word) }
                    else ->
                        withStyle(SpanStyle(color = SyntaxColors.plain)) { append(word) }
                }
                i = end
            }
            // Braces and operators
            ch in "{}()[];:,.=><+-*/%!&|?~^" -> {
                withStyle(SpanStyle(color = SyntaxColors.jsOperator)) { append(ch) }
                i++
            }
            else -> {
                append(ch)
                i++
            }
        }
    }
}

private fun findCharStringEnd(text: String, start: Int, quote: Char): Int {
    var i = start + 1
    while (i < text.length) {
        when (text[i]) {
            '\\' -> i += 2
            quote -> return i + 1
            else -> i++
        }
    }
    return text.length
}

private fun findJsWordEnd(text: String, start: Int): Int {
    var i = start
    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '$')) i++
    return i
}

// ── Highlight a single line with the appropriate language ────────

/**
 * Maps a [SyntaxLanguage] to an editor-core [LanguageMode], returning null
 * for languages not supported by editor-core (e.g. GraphQL).
 */
private fun syntaxLanguageToMode(language: SyntaxLanguage): LanguageMode? = when (language) {
    SyntaxLanguage.JSON       -> LanguageMode.JSON
    SyntaxLanguage.XML        -> LanguageMode.XML
    SyntaxLanguage.HTML       -> LanguageMode.HTML
    SyntaxLanguage.JAVASCRIPT -> LanguageMode.JAVASCRIPT
    SyntaxLanguage.PLAIN      -> LanguageMode.PLAIN_TEXT
    SyntaxLanguage.GRAPHQL    -> null
}

/** Maps an editor-core [TokenType] to the appropriate [Color] for the given UI language. */
private fun tokenColor(type: TokenType, language: SyntaxLanguage): Color = when (type) {
    TokenType.KEYWORD    -> when (language) {
        SyntaxLanguage.JAVASCRIPT -> SyntaxColors.jsKeyword
        else                      -> SyntaxColors.jsonBoolean
    }
    TokenType.STRING     -> when (language) {
        SyntaxLanguage.JSON                        -> SyntaxColors.jsonString
        SyntaxLanguage.JAVASCRIPT                  -> SyntaxColors.jsString
        SyntaxLanguage.XML, SyntaxLanguage.HTML    -> SyntaxColors.xmlAttrValue
        else                                       -> SyntaxColors.jsonString
    }
    TokenType.NUMBER     -> SyntaxColors.jsonNumber
    TokenType.COMMENT    -> when (language) {
        SyntaxLanguage.XML, SyntaxLanguage.HTML -> SyntaxColors.xmlComment
        else                                    -> SyntaxColors.jsComment
    }
    TokenType.OPERATOR   -> SyntaxColors.jsOperator
    TokenType.PUNCTUATION -> SyntaxColors.jsonBrace
    TokenType.TAG        -> SyntaxColors.xmlTagName
    TokenType.ATTRIBUTE  -> SyntaxColors.xmlAttrName
    TokenType.PROPERTY   -> SyntaxColors.jsonKey
    TokenType.VALUE      -> SyntaxColors.xmlAttrValue
    TokenType.PLAIN      -> SyntaxColors.plain
    TokenType.ERROR      -> Color(0xFFFF6B6B)
}

/**
 * Highlights text using editor-core's language tokenizers.
 * Produces an [AnnotatedString] with the same colour scheme as the legacy
 * per-language highlighters, but using the new unified tokenizer.
 */
private fun highlightWithEditorCore(text: String, language: SyntaxLanguage): AnnotatedString {
    val mode = syntaxLanguageToMode(language) ?: return AnnotatedString(text)
    if (mode == LanguageMode.PLAIN_TEXT) return AnnotatedString(text)
    if (!LanguageRegistry.hasProvider(mode)) LanguageRegistry.registerBuiltins()
    val provider = LanguageRegistry.getProvider(mode)
    val doc = EditorDocument.create(text)
    return buildAnnotatedString {
        append(text)
        var tokenState: Any? = null
        var charOffset = 0
        for (lineNum in 1..doc.lineCount) {
            val lineText = doc.lineText(lineNum)
            val (tokens, newState) = provider.tokenizeLine(lineText, lineNum, tokenState)
            tokenState = newState
            for (token in tokens) {
                val start = charOffset + token.startOffset
                val end = charOffset + token.endOffset
                if (start < end && end <= text.length) {
                    val color = tokenColor(token.type, language)
                    addStyle(SpanStyle(color = color), start, end.coerceAtMost(text.length))
                }
            }
            charOffset += lineText.length + if (lineNum < doc.lineCount) 1 else 0
        }
    }
}

fun highlightLine(line: String, language: SyntaxLanguage): AnnotatedString = when (language) {
    SyntaxLanguage.GRAPHQL    -> highlightGraphql(line)
    SyntaxLanguage.PLAIN      -> AnnotatedString(line)
    else                      -> highlightWithEditorCore(line, language)
}

/** Highlight the entire text at once (used for small responses). */
fun highlightText(text: String, language: SyntaxLanguage): AnnotatedString = when (language) {
    SyntaxLanguage.GRAPHQL    -> highlightGraphql(text)
    SyntaxLanguage.PLAIN      -> AnnotatedString(text)
    else                      -> highlightWithEditorCore(text, language)
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

// ── Auto-formatting ─────────────────────────────────────────────

@kotlinx.serialization.ExperimentalSerializationApi
private val prettyJsonFormatter = Json { prettyPrint = true; prettyPrintIndent = "  " }

/**
 * Try to pretty-print JSON.  Falls back to the raw text on any error.
 */
@kotlinx.serialization.ExperimentalSerializationApi
fun tryPrettyPrint(raw: String): String = try {
    val element = prettyJsonFormatter.decodeFromString(JsonElement.serializer(), raw)
    prettyJsonFormatter.encodeToString(JsonElement.serializer(), element)
} catch (_: Exception) {
    raw
}

/**
 * Auto-format [raw] text based on [language].
 */
@kotlinx.serialization.ExperimentalSerializationApi
fun autoFormat(raw: String, language: SyntaxLanguage): String = when (language) {
    SyntaxLanguage.JSON       -> tryPrettyPrint(raw)
    SyntaxLanguage.XML        -> formatXml(raw)
    SyntaxLanguage.HTML       -> formatXml(raw)
    SyntaxLanguage.GRAPHQL    -> raw
    SyntaxLanguage.JAVASCRIPT -> raw
    SyntaxLanguage.PLAIN      -> raw
}

// ── JSON validation ─────────────────────────────────────────────

/**
 * Result of a JSON validation pass.
 * [line] and [col] are 1-based and best-effort (extracted from the exception message).
 */
data class JsonValidationError(
    val message: String,
    val line: Int = -1,
    val col: Int = -1,
)

/**
 * Validates [text] as JSON.  Returns [JsonValidationError] on any parse error,
 * or null if the text is empty or valid JSON.
 */
fun validateJson(text: String): JsonValidationError? {
    if (text.isBlank()) return null
    return try {
        Json.Default.parseToJsonElement(text)
        null   // valid
    } catch (e: Exception) {
        // Try to extract line/col from the exception message, e.g. "… at line 3 column 7 …"
        val msg = e.message ?: "Invalid JSON"
        val lineMatch = Regex("""line (\d+)""", RegexOption.IGNORE_CASE).find(msg)
        val colMatch  = Regex("""column (\d+)""", RegexOption.IGNORE_CASE).find(msg)
        JsonValidationError(
            message = msg.substringBefore("\n").take(200),
            line = lineMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1,
            col  = colMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1,
        )
    }
}

// ── XML validation (lightweight) ────────────────────────────────

data class XmlValidationError(val message: String)

/**
 * Very lightweight XML well-formedness check based on tag balancing.
 * Returns null if [text] is blank or appears well-formed, or an [XmlValidationError].
 */
fun validateXml(text: String): XmlValidationError? {
    if (text.isBlank()) return null
    return try {
        val trimmed = text.trim()
        // Quick heuristic: must start with '<' and end with '>'
        if (!trimmed.startsWith("<") || !trimmed.endsWith(">"))
            return XmlValidationError("XML must begin with '<' and end with '>'")
        // Count unclosed tags (simplified check)
        val openTags = Regex("<(\\w[\\w:.-]*)(?:\\s[^>]*)?>").findAll(trimmed)
            .map { it.groupValues[1].lowercase() }
            .filter { it !in setOf("area","base","br","col","embed","hr","img","input","link","meta","param","source","track","wbr") }
            .toList()
        val closeTags = Regex("</(\\w[\\w:.-]*)>").findAll(trimmed)
            .map { it.groupValues[1].lowercase() }
            .toList()
        val selfClosing = Regex("<\\w[\\w:.-]*[^>]*/\\s*>").findAll(trimmed).count()
        val expectedClose = openTags.size - selfClosing
        if (expectedClose != closeTags.size) {
            XmlValidationError("XML appears to have mismatched tags (${openTags.size - selfClosing} open, ${closeTags.size} close)")
        } else null
    } catch (_: Exception) {
        null  // Don't block on validation errors in the validator itself
    }
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
