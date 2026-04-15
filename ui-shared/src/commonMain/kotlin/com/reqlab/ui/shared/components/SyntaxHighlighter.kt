package com.reqlab.ui.shared.components

import androidx.compose.ui.text.AnnotatedString
import com.reqlab.editor.core.ContentTypeUtil
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.LanguageRegistry
import com.reqlab.editor.core.XmlMode
import com.reqlab.editor.ui.SyntaxColors as EditorSyntaxColors
import com.reqlab.editor.ui.highlightText as editorHighlightText
import com.reqlab.editor.ui.highlightLine as editorHighlightLine
import com.reqlab.editor.ui.autoFormat as editorAutoFormat
import com.reqlab.editor.ui.SearchMatch as EditorSearchMatch
import com.reqlab.editor.ui.findSearchMatches as editorFindSearchMatches
import com.reqlab.editor.ui.applySearchHighlights as editorApplySearchHighlights

// ── Syntax token colours (delegates to editor-ui) ───────────────

/**
 * Re-exports editor-ui's [EditorSyntaxColors] for backward compatibility.
 * New code should import [com.reqlab.editor.ui.SyntaxColors] directly.
 */
object SyntaxColors {
    // JSON
    val jsonKey      get() = EditorSyntaxColors.jsonKey
    val jsonString   get() = EditorSyntaxColors.jsonString
    val jsonNumber   get() = EditorSyntaxColors.jsonNumber
    val jsonBoolean  get() = EditorSyntaxColors.jsonBoolean
    val jsonNull     get() = EditorSyntaxColors.jsonBoolean
    val jsonBrace    get() = EditorSyntaxColors.jsonBrace

    // XML / HTML
    val xmlTagName   get() = EditorSyntaxColors.xmlTagName
    val xmlAttrName  get() = EditorSyntaxColors.xmlAttrName
    val xmlAttrValue get() = EditorSyntaxColors.xmlAttrValue
    val xmlBracket   get() = EditorSyntaxColors.xmlBracket
    val xmlContent   get() = EditorSyntaxColors.xmlContent
    val xmlComment   get() = EditorSyntaxColors.xmlComment
    val xmlDoctype   get() = EditorSyntaxColors.xmlDoctype

    // GraphQL
    val gqlKeyword   get() = EditorSyntaxColors.gqlKeyword
    val gqlType      get() = EditorSyntaxColors.gqlType
    val gqlField     get() = EditorSyntaxColors.gqlField
    val gqlDirective get() = EditorSyntaxColors.gqlDirective
    val gqlComment   get() = EditorSyntaxColors.jsComment

    // JavaScript
    val jsKeyword   get() = EditorSyntaxColors.jsKeyword
    val jsBuiltin   get() = EditorSyntaxColors.jsBuiltin
    val jsString    get() = EditorSyntaxColors.jsString
    val jsComment   get() = EditorSyntaxColors.jsComment
    val jsOperator  get() = EditorSyntaxColors.jsOperator

    // General
    val plain        get() = EditorSyntaxColors.plain
    val searchMatch  get() = EditorSyntaxColors.searchMatch
    val searchActive get() = EditorSyntaxColors.searchActive
}

// ── Content-type detection ──────────────────────────────────────

/**
 * Syntax language enum for backward compatibility.
 * New code should use [LanguageMode] from editor-core directly.
 */
enum class SyntaxLanguage {
    JSON, XML, HTML, GRAPHQL, JAVASCRIPT, PLAIN;

    fun toLanguageMode(): LanguageMode = when (this) {
        JSON       -> LanguageMode.JSON
        XML        -> LanguageMode.XML
        HTML       -> LanguageMode.HTML
        GRAPHQL    -> LanguageMode.GRAPHQL
        JAVASCRIPT -> LanguageMode.JAVASCRIPT
        PLAIN      -> LanguageMode.PLAIN_TEXT
    }

