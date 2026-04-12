package com.reqlab.ui.shared.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.reqlab.editor.core.EditorDocument
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.LanguageRegistry
import com.reqlab.editor.core.FoldRegion as CoreFoldRegion

// ── Data structures ─────────────────────────────────────────────

/**
 * Represents a foldable region in source code (e.g. a JSON object,
 * an XML element, or a JavaScript block).
 */
data class FoldRegion(
    val startLine: Int,   // 0-indexed line where the fold starts
    val endLine: Int,     // 0-indexed line where the fold ends (inclusive)
    val depth: Int = 0,   // nesting depth (0 = top-level)
)

/**
 * A line that is visible in the editor after applying fold operations.
 * When a fold region is collapsed, only its start line appears and
 * [isFolded] is true.
 */
data class VisibleLine(
    val originalIndex: Int,        // index in the original lines list
    val text: String,              // the text to display
    val isFoldStart: Boolean = false,
    val isFolded: Boolean = false,
    val foldedLineCount: Int = 0,  // number of hidden lines when folded
)

// ── Fold state ──────────────────────────────────────────────────

/**
 * Observable fold state.  Uses Compose [mutableStateListOf] so that
 * toggling a fold automatically triggers recomposition.
 */
class FoldState {
    internal val foldedStartLines = mutableStateListOf<Int>()

    fun isFolded(startLine: Int): Boolean = startLine in foldedStartLines

    fun toggle(startLine: Int) {
        if (isFolded(startLine)) unfold(startLine) else fold(startLine)
    }

    fun fold(startLine: Int) {
        if (startLine !in foldedStartLines) foldedStartLines.add(startLine)
    }

    fun unfold(startLine: Int) {
        foldedStartLines.remove(startLine)
    }

    fun foldAll(regions: List<FoldRegion>) {
        foldedStartLines.clear()
        foldedStartLines.addAll(regions.map { it.startLine })
    }

    fun unfoldAll() {
        foldedStartLines.clear()
    }
}

@Composable
fun rememberFoldState(): FoldState = remember { FoldState() }

// ── Region detection: JSON / JS / GraphQL (brace-based) ────────

/**
 * Detect foldable regions by matching `{ }` and `[ ]` pairs that
 * span more than one line.  Handles strings so that braces inside
 * string literals are ignored.
 */
