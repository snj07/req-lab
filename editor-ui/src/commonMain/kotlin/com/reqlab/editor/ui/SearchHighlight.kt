package com.reqlab.editor.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Represents a single search match within a line.
 */
data class SearchMatch(
    val lineIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * Finds all occurrences of [query] in [lines].
 * Returns a list of [SearchMatch] with line indices and character offsets.
 * [ignoreCase] controls case sensitivity.
 */
fun findSearchMatches(
    lines: List<String>,
    query: String,
    ignoreCase: Boolean = true,
): List<SearchMatch> {
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
 * Overlays search-highlight spans on top of an already-highlighted [base].
 * [matches] should be the matches for this specific line.
 * [activeGlobalIndex] is the globally-active match index;
 * [globalStartIndex] is the global offset of the first match in this batch.
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
                    background = if (isActive) SyntaxColors.searchActive else SyntaxColors.searchMatch,
                ),
                match.startOffset,
                match.endOffset.coerceAtMost(base.length),
            )
        }
    }
}

/** Replace every occurrence of [query] in [text]. Empty [query] is a no-op. */
fun replaceAllOccurrences(
    text: String,
    query: String,
    replacement: String,
    ignoreCase: Boolean = true,
): String {
    if (query.isEmpty()) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val pos = text.indexOf(query, i, ignoreCase)
        if (pos < 0) {
            out.append(text, i, text.length)
            break
        }
        out.append(text, i, pos)
        out.append(replacement)
        i = pos + query.length
    }
    return out.toString()
}