    companion object {
        fun fromLanguageMode(mode: LanguageMode): SyntaxLanguage = when (mode) {
            LanguageMode.JSON       -> JSON
            LanguageMode.XML        -> XML
            LanguageMode.HTML       -> HTML
            LanguageMode.GRAPHQL    -> GRAPHQL
            LanguageMode.JAVASCRIPT -> JAVASCRIPT
            LanguageMode.PLAIN_TEXT -> PLAIN
        }
    }
}

fun detectLanguage(contentType: String?): SyntaxLanguage =
    SyntaxLanguage.fromLanguageMode(LanguageMode.fromContentType(contentType))

// ── Syntax highlighting (delegates to editor-ui) ────────────────

fun highlightLine(line: String, language: SyntaxLanguage): AnnotatedString =
    editorHighlightLine(line, language.toLanguageMode())

fun highlightText(text: String, language: SyntaxLanguage): AnnotatedString =
    editorHighlightText(text, language.toLanguageMode())

fun highlightJson(text: String): AnnotatedString =
    editorHighlightText(text, LanguageMode.JSON)

fun highlightXml(text: String): AnnotatedString =
    editorHighlightText(text, LanguageMode.XML)

fun highlightGraphql(text: String): AnnotatedString =
    editorHighlightText(text, LanguageMode.GRAPHQL)

fun highlightJavaScript(text: String): AnnotatedString =
    editorHighlightText(text, LanguageMode.JAVASCRIPT)

// ── Search match highlighting (delegates to editor-ui) ──────────

data class SearchMatch(val lineIndex: Int, val startOffset: Int, val endOffset: Int)

fun findSearchMatches(lines: List<String>, query: String, ignoreCase: Boolean = true): List<SearchMatch> =
    editorFindSearchMatches(lines, query, ignoreCase).map {
        SearchMatch(it.lineIndex, it.startOffset, it.endOffset)
    }

fun applySearchHighlights(
    base: AnnotatedString,
    matches: List<SearchMatch>,
    activeGlobalIndex: Int,
    globalStartIndex: Int,
): AnnotatedString =
    editorApplySearchHighlights(
        base,
        matches.map { EditorSearchMatch(it.lineIndex, it.startOffset, it.endOffset) },
        activeGlobalIndex,
        globalStartIndex,
    )

// ── Formatting (delegates to editor-core) ───────────────────────

fun formatXml(raw: String): String = XmlMode.format(raw)

fun tryPrettyPrint(raw: String): String {
    if (!LanguageRegistry.hasProvider(LanguageMode.JSON)) LanguageRegistry.registerBuiltins()
    return LanguageRegistry.getProvider(LanguageMode.JSON).format(raw)
}

fun autoFormat(raw: String, language: SyntaxLanguage): String =
    editorAutoFormat(raw, language.toLanguageMode())

// ── Validation ──────────────────────────────────────────────────

data class JsonValidationError(
    val message: String,
    val line: Int = -1,
    val col: Int = -1,
)

fun validateJson(text: String): JsonValidationError? {
    if (text.isBlank()) return null
    if (!LanguageRegistry.hasProvider(LanguageMode.JSON)) LanguageRegistry.registerBuiltins()
    val errors = LanguageRegistry.getProvider(LanguageMode.JSON).validate(text)
    if (errors.isEmpty()) return null
    val first = errors.first()
    return JsonValidationError(
        message = first.message.substringBefore("\n").take(200),
        line = first.line,
        col = first.col,
    )
}

data class XmlValidationError(val message: String)

fun validateXml(text: String): XmlValidationError? {
    if (text.isBlank()) return null
    if (!LanguageRegistry.hasProvider(LanguageMode.XML)) LanguageRegistry.registerBuiltins()
    val errors = LanguageRegistry.getProvider(LanguageMode.XML).validate(text)
    if (errors.isEmpty()) return null
    return XmlValidationError(errors.joinToString("; ") { it.message })
}

// ── File extension from content type (delegates to editor-core) ─

fun fileExtensionForContentType(contentType: String?): String =
    ContentTypeUtil.fileExtensionForContentType(contentType)

// ── XML void elements (re-exported from editor-core) ────────────

val VOID_ELEMENTS: Set<String> get() = XmlMode.VOID_ELEMENTS