fun detectBraceFoldRegions(lines: List<String>): List<FoldRegion> {
    data class BraceInfo(val line: Int, val char: Char, val depth: Int)

    val regions = mutableListOf<FoldRegion>()
    val stack = mutableListOf<BraceInfo>()
    var depth = 0
    var inString = false
    var escaped = false

    lines.forEachIndexed { lineIndex, line ->
        for (ch in line) {
            if (escaped) { escaped = false; continue }
            if (ch == '\\' && inString) { escaped = true; continue }
            if (ch == '"') { inString = !inString; continue }
            if (inString) continue

            when (ch) {
                '{', '[' -> {
                    stack.add(BraceInfo(lineIndex, ch, depth))
                    depth++
                }
                '}', ']' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    if (stack.isNotEmpty()) {
                        val matching = stack.removeAt(stack.lastIndex)
                        val expectedClose = if (matching.char == '{') '}' else ']'
                        if (ch == expectedClose && lineIndex > matching.line) {
                            regions.add(
                                FoldRegion(
                                    startLine = matching.line,
                                    endLine = lineIndex,
                                    depth = matching.depth,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    return regions.sortedBy { it.startLine }
}

// ── Region detection: XML / HTML (tag-based) ────────────────────

private val TAG_OPEN_REGEX = Regex("""<(\w[\w:.-]*)(?:\s[^>]*)?>""")
private val TAG_CLOSE_REGEX = Regex("""</(\w[\w:.-]*)>""")
private val SELF_CLOSING_REGEX = Regex("""/\s*>$""")
private val VOID_ELEMENTS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr",
)

/**
 * Detect foldable regions by matching XML/HTML opening and closing
 * tags that span more than one line.
 */
fun detectXmlFoldRegions(lines: List<String>): List<FoldRegion> {
    data class TagInfo(val name: String, val line: Int, val depth: Int)

    val regions = mutableListOf<FoldRegion>()
    val stack = mutableListOf<TagInfo>()

    lines.forEachIndexed { lineIndex, line ->
        // Check for closing tags first so they match in reverse nesting order.
        for (m in TAG_CLOSE_REGEX.findAll(line)) {
            val tagName = m.groupValues[1].lowercase()
            val openIndex = stack.indexOfLast { it.name == tagName }
            if (openIndex >= 0) {
                val open = stack.removeAt(openIndex)
                // Also remove any unmatched tags pushed after the matching open.
                while (stack.size > openIndex) stack.removeAt(stack.lastIndex)
                if (lineIndex > open.line) {
                    regions.add(FoldRegion(open.line, lineIndex, open.depth))
                }
            }
        }

        // Then process opening tags.
        for (m in TAG_OPEN_REGEX.findAll(line)) {
            val tagName = m.groupValues[1].lowercase()
            if (tagName in VOID_ELEMENTS) continue
            val fullMatch = m.value
            if (SELF_CLOSING_REGEX.containsMatchIn(fullMatch)) continue
            stack.add(TagInfo(tagName, lineIndex, stack.size))
        }
    }

    return regions.sortedBy { it.startLine }
}

// ── Multi-line comment fold regions ─────────────────────────────

/**
 * Detect multi-line block comments and HTML/XML comment blocks.
 */
fun detectCommentFoldRegions(lines: List<String>): List<FoldRegion> {
    val regions = mutableListOf<FoldRegion>()
    var commentStart = -1

    lines.forEachIndexed { idx, line ->
        val trimmed = line.trim()
        // C-style block comments
        if (commentStart < 0 && trimmed.contains("/*")) {
            commentStart = idx
        }
        if (commentStart >= 0 && trimmed.contains("*/")) {
            if (idx > commentStart) regions.add(FoldRegion(commentStart, idx))
            commentStart = -1
        }
        // HTML/XML comments
        if (commentStart < 0 && trimmed.contains("<!--")) {
            commentStart = idx
        }
        if (commentStart >= 0 && trimmed.contains("-->")) {
            if (idx > commentStart) regions.add(FoldRegion(commentStart, idx))
            commentStart = -1
        }
    }

    return regions
}

// ── Language-aware dispatcher ───────────────────────────────────

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

/**
 * Detect all fold regions in [lines] for the given [language].
 *
 * For JSON, XML, HTML, and JavaScript, fold detection is delegated to the
 * editor-core [LanguageRegistry] so that the new editor engine determines
 * fold regions. For GraphQL (not in editor-core) and PLAIN, the legacy
 * detection is used.
 */
fun detectFoldRegions(lines: List<String>, language: SyntaxLanguage): List<FoldRegion> {
    if (lines.size < 3) return emptyList()

    fun mergeRegions(primary: List<FoldRegion>, fallback: List<FoldRegion>): List<FoldRegion> {
        val merged = (primary + fallback)
            .filter { it.endLine > it.startLine }
            .distinctBy { it.startLine to it.endLine }
            .sortedBy { it.startLine }
        return merged
    }

    fun coreRegionsFor(mode: LanguageMode): List<FoldRegion> {
        if (!LanguageRegistry.hasProvider(mode)) LanguageRegistry.registerBuiltins()
        val doc = EditorDocument.create(lines.joinToString("\n"))
        val provider = LanguageRegistry.getProvider(mode)
        val coreRegions: List<CoreFoldRegion> = provider.foldingRegions(doc)
        return coreRegions
            .map { FoldRegion(startLine = it.startLine - 1, endLine = it.endLine - 1) }
            .filter { it.endLine > it.startLine }
    }

    return when (language) {
        SyntaxLanguage.JSON -> {
            // Keep JSON folds stable while user is typing incomplete documents.
            // The tolerant brace matcher prevents arrows from disappearing between keystrokes.
            val core = runCatching { coreRegionsFor(LanguageMode.JSON) }.getOrDefault(emptyList())
            val legacy = detectBraceFoldRegions(lines)
            mergeRegions(core, legacy)
        }
        SyntaxLanguage.JAVASCRIPT -> {
            val core = runCatching { coreRegionsFor(LanguageMode.JAVASCRIPT) }.getOrDefault(emptyList())
            val legacy = detectBraceFoldRegions(lines) + detectCommentFoldRegions(lines)
            mergeRegions(core, legacy)
        }
        SyntaxLanguage.XML -> {
            val core = runCatching { coreRegionsFor(LanguageMode.XML) }.getOrDefault(emptyList())
            val legacy = detectXmlFoldRegions(lines) + detectCommentFoldRegions(lines)
            mergeRegions(core, legacy)
        }
        SyntaxLanguage.HTML -> {
            val core = runCatching { coreRegionsFor(LanguageMode.HTML) }.getOrDefault(emptyList())
            val legacy = detectXmlFoldRegions(lines) + detectCommentFoldRegions(lines)
            mergeRegions(core, legacy)
        }
        SyntaxLanguage.GRAPHQL -> {
            (detectBraceFoldRegions(lines) + detectCommentFoldRegions(lines)).sortedBy { it.startLine }
        }
        SyntaxLanguage.PLAIN -> emptyList()
    }
}

// ── Visible-line computation ────────────────────────────────────

/**
 * Returns the subset of [lines] that are visible given the current
 * [foldState].  Folded regions are collapsed to their start line
 * with [VisibleLine.isFolded] = true.
 */
fun computeVisibleLines(
    lines: List<String>,
    foldRegions: List<FoldRegion>,
    foldState: FoldState,
): List<VisibleLine> {
    val foldStarts = foldRegions.associateBy { it.startLine }
    val result = mutableListOf<VisibleLine>()
    var i = 0

    while (i < lines.size) {
        val region = foldStarts[i]
        val isFoldStart = region != null
        val isFolded = isFoldStart && foldState.isFolded(i)

        if (isFolded && region != null) {
            result.add(
                VisibleLine(
                    originalIndex = i,
                    text = lines[i],
                    isFoldStart = true,
                    isFolded = true,
                    foldedLineCount = region.endLine - region.startLine,
                ),
            )
            i = region.endLine + 1
        } else {
            result.add(
                VisibleLine(
                    originalIndex = i,
                    text = lines[i],
                    isFoldStart = isFoldStart,
                    isFolded = false,
                ),
            )
            i++
        }
    }

    return result
}
