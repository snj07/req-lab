package com.reqlab.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.reqlab.editor.core.EditorDocument
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.LanguageRegistry
import com.reqlab.editor.core.FoldingStyle
import com.reqlab.editor.core.XmlMode
import com.reqlab.editor.core.FoldRegion as CoreFoldRegion

// ── Data structures ─────────────────────────────────────────────

/**
 * A foldable region in source code (0-indexed line numbers).
 */
data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val depth: Int = 0,
)

/**
 * A line that is visible after applying fold operations.
 */
data class VisibleLine(
    val originalIndex: Int,
    val text: String,
    val isFoldStart: Boolean = false,
    val isFolded: Boolean = false,
    val foldedLineCount: Int = 0,
)

// ── Fold state ──────────────────────────────────────────────────

/**
 * Observable fold state.  Uses Compose [mutableStateListOf] so that
 * toggling a fold automatically triggers recomposition.
 */
class FoldState {
    val foldedStartLines = mutableStateListOf<Int>()

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

// ── Region detection: brace-based (JSON / JS / GraphQL) ────────

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

fun detectXmlFoldRegions(lines: List<String>): List<FoldRegion> {
    data class TagInfo(val name: String, val line: Int, val depth: Int)

    val regions = mutableListOf<FoldRegion>()
    val stack = mutableListOf<TagInfo>()

    lines.forEachIndexed { lineIndex, line ->
        for (m in TAG_CLOSE_REGEX.findAll(line)) {
            val tagName = m.groupValues[1].lowercase()
            val openIndex = stack.indexOfLast { it.name == tagName }
            if (openIndex >= 0) {
                val open = stack.removeAt(openIndex)
                while (stack.size > openIndex) stack.removeAt(stack.lastIndex)
                if (lineIndex > open.line) {
                    regions.add(FoldRegion(open.line, lineIndex, open.depth))
                }
            }
        }

        for (m in TAG_OPEN_REGEX.findAll(line)) {
            val tagName = m.groupValues[1].lowercase()
            if (tagName in XmlMode.VOID_ELEMENTS) continue
            val fullMatch = m.value
            if (SELF_CLOSING_REGEX.containsMatchIn(fullMatch)) continue
            stack.add(TagInfo(tagName, lineIndex, stack.size))
        }
    }

    return regions.sortedBy { it.startLine }
}

// ── Multi-line comment fold regions ─────────────────────────────

fun detectCommentFoldRegions(lines: List<String>): List<FoldRegion> {
    val regions = mutableListOf<FoldRegion>()
    var commentStart = -1

    lines.forEachIndexed { idx, line ->
        val trimmed = line.trim()
        if (commentStart < 0 && trimmed.contains("/*")) {
            commentStart = idx
        }
        if (commentStart >= 0 && trimmed.contains("*/")) {
            if (idx > commentStart) regions.add(FoldRegion(commentStart, idx))
            commentStart = -1
        }
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
 * Detects fold regions for [lines] using the [LanguageModeProvider] registered for [mode].
 *
 * The dispatch is driven by [LanguageModeProvider.foldingStyle] — a stable 3-case enum —
 * so **adding a new language never requires touching this function**.  Simply implement
 * [LanguageModeProvider] with the correct [FoldingStyle] and register it in [LanguageRegistry].
 */
fun detectFoldRegions(lines: List<String>, mode: LanguageMode): List<FoldRegion> {
    if (lines.size < 3) return emptyList()
    if (!LanguageRegistry.hasProvider(mode)) LanguageRegistry.registerBuiltins()
    val provider = LanguageRegistry.getProvider(mode)

    // Ask the language provider for its structural fold regions.
    val coreRegions = runCatching {
        val doc = EditorDocument.create(lines.joinToString("\n"))
        provider.foldingRegions(doc)
            .map { FoldRegion(startLine = it.startLine - 1, endLine = it.endLine - 1) }
            .filter { it.endLine > it.startLine }
    }.getOrDefault(emptyList())

    // UI-layer fallbacks — driven by foldingStyle, not by language identity.
    val legacyRegions: List<FoldRegion> = when (provider.foldingStyle) {
        FoldingStyle.BRACE -> detectBraceFoldRegions(lines) + detectCommentFoldRegions(lines)
        FoldingStyle.XML   -> detectXmlFoldRegions(lines)   + detectCommentFoldRegions(lines)
        FoldingStyle.PLAIN -> emptyList()
    }

    return (coreRegions + legacyRegions)
        .filter { it.endLine > it.startLine }
        .distinctBy { it.startLine to it.endLine }
        .sortedBy { it.startLine }
}

// ── Visible-line computation ────────────────────────────────────

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
