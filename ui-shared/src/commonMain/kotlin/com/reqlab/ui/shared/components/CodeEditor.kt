package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.editor.core.InlineEditorError
import com.reqlab.editor.core.InlineErrorSeverity
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorRendererV2
import com.reqlab.editor.ui.EditorTheme
import com.reqlab.editor.ui.EditorViewModelV2
import com.reqlab.editor.ui.SyntaxHighlighterRegistry
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.platform.copyToClipboard as platformCopyToClipboard
import com.reqlab.ui.shared.platform.readFromClipboard
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Constants ───────────────────────────────────────────────────

private const val LARGE_LINE_THRESHOLD = 200

/**
 * When editable content exceeds this many lines, switch to [UnifiedEditableContent]
 * which uses a single BasicTextField backed by [EditorDocument].
 * Both paths use a single-document model; this threshold only controls
 * which composable function handles the layout.
 */
private const val UNIFIED_EDIT_THRESHOLD = 500

/**
 * Character count above which we always use [UnifiedEditableContent] even if the
 * document has few lines (e.g. a 5 MB minified single-line JSON).
 */
private const val UNIFIED_EDIT_CHAR_THRESHOLD = 200_000

private val EditorLineHeight = 20.sp
private val EditorFontSize = 13.sp
private const val FOLD_MARKER = " ... "

/**
 * Character count above which the V2 virtualized editor is used for the editable path.
 * Below this threshold [UnifiedEditableContent] is used (supports search, fold, etc.).
 * Above it, [EditorRendererV2] replaces [UnifiedEditableContent] to prevent UI-thread hangs.
 */
private const val V2_EDITABLE_THRESHOLD = 100_000

/**
 * Threshold in characters above which syntax highlighting is disabled in the
 * single-BasicTextField editable path (`EditableCodeContent`).  Must be at
 * least as large as VIRTUALIZED_EDIT_CHAR_THRESHOLD (200 K) so that every
 * document that reaches EditableCodeContent still gets colours.  Building the
 * AnnotatedString is deferred by 150 ms so even 200 K chars is fine.
 */
private const val HIGHLIGHT_CHAR_THRESHOLD = 12_000_000

/**
 * Compose text layout can still fail on extremely long single-line payloads.
 * Keep a high single-line cap to preserve stability while leaving multi-line
 * large files (e.g. formatted 10 MB JSON) fully editable.
 */
private const val SINGLE_LINE_SAFE_FIELD_CHARS = 50_000

/**
 * Above this many visible lines, rendering one gutter composable per line becomes
 * expensive. Switch to a compact gutter to keep very large files responsive.
 */
private const val FULL_GUTTER_MAX_LINES = 5_000
private const val COMPACT_MODE_SAFE_FIELD_CHARS = 200_000
private const val FULL_GUTTER_MAX_LINE_CHARS = 20_000
private const val MAX_GUTTER_LINE_HEIGHT_PX = 200_000f

/** Width of one indent level for guide rendering. ~2 spaces in monospace. */
private val INDENT_GUIDE_WIDTH = 18.dp
private val INDENT_GUIDE_COLOR = Color(0xFF4E4E4E) // subtle grey, 35% opacity applied at draw
private val INDENT_GUIDE_DASH = floatArrayOf(3f, 4f) // 3px dash, 4px gap

/**
 * Computes the indentation depth (number of leading whitespace units) for a line.
 * Each tab counts as one level; spaces are grouped by [tabSize] (default 2).
 */
private fun indentDepth(line: String, tabSize: Int = 2): Int {
    var spaces = 0
    var tabs = 0
    for (ch in line) {
        when (ch) {
            ' '  -> spaces++
            '\t' -> tabs++
            else -> break
        }
    }
    return tabs + spaces / tabSize
}

private fun chunkSingleLineForDisplay(source: String, chunkSize: Int = 2_000): String {
    if (source.length <= chunkSize) return source
    val out = StringBuilder(source.length + source.length / chunkSize)
    var i = 0
    while (i < source.length) {
        val end = (i + chunkSize).coerceAtMost(source.length)
        out.append(source, i, end)
        if (end < source.length) out.append('\n')
        i = end
    }
    return out.toString()
}

// ── Public API ──────────────────────────────────────────────────

/**
 * Full-featured code editor / viewer with syntax highlighting,
 * code folding, search, formatting, word wrap, copy, and download.
 *
 * @param text          Current content.
 * @param onTextChange  Callback when the user edits text.
 *                      When `null` the editor is **read-only**.
 * @param language      Determines syntax highlighting and fold detection.
 * @param showToolbar   Whether to show the toolbar row.
 * @param enableFolding Enable code folding (read-only mode only).
 * @param enableSearch  Show the search toggle in the toolbar.
 * @param enableFormat  Show the format/beautify toggle.
 * @param enableWordWrap Show the word-wrap toggle.
 * @param enableCopy    Show copy-to-clipboard button.
 * @param enableDownload Show download-to-file button.
 * @param onDownload    Callback for the download action.
 * @param placeholder   Placeholder text shown when the editor is empty.
 * @param inlineErrors  Diagnostics to underline inline in editable mode.
 *                      Errors show as a red underline; warnings as amber.
 * @param testTagPrefix Prefix for Compose test tags.
 */
@kotlinx.serialization.ExperimentalSerializationApi
@Composable
fun CodeEditor(
    text: String,
    onTextChange: ((String) -> Unit)? = null,
    language: SyntaxLanguage = SyntaxLanguage.PLAIN,
    modifier: Modifier = Modifier,
    showToolbar: Boolean = true,
    enableFolding: Boolean = true,
    enableSearch: Boolean = true,
    enableFormat: Boolean = true,
    enableWordWrap: Boolean = true,
    enableCopy: Boolean = true,
    enableDownload: Boolean = false,
    onDownload: (() -> Unit)? = null,
    placeholder: String = "",
    inlineErrors: List<InlineEditorError> = emptyList(),
    testTagPrefix: String = "code-editor",
) {
    val isReadOnly = onTextChange == null

    // ── V2 ViewModel (manages all document state) ─────────────
    val languageMode = language.toLanguageMode()
    val viewModel = remember(languageMode) {
        if (!SyntaxHighlighterRegistry.hasHighlighter(LanguageMode.PLAIN_TEXT)) {
            SyntaxHighlighterRegistry.registerBuiltinHighlighters()
        }
        EditorViewModelV2(initialText = text, languageMode = languageMode)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }

    // ── Format / display state ───────────────────────────────
    var isFormatted by remember { mutableStateOf(isReadOnly) }
    val displayText = remember(text, isFormatted, language) {
        if (isFormatted && isReadOnly) autoFormat(text, language) else text
    }
    // Keep V2 doc in sync with external text (or formatted text for read-only)
    LaunchedEffect(displayText) { viewModel.onExternalTextChanged(displayText) }

    // ── Toolbar state ────────────────────────────────────────
    var wordWrap by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // ── Fold regions (synchronous, for toolbar display) ───────
    val allLines = remember(displayText) { displayText.split('\n') }
    val toolbarFoldRegions: List<FoldRegion> = remember(allLines, language) {
        if (enableFolding) detectFoldRegions(allLines, language) else emptyList()
    }

    // ── Search matches (synchronous, for search bar match count) ─
    val visibleTexts   = remember(allLines) { allLines }
    val searchMatches  = remember(visibleTexts, searchQuery) {
        findSearchMatches(visibleTexts, searchQuery)
    }
    LaunchedEffect(searchMatches.size) {
        activeMatchIndex = if (searchMatches.isNotEmpty())
            activeMatchIndex.coerceIn(0, searchMatches.size - 1) else 0
    }

    // ── Layout ───────────────────────────────────────────────
    Column(modifier = modifier.testTag(testTagPrefix)) {

        // Toolbar
        if (showToolbar) {
            CodeEditorToolbar(
                language = language,
                isReadOnly = isReadOnly,
                wordWrap = wordWrap,
                onToggleWordWrap = if (enableWordWrap) {
                    { wordWrap = !wordWrap }
                } else null,
                isFormatted = isFormatted,
                onToggleFormat = if (enableFormat) {
                    {
                        if (isReadOnly) {
                            isFormatted = !isFormatted
                        } else {
                            val formatted = autoFormat(text, language)
                            if (formatted != text) onTextChange?.invoke(formatted)
                        }
                    }
                } else null,
                showSearch = showSearch,
                onToggleSearch = if (enableSearch) {
                    {
                        showSearch = !showSearch
                        if (!showSearch) searchQuery = ""
                    }
                } else null,
                onCopy = if (enableCopy) {
                    { platformCopyToClipboard(viewModel.getFullText()) }
                } else null,
                onDownload = if (enableDownload) onDownload else null,
                hasFoldRegions = toolbarFoldRegions.isNotEmpty(),
                onFoldAll   = if (enableFolding && toolbarFoldRegions.isNotEmpty()) {
                    { viewModel.foldAll() }
                } else null,
                onUnfoldAll = if (enableFolding && toolbarFoldRegions.isNotEmpty()) {
                    { viewModel.unfoldAll() }
                } else null,
                testTagPrefix = testTagPrefix,
            )
        }

        // Search bar
        if (showSearch) {
            CodeEditorSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it; activeMatchIndex = 0 },
                matchCount = searchMatches.size,
                activeIndex = activeMatchIndex,
                onNext = {
                    if (searchMatches.isNotEmpty()) {
                        activeMatchIndex = (activeMatchIndex + 1) % searchMatches.size
                    }
                },
                onPrev = {
                    if (searchMatches.isNotEmpty()) {
                        activeMatchIndex = (activeMatchIndex - 1 + searchMatches.size) % searchMatches.size
                    }
                },
                onClose = { showSearch = false; searchQuery = "" },
                testTagPrefix = testTagPrefix,
            )
        }

        // ── Content: always V2 (GapBuffer + LazyColumn, no UI-thread hangs) ──
        EditorRendererV2(
            viewModel      = viewModel,
            isReadOnly     = isReadOnly,
            language       = language.toLanguageMode(),
            theme          = EditorTheme.Dark,
            wordWrap       = wordWrap,
            testTagPrefix  = testTagPrefix,
            modifier       = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("$testTagPrefix-input"),
            onTextChange   = onTextChange,
            onPasteRequest = { readFromClipboard() },
            onCopyRequest  = { text -> platformCopyToClipboard(text) },
        )
    }
}

// ── Toolbar ─────────────────────────────────────────────────────

@Composable
private fun CodeEditorToolbar(
    language: SyntaxLanguage,
    isReadOnly: Boolean,
    wordWrap: Boolean,
    onToggleWordWrap: (() -> Unit)?,
    isFormatted: Boolean,
    onToggleFormat: (() -> Unit)?,
    showSearch: Boolean,
    onToggleSearch: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    hasFoldRegions: Boolean,
    onFoldAll: (() -> Unit)?,
    onUnfoldAll: (() -> Unit)?,
    testTagPrefix: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.SurfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("$testTagPrefix-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Language badge
        Text(
            text = language.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = ReqLabColors.Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(ReqLabColors.Primary.copy(alpha = 0.10f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        Spacer(Modifier.weight(1f))

        // Format / beautify
        if (onToggleFormat != null) {
            ToolbarBtn(
                icon = Icons.Default.FormatAlignLeft,
                contentDescription = if (isFormatted) Strings.raw else Strings.format,
                active = isFormatted && isReadOnly,
                onClick = onToggleFormat,
                testTag = "$testTagPrefix-format-toggle",
            )
        }

        // Word wrap
        if (onToggleWordWrap != null) {
            ToolbarBtn(
                icon = Icons.Default.WrapText,
                contentDescription = if (wordWrap) "Disable word wrap" else "Enable word wrap",
                active = wordWrap,
                onClick = onToggleWordWrap,
                testTag = "$testTagPrefix-word-wrap-toggle",
            )
        }

        // Search
        if (onToggleSearch != null) {
            ToolbarBtn(
                icon = Icons.Default.Search,
                contentDescription = Strings.search,
                active = showSearch,
                onClick = onToggleSearch,
                testTag = "$testTagPrefix-search-toggle",
            )
        }

        // Fold / Unfold
        if (hasFoldRegions) {
            ToolbarBtn(
                icon = Icons.Default.UnfoldLess,
                contentDescription = "Fold all",
                onClick = onFoldAll ?: {},
                testTag = "$testTagPrefix-fold-all",
            )
            ToolbarBtn(
                icon = Icons.Default.UnfoldMore,
                contentDescription = "Unfold all",
                onClick = onUnfoldAll ?: {},
                testTag = "$testTagPrefix-unfold-all",
            )
        }

        // Separator before clipboard actions
        if (onCopy != null || onDownload != null) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(ReqLabColors.Border),
            )
        }

        // Copy
        if (onCopy != null) {
            ToolbarBtn(
                icon = Icons.Default.ContentCopy,
                contentDescription = Strings.copyBody,
                onClick = onCopy,
                testTag = "$testTagPrefix-copy-button",
            )
        }

        // Download
        if (onDownload != null) {
            ToolbarBtn(
                icon = Icons.Default.Download,
                contentDescription = Strings.downloadResponse,
                onClick = onDownload,
                testTag = "$testTagPrefix-download-button",
            )
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
}

@Composable
private fun ToolbarBtn(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit,
    testTag: String = "",
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(28.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Search Bar ──────────────────────────────────────────────────

@Composable
private fun CodeEditorSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    activeIndex: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    testTagPrefix: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReqLabColors.SurfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("$testTagPrefix-search-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = ReqLabColors.OnSurface,
                fontSize = 12.sp,
                fontFamily = CodeFontFamily,
            ),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(ReqLabColors.Background)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("$testTagPrefix-search-input"),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            Strings.searchInResponse,
                            color = ReqLabColors.OnSurfaceDim,
                            fontSize = 12.sp,
                            fontFamily = CodeFontFamily,
                        )
                    }
                    inner()
                }
            },
        )

        if (query.isNotEmpty()) {
            Text(
                text = if (matchCount > 0) "${activeIndex + 1}/$matchCount" else Strings.noResults,
                color = if (matchCount > 0) ReqLabColors.OnSurfaceVariant else ReqLabColors.OnSurfaceDim,
                fontSize = 11.sp,
                modifier = Modifier.widthIn(min = 60.dp),
            )
        }

        IconButton(onClick = onPrev, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.ArrowUpward, "Previous match", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.ArrowDownward, "Next match", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, "Close search", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
}

// ── Inline error highlighting helper ───────────────────────────

/**
 * Builds an [AnnotatedString] that combines syntax highlighting with
 * [TextDecoration.Underline] spans for each [InlineEditorError].
 *
 * Error positions use 1-based line/column coordinates. The underline
 * spans from the error column to the end of that logical line, so the
 * user can see exactly which line is problematic without a banner.
 */
private fun buildHighlightedWithErrors(
    text: String,
    language: SyntaxLanguage,
    errors: List<InlineEditorError>,
): AnnotatedString {
    val base = highlightText(text, language)
    if (errors.isEmpty()) return base

    val lines = text.lines()
    return buildAnnotatedString {
        append(base)
        errors.forEach { err ->
            val lineIdx = (err.line - 1).coerceIn(0, lines.lastIndex)
            val lineStart = lines.take(lineIdx).sumOf { it.length + 1 }
            val lineLen = lines[lineIdx].length
            var spanStart = (lineStart + (err.col - 1).coerceAtLeast(0))
                .coerceIn(lineStart, lineStart + lineLen)
            var spanEnd = (lineStart + lineLen).coerceAtLeast(spanStart)

            // If the reported column is at line end (or beyond), ensure we still
            // underline a visible character so errors like trailing commas are shown.
            if (spanStart >= spanEnd && lineLen > 0) {
                spanStart = (lineStart + lineLen - 1).coerceAtLeast(lineStart)
                spanEnd = lineStart + lineLen
            }

            if (spanStart < spanEnd) {
                val underlineColor = if (err.severity == InlineErrorSeverity.ERROR) {
                    Color(0xFFFF6B6B)
                } else {
                    Color(0xFFFFBB44)
                }
                addStyle(
                    SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        color = underlineColor,
                    ),
                    spanStart,
                    spanEnd,
                )
            }
        }
    }
}

// ── Editable content ────────────────────────────────────────────

/**
 * Tracks one collapsed region inside [EditableCodeContent].
 * [docStart] and [docEnd] are line indices in the FULL document text.
 * [hiddenLines] are the document lines from docStart+1 through docEnd
 * that were removed from the view and must be reinserted on unfold.
 */
private data class ActiveFold(
    val docStart: Int,
    val docEnd: Int,
    val hiddenLines: List<String>,
)

/**
 * Editable code pane with:
 * - Pixel-perfect line number gutter (word-wrap–aware via [TextLayoutResult]).
 * - Inline error/warning underlines via [inlineErrors].
 * - Syntax highlighting (disabled above [HIGHLIGHT_CHAR_THRESHOLD] chars).
 * - Real code folding: clicking ▼/▶ in the gutter actually removes/restores
 *   lines from the [BasicTextField] so collapsed regions are truly hidden.
 *   When the user types while folds are active the folds are cleared and the
 *   current view text becomes the new document — simple and safe.
 */
@Composable
private fun EditableCodeContent(
    text: String,
    onTextChange: (String) -> Unit,
    language: SyntaxLanguage,
    @Suppress("UNUSED_PARAMETER") wordWrap: Boolean,
    placeholder: String,
    inlineErrors: List<InlineEditorError> = emptyList(),
    foldRegions: List<FoldRegion> = emptyList(),
    foldState: FoldState? = null,
    testTagPrefix: String,
) {
    val density = LocalDensity.current

    // ── Active fold state ─────────────────────────────────────────
    // Tracks which document regions are currently collapsed.
    val activeFolds = remember { mutableStateListOf<ActiveFold>() }

    // Stable snapshots used as remember-keys (avoids re-keying on every tick).
    val activeFoldKey = activeFolds.joinToString(",") { "${it.docStart}:${it.docEnd}" }

    // Clear folds whenever the full document text changes externally (user typed).
    LaunchedEffect(text) { activeFolds.clear() }

    // ── View-text derivation ──────────────────────────────────────
    // viewText  = text with hidden lines removed.
    // docForView[i]  = document-line index for view line i.
    // viewForDoc[d]  = view-line index for document line d (only visible lines).
    val docLines = remember(text) { text.lines() }
    data class ViewMapping(
        val viewText: String,
        val docForView: List<Int>,
        val viewForDoc: Map<Int, Int>,
    )
    val vm = remember(text, activeFoldKey) {
        if (activeFolds.isEmpty()) {
            val idx = docLines.indices.toList()
            ViewMapping(text, idx, idx.associateWith { it })
        } else {
            val byDocStart = activeFolds.associateBy { it.docStart }
            val viewLines  = mutableListOf<String>()
            val docForView = mutableListOf<Int>()
            val viewForDoc = mutableMapOf<Int, Int>()
            var d = 0
            while (d < docLines.size) {
                val fold = byDocStart[d]
                val v    = viewLines.size
                viewLines.add(docLines[d])
                docForView.add(d)
                viewForDoc[d] = v
                d = if (fold != null) fold.docEnd + 1 else d + 1
            }
            ViewMapping(viewLines.joinToString("\n"), docForView, viewForDoc)
        }
    }
    val viewText    = vm.viewText
    val docForView  = vm.docForView
    val viewForDoc  = vm.viewForDoc

    // ── Fold / unfold helpers ─────────────────────────────────────
    val foldRegionByDocLine = remember(foldRegions) { foldRegions.associateBy { it.startLine } }
    val activeFoldByDocStart = activeFolds.associateBy { it.docStart }

    fun foldAt(viewLine: Int) {
        val doc    = docForView.getOrElse(viewLine) { return }
        val region = foldRegionByDocLine[doc] ?: return
        if (activeFoldByDocStart.containsKey(doc)) return          // already folded
        val end = region.endLine.coerceAtMost(docLines.lastIndex)
        if (end <= doc) return
        activeFolds.add(ActiveFold(doc, end, docLines.subList(doc + 1, end + 1).toList()))
    }

    fun unfoldAt(viewLine: Int) {
        val doc  = docForView.getOrElse(viewLine) { return }
        val fold = activeFoldByDocStart[doc] ?: return
        activeFolds.remove(fold)
    }

    // ── Styles ────────────────────────────────────────────────────
    val textStyle = TextStyle(
        color = ReqLabColors.OnSurface,
        fontSize = EditorFontSize,
        lineHeight = EditorLineHeight,
        fontFamily = CodeFontFamily,
    )
    val lineNumStyle = TextStyle(
        color = ReqLabColors.OnSurfaceDim.copy(alpha = 0.55f),
        fontSize = EditorFontSize,
        lineHeight = EditorLineHeight,
        fontFamily = CodeFontFamily,
        textAlign = TextAlign.End,
    )

    val effectiveLanguage = if (text.length > HIGHLIGHT_CHAR_THRESHOLD) SyntaxLanguage.PLAIN else language

    // ── TextField state (tracks the VIEW text) ────────────────────
    // Initial value uses plain (un-highlighted) text to avoid an expensive
    // O(n) highlighting call during composition.  Highlighting is deferred
    // to the LaunchedEffect below.
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(annotatedString = AnnotatedString(viewText)),
        )
    }

    // ── Immediate text sync (fold / unfold) ───────────────────────
    // When the user collapses or expands a fold region, `viewText` changes
    // but `fieldValue` still holds the old full text.  We must sync
    // immediately (no delay) so the BasicTextField shows the folded view;
    // otherwise the gutter and content show different line counts → jitter.
    //
    // Two correctness requirements:
    //  1. Clamp BOTH selection endpoints (not just start) so a Cmd+A or drag
    //     selection made in the old text does not extend beyond the new text.
    //  2. Re-request focus after replacing fieldValue.  On macOS the IME
    //     connection can be silently reset when the TextFieldValue is replaced
    //     with a different text, causing all keyboard shortcuts (Cmd+A, Cmd+Z,
    //     …) to stop working until the user clicks in the text field again.
    val inputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { inputFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(viewText) {
        if (fieldValue.text != viewText) {
            // Preserve the full selection range — clamp both endpoints.
            val newLen = viewText.length
            val selStart = fieldValue.selection.start.coerceAtMost(newLen)
            val selEnd   = fieldValue.selection.end.coerceAtMost(newLen)
            fieldValue = TextFieldValue(
                annotatedString = AnnotatedString(viewText),
                selection = androidx.compose.ui.text.TextRange(selStart, selEnd),
            )
            // Restore focus: fold/unfold can reset the native IME connection on
            // desktop, so explicitly re-request Compose focus.
            try { inputFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // ── Debounced highlighting ─────────────────────────────────────
    // After typing/folding settles for 150 ms, apply full syntax colours.
    // Runs on Dispatchers.Default to avoid blocking the main thread.
    LaunchedEffect(viewText, inlineErrors, effectiveLanguage) {
        delay(150)
        if (fieldValue.text == viewText) {
            val want = withContext(Dispatchers.Default) {
                buildHighlightedWithErrors(viewText, effectiveLanguage, inlineErrors)
            }
            if (fieldValue.text == viewText && fieldValue.annotatedString != want) {
                fieldValue = fieldValue.copy(annotatedString = want)
            }
        }
    }

    val viewLines = remember(viewText) { viewText.lines() }
    val hasVeryLongLine = remember(viewLines) { viewLines.any { it.length > FULL_GUTTER_MAX_LINE_CHARS } }

    // ── Gutter geometry ───────────────────────────────────────────
    val hasFoldIndicators = foldRegions.isNotEmpty()
    // Gutter width is stable: based on doc-line count (never changes mid-session
    // due to folding) plus indicator column when fold regions exist.
    val gutterWidth = remember(docLines.size, hasFoldIndicators) {
        (docLines.size.toString().length * 9 + 20 + if (hasFoldIndicators) 16 else 0).dp
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Per-view-line heights for word-wrap–aligned gutter.  Falls back to an
    // empty list on first frame; the gutter uses wrapContentHeight instead.
    val logicalLineHeightsDp: List<Dp> = remember(textLayoutResult, viewLines.size) {
        val tlr = textLayoutResult ?: return@remember emptyList()
        val textLen = tlr.layoutInput.text.length
        if (textLen == 0) return@remember viewLines.map { with(density) { EditorLineHeight.toDp() } }
        var charOffset = 0
        viewLines.mapIndexed { _, line ->
            val lineStart = charOffset
            val maxOffset = (textLen - 1).coerceAtLeast(0)
            val s = lineStart.coerceIn(0, maxOffset)
            val e = if (line.isEmpty()) s else (lineStart + line.length - 1).coerceIn(s, maxOffset)
            val first = tlr.getLineForOffset(s)
            val last  = tlr.getLineForOffset(e)
            val h     = tlr.getLineBottom(last) - tlr.getLineTop(first)
            charOffset += line.length + 1
            with(density) { h.toDp() }
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(ReqLabColors.SurfaceContainer)
            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .verticalScroll(scrollState),
        ) {
            // ── Gutter ────────────────────────────────────────────────
            // Single rendering path — fold indicators are ALWAYS present
            // so that inline-error recompositions never remove them.
            // Heights are applied when the layout result is ready; otherwise
            // the row wraps its text content (pixel-perfect on first frame).
            Column(
                modifier = Modifier
                    .width(gutterWidth)
                    .wrapContentHeight()
                    .background(ReqLabColors.SurfaceContainer)
                    .padding(top = 12.dp, bottom = 12.dp)
                    .testTag("$testTagPrefix-line-numbers"),
                horizontalAlignment = Alignment.End,
            ) {
                val heightsReady = logicalLineHeightsDp.size == viewLines.size
                viewLines.forEachIndexed { viewIdx, _ ->
                    val docLine     = docForView.getOrElse(viewIdx) { viewIdx }
                    val isFolded    = activeFoldByDocStart.containsKey(docLine)
                    val isFoldable  = !isFolded && foldRegionByDocLine.containsKey(docLine)
                    Box(
                        modifier = if (heightsReady)
                            Modifier.height(logicalLineHeightsDp[viewIdx]).fillMaxWidth().padding(end = 4.dp)
                        else
                            Modifier.fillMaxWidth().padding(end = 4.dp),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.End) {
                            when {
                                isFolded -> Text(
                                    text = "▸",
                                    color = ReqLabColors.Primary,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        // pointerInput instead of .clickable so that
                                        // the gutter indicator does NOT steal keyboard
                                        // focus from the BasicTextField when tapped.
                                        .pointerInput(viewIdx) {
                                            detectTapGestures(onTap = { unfoldAt(viewIdx) })
                                        }
                                        .testTag("$testTagPrefix-fold-indicator-$docLine")
                                        .padding(end = 2.dp),
                                )
                                isFoldable -> Text(
                                    text = "▾",
                                    color = ReqLabColors.OnSurfaceDim,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .pointerInput(viewIdx) {
                                            detectTapGestures(onTap = { foldAt(viewIdx) })
                                        }
                                        .testTag("$testTagPrefix-fold-indicator-$docLine")
                                        .padding(end = 2.dp),
                                )
                                hasFoldIndicators -> Spacer(Modifier.width(14.dp))
                            }
                            Text(text = "${docLine + 1}", style = lineNumStyle)
                        }
                    }
                }
            }

            // ── Gutter / content divider ─────────────────────────────
            Box(Modifier.width(1.dp).fillMaxHeight().background(ReqLabColors.Border))

            // ── Syntax-highlighted editable text field ───────────────
            val indentDepths    = remember(viewLines) { viewLines.map { indentDepth(it) } }
            val indentGuidePx   = with(density) { INDENT_GUIDE_WIDTH.toPx() }

            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    // Compare against fieldValue.text (what BasicTextField currently
                    // shows) NOT against viewText.
                    //
                    // The race: after a fold click, viewText changes immediately
                    // (activeFolds mutated) but fieldValue.text is still the old
                    // unfolded text until LaunchedEffect(viewText) fires (next
                    // coroutine dispatch).  During that window BasicTextField still
                    // holds the unfolded text, so any cursor move / Cmd+A / Cmd+Z
                    // would produce newValue.text == fieldValue.text (unchanged)
                    // but newValue.text != viewText (folded).  The old comparison
                    // would wrongly treat this as a text edit, clear all folds,
                    // and call onTextChange — silently reverting the fold and
                    // breaking every keyboard shortcut after fold.
                    if (newValue.text != fieldValue.text) {
                        // Real text edit from user (typing, paste, undo/redo).
                        activeFolds.clear()
                        fieldValue = TextFieldValue(
                            AnnotatedString(newValue.text),
                            newValue.selection,
                            newValue.composition,
                        )
                        onTextChange(newValue.text)
                    } else {
                        // Cursor / selection move only — preserve highlighted spans.
                        fieldValue = fieldValue.copy(
                            selection = newValue.selection,
                            composition = newValue.composition,
                        )
                    }
                },
                textStyle = textStyle,
                cursorBrush = SolidColor(ReqLabColors.Primary),
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                    .testTag("$testTagPrefix-input")
                    .focusRequester(inputFocusRequester)
                    .drawBehind {
                        val tlr = textLayoutResult ?: return@drawBehind
                        val guideColor  = INDENT_GUIDE_COLOR.copy(alpha = 0.35f)
                        val dashEffect  = PathEffect.dashPathEffect(INDENT_GUIDE_DASH, 0f)
                        var charOff = 0
                        for (li in indentDepths.indices) {
                            val depth = indentDepths[li]
                            if (depth <= 0) { charOff += viewLines[li].length + 1; continue }
                            val s0  = charOff.coerceAtMost(tlr.layoutInput.text.length - 1).coerceAtLeast(0)
                            val vls = tlr.getLineForOffset(s0)
                            val e0  = (charOff + viewLines[li].length).coerceAtMost(tlr.layoutInput.text.length - 1).coerceAtLeast(0)
                            val vle = tlr.getLineForOffset(e0)
                            val top = tlr.getLineTop(vls)
                            val bot = tlr.getLineBottom(vle)
                            for (d in 1..depth) {
                                val x = d * indentGuidePx
                                drawLine(guideColor, Offset(x, top), Offset(x, bot), 1f, pathEffect = dashEffect)
                            }
                            charOff += viewLines[li].length + 1
                        }
                    },
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
                        }
                        inner()
                    }
                },
            )
        }

        // ── Scrollbar ────────────────────────────────────────────────
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

// ── Unified editable content (all file sizes — single document model) ─

/**
 * Unified editor for all file sizes — the single-document-model replacement
 * for the old per-line VirtualizedEditableCodeContent.
 *
 * Architecture
 * ────────────
 * An [EditorDocument] holds the entire text and a precomputed line-start
 * index for O(1) offset/line conversions.  A single [BasicTextField] always
 * shows the full view text (after folding) so that Cmd+A, Backspace across
 * lines, and multi-line selection all work natively — exactly as they do for
 * small files in [EditableCodeContent].
 *
 * Folding uses the same activeFolds / ViewMapping pattern as [EditableCodeContent].
 *
 * Large files
 * ───────────
 * For documents exceeding [HIGHLIGHT_CHAR_THRESHOLD] chars the syntax-
 * highlighting AnnotatedString step is skipped (plain text only) so that
 * Compose never has to build million-char spans.  The document is still fully
 * editable — only the colour decoration is omitted.
 *
 * Cursor & selection model
 * ────────────────────────
 * Because the entire view text lives in one BasicTextField the cursor and
 * selection state is managed natively by Compose.  There is no per-line
 * index translation needed.  Cmd+A selects all visible (unfolded) text.
 * Backspace at column 0 merges with the previous line.  Pasting multi-line
 * content inserts all lines in one operation.
 */
@Composable
private fun UnifiedEditableContent(
    text: String,
    onTextChange: (String) -> Unit,
    language: SyntaxLanguage,
    @Suppress("UNUSED_PARAMETER") wordWrap: Boolean,
    placeholder: String,
    inlineErrors: List<InlineEditorError> = emptyList(),
    foldRegions: List<FoldRegion> = emptyList(),
    foldState: FoldState? = null,
    testTagPrefix: String,
    precomputedLines: List<String>? = null,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // ── Document model ────────────────────────────────────────────
    val selfEditRef = remember { object { var lastText: String = text } }

    // ── Active fold state ─────────────────────────────────────────
    val activeFolds = remember { mutableStateListOf<ActiveFold>() }
    val activeFoldKey = activeFolds.joinToString(",") { "${it.docStart}:${it.docEnd}" }

    LaunchedEffect(text) {
        if (text != selfEditRef.lastText) {
            activeFolds.clear()
            selfEditRef.lastText = text
        }
    }

    // ── View-text derivation ──────────────────────────────────────
    // Reuse precomputed lines from the outer CodeEditor when available,
    // avoiding a redundant O(n) text.lines() scan on multi-MB strings.
    val docLines = precomputedLines ?: remember(text) { text.lines() }

    data class ViewMapping(
        val viewText: String,
        val docForView: List<Int>,
        val viewForDoc: Map<Int, Int>,
    )

    val vm = remember(text, activeFoldKey) {
        if (activeFolds.isEmpty()) {
            val idx = docLines.indices.toList()
            ViewMapping(text, idx, idx.associateWith { it })
        } else {
            val byDocStart = activeFolds.associateBy { it.docStart }
            val viewLines  = mutableListOf<String>()
            val docForView = mutableListOf<Int>()
            val viewForDoc = mutableMapOf<Int, Int>()
            var d = 0
            while (d < docLines.size) {
                val fold = byDocStart[d]
                val v    = viewLines.size
                viewLines.add(docLines[d])
                docForView.add(d)
                viewForDoc[d] = v
                d = if (fold != null) fold.docEnd + 1 else d + 1
            }
            ViewMapping(viewLines.joinToString("\n"), docForView, viewForDoc)
        }
    }

    val viewText   = vm.viewText
    val docForView = vm.docForView

    // ── Fold / unfold helpers ─────────────────────────────────────
    val foldRegionByDocLine  = remember(foldRegions) { foldRegions.associateBy { it.startLine } }
    val activeFoldByDocStart = activeFolds.associateBy { it.docStart }

    fun foldAt(viewLine: Int) {
        val doc    = docForView.getOrElse(viewLine) { return }
        val region = foldRegionByDocLine[doc] ?: return
        if (activeFoldByDocStart.containsKey(doc)) return
        val end = region.endLine.coerceAtMost(docLines.lastIndex)
        if (end <= doc) return
        activeFolds.add(ActiveFold(doc, end, docLines.subList(doc + 1, end + 1).toList()))
    }

    fun unfoldAt(viewLine: Int) {
        val doc  = docForView.getOrElse(viewLine) { return }
        val fold = activeFoldByDocStart[doc] ?: return
        activeFolds.remove(fold)
    }

    // ── Sync parent FoldState (toolbar fold-all / unfold-all) ─────
    val foldGeneration = foldState?.foldedStartLines?.size ?: 0
    LaunchedEffect(foldGeneration) {
        if (foldState != null) {
            val parentFolded = foldState.foldedStartLines
            for (startLine in parentFolded) {
                if (!activeFoldByDocStart.containsKey(startLine)) {
                    val region = foldRegionByDocLine[startLine] ?: continue
                    val end = region.endLine.coerceAtMost(docLines.lastIndex)
                    if (end > startLine) {
                        activeFolds.add(ActiveFold(startLine, end, docLines.subList(startLine + 1, end + 1).toList()))
                    }
                }
            }
            activeFolds.removeAll { it.docStart !in parentFolded }
        }
    }

    // ── Styles ────────────────────────────────────────────────────
    val textStyle = TextStyle(
        color = ReqLabColors.OnSurface,
        fontSize = EditorFontSize,
        lineHeight = EditorLineHeight,
        fontFamily = CodeFontFamily,
    )
    val lineNumStyle = TextStyle(
        color = ReqLabColors.OnSurfaceDim.copy(alpha = 0.55f),
        fontSize = EditorFontSize,
        lineHeight = EditorLineHeight,
        fontFamily = CodeFontFamily,
        textAlign = TextAlign.End,
    )

    val effectiveLanguage = if (text.length > HIGHLIGHT_CHAR_THRESHOLD) SyntaxLanguage.PLAIN else language

    // Cache viewLines — reuse docLines when no folds are active (viewText === text),
    // avoiding a redundant O(n) .lines() scan on multi-MB strings.
    val viewLines = remember(viewText) {
        if (viewText === text) docLines else viewText.lines()
    }

    val compactMode = viewLines.size > FULL_GUTTER_MAX_LINES
    val requiresCompactCap = compactMode && viewText.length > COMPACT_MODE_SAFE_FIELD_CHARS
    val requiresSingleLineCap = !requiresCompactCap && viewLines.size == 1 && viewText.length > SINGLE_LINE_SAFE_FIELD_CHARS
    val appliedCap = when {
        requiresCompactCap -> COMPACT_MODE_SAFE_FIELD_CHARS
        requiresSingleLineCap -> SINGLE_LINE_SAFE_FIELD_CHARS
        else -> -1
    }
    val fieldText = when {
        requiresCompactCap -> viewText.take(COMPACT_MODE_SAFE_FIELD_CHARS)
        requiresSingleLineCap -> chunkSingleLineForDisplay(viewText.take(SINGLE_LINE_SAFE_FIELD_CHARS))
        else -> viewText
    }
    val isDisplayCapped = appliedCap > 0

    // ── TextField state ───────────────────────────────────────────
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(annotatedString = AnnotatedString(fieldText)))
    }

    val inputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { inputFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Immediate sync when fold/unfold changes viewText.
    LaunchedEffect(viewText) {
        if (fieldValue.text != fieldText) {
            val newLen   = fieldText.length
            val selStart = fieldValue.selection.start.coerceAtMost(newLen)
            val selEnd   = fieldValue.selection.end.coerceAtMost(newLen)
            fieldValue = TextFieldValue(
                annotatedString = AnnotatedString(fieldText),
                selection = androidx.compose.ui.text.TextRange(selStart, selEnd),
            )
            try { inputFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Debounced syntax highlighting — runs on Dispatchers.Default so the
    // main/UI thread is NEVER blocked by O(n) char scanning of large text.
    LaunchedEffect(viewText, inlineErrors, effectiveLanguage) {
        delay(150)
        if (fieldValue.text == fieldText) {
            val want = withContext(Dispatchers.Default) {
                buildHighlightedWithErrors(fieldText, effectiveLanguage, inlineErrors)
            }
            // Re-check after async work: another edit may have arrived
            if (fieldValue.text == fieldText && fieldValue.annotatedString != want) {
                fieldValue = fieldValue.copy(annotatedString = want)
            }
        }
    }

    val hasVeryLongLine = remember(viewLines) { viewLines.any { it.length > FULL_GUTTER_MAX_LINE_CHARS } }

    // ── Gutter geometry ───────────────────────────────────────────
    val scrollState = rememberScrollState()
    val hasFoldIndicators = foldRegions.isNotEmpty()
    val renderFullGutter = !isDisplayCapped && !hasVeryLongLine && viewLines.size <= FULL_GUTTER_MAX_LINES
    // Always use verticalScroll so the editor is scrollable regardless of gutter mode.
    // In compact/capped mode the Row's height is determined by the BasicTextField content
    // (because all children use wrapContentHeight in the unconstrained scroll context).
    val containerModifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .verticalScroll(scrollState)
    val gutterWidth = remember(docLines.size, hasFoldIndicators) {
        (docLines.size.toString().length * 9 + 20 + if (hasFoldIndicators) 16 else 0).dp
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val logicalLineHeightsDp: List<Dp> = remember(textLayoutResult, viewLines.size, renderFullGutter) {
        if (!renderFullGutter) return@remember emptyList()
        val tlr = textLayoutResult ?: return@remember emptyList()
        val textLen = tlr.layoutInput.text.length
        if (textLen == 0) return@remember viewLines.map { with(density) { EditorLineHeight.toDp() } }
        var charOffset = 0
        viewLines.mapIndexed { _, line ->
            val lineStart = charOffset
            val maxOffset = (textLen - 1).coerceAtLeast(0)
            val s = lineStart.coerceIn(0, maxOffset)
            val e = if (line.isEmpty()) s else (lineStart + line.length - 1).coerceIn(s, maxOffset)
            val first = tlr.getLineForOffset(s)
            val last  = tlr.getLineForOffset(e)
            val h     = (tlr.getLineBottom(last) - tlr.getLineTop(first)).coerceAtMost(MAX_GUTTER_LINE_HEIGHT_PX)
            charOffset += line.length + 1
            with(density) { h.toDp() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(ReqLabColors.SurfaceContainer)
            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
            // Intercept Cmd+V / Ctrl+V BEFORE BasicTextField sees it.
            // BasicTextField handles paste internally on the main thread:
            // it reads the full clipboard string, constructs a TextFieldValue,
            // and does text layout — all synchronously.  For multi-MB payloads
            // this freezes the UI.  By consuming the key event here and routing
            // through a background coroutine we avoid that main-thread work.
            .onPreviewKeyEvent { event ->
                val isPaste = event.type == KeyEventType.KeyDown &&
                    event.key == Key.V &&
                    (event.isMetaPressed || event.isCtrlPressed)
                if (!isPaste) return@onPreviewKeyEvent false

                coroutineScope.launch {
                    // Read clipboard on Default dispatcher — AWT clipboard access
                    // is synchronous but lightweight; off the main thread to avoid
                    // any incidental blocking (e.g. waiting for clipboard owner).
                    val clipText = withContext(Dispatchers.Default) { readFromClipboard() }
                        ?: return@launch  // null = no text content or not supported

                    // Determine the pasted content, respecting current selection.
                    val currentSelection = fieldValue.selection
                    val currentText = viewText
                    val pastedText: String = if (currentSelection.length == 0) {
                        // No selection — insert at cursor
                        val cursorPos = currentSelection.start.coerceAtMost(currentText.length)
                        currentText.substring(0, cursorPos) + clipText + currentText.substring(cursorPos)
                    } else {
                        // Replace selection
                        val selStart = currentSelection.min.coerceAtMost(currentText.length)
                        val selEnd = currentSelection.max.coerceAtMost(currentText.length)
                        currentText.substring(0, selStart) + clipText + currentText.substring(selEnd)
                    }

                    // Compute capped field text for display (same logic as onValueChange).
                    val hasNewline = pastedText.indexOf('\n') >= 0
                    val nextCompactMode = pastedText.length > COMPACT_MODE_SAFE_FIELD_CHARS && hasNewline
                    val nextRequiresSingleLineCap = !nextCompactMode && !hasNewline &&
                        pastedText.length > SINGLE_LINE_SAFE_FIELD_CHARS
                    val nextRequiresCompactCap = nextCompactMode && pastedText.length > COMPACT_MODE_SAFE_FIELD_CHARS
                    val nextFieldText = when {
                        nextRequiresCompactCap -> pastedText.take(COMPACT_MODE_SAFE_FIELD_CHARS)
                        nextRequiresSingleLineCap -> chunkSingleLineForDisplay(
                            pastedText.take(SINGLE_LINE_SAFE_FIELD_CHARS),
                        )
                        else -> pastedText
                    }
                    val cursorAfterPaste = if (currentSelection.length == 0) {
                        (currentSelection.start + clipText.length).coerceAtMost(nextFieldText.length)
                    } else {
                        (currentSelection.min + clipText.length).coerceAtMost(nextFieldText.length)
                    }

                    activeFolds.clear()
                    fieldValue = TextFieldValue(
                        annotatedString = AnnotatedString(nextFieldText),
                        selection = androidx.compose.ui.text.TextRange(cursorAfterPaste),
                    )
                    selfEditRef.lastText = pastedText
                    onTextChange(pastedText)
                }
                true  // consume the event — prevent BasicTextField from also handling paste
            },
    ) {
        Row(modifier = containerModifier) {
            // ── Gutter ────────────────────────────────────────────────
            if (renderFullGutter) {
                Column(
                    modifier = Modifier
                        .width(gutterWidth)
                        .wrapContentHeight()
                        .background(ReqLabColors.SurfaceContainer)
                        .padding(top = 12.dp, bottom = 12.dp)
                        .testTag("$testTagPrefix-line-numbers"),
                    horizontalAlignment = Alignment.End,
                ) {
                    val heightsReady = logicalLineHeightsDp.size == viewLines.size
                    viewLines.forEachIndexed { viewIdx, _ ->
                        val docLine    = docForView.getOrElse(viewIdx) { viewIdx }
                        val isFolded   = activeFoldByDocStart.containsKey(docLine)
                        val isFoldable = !isFolded && foldRegionByDocLine.containsKey(docLine)
                        Box(
                            modifier = if (heightsReady)
                                Modifier.height(logicalLineHeightsDp[viewIdx]).fillMaxWidth().padding(end = 4.dp)
                            else
                                Modifier.fillMaxWidth().padding(end = 4.dp),
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.End) {
                                when {
                                    isFolded -> Text(
                                        text = "▸",
                                        color = ReqLabColors.Primary,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .pointerInput(viewIdx) {
                                                detectTapGestures(onTap = { unfoldAt(viewIdx) })
                                            }
                                            .testTag("$testTagPrefix-fold-indicator-$docLine")
                                            .padding(end = 2.dp),
                                    )
                                    isFoldable -> Text(
                                        text = "▾",
                                        color = ReqLabColors.OnSurfaceDim,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .pointerInput(viewIdx) {
                                                detectTapGestures(onTap = { foldAt(viewIdx) })
                                            }
                                            .testTag("$testTagPrefix-fold-indicator-$docLine")
                                            .padding(end = 2.dp),
                                    )
                                    hasFoldIndicators -> Spacer(Modifier.width(14.dp))
                                }
                                Text(text = "${docLine + 1}", style = lineNumStyle)
                            }
                        }
                    }
                }
            } else {
                // Compact gutter: shown when lines > FULL_GUTTER_MAX_LINES or display is capped
                // (e.g. a single-line 5MB minified JSON).  Show the actual doc line count so
                // the user always sees a meaningful number instead of the opaque "Ln" label.
                // Compact gutter: wrapContentHeight so it doesn't try to fill infinite
                // height in the verticalScroll-unconstrained Row.
                Box(
                    modifier = Modifier
                        .width(gutterWidth)
                        .wrapContentHeight()
                        .background(ReqLabColors.SurfaceContainer)
                        .testTag("$testTagPrefix-line-numbers"),
                ) {
                    Text(
                        text = "${docLines.size}",
                        style = lineNumStyle,
                        modifier = Modifier.padding(top = 12.dp, end = 6.dp),
                    )
                }
            }

            Box(Modifier.width(1.dp).fillMaxHeight().background(ReqLabColors.Border))

            // ── Single-document-model BasicTextField ─────────────────
            val indentDepths  = remember(viewLines) { viewLines.map { indentDepth(it) } }
            val indentGuidePx = with(density) { INDENT_GUIDE_WIDTH.toPx() }

            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    if (newValue.text != fieldValue.text) {
                        val wasSelectAll =
                            fieldValue.selection.start == 0 &&
                                fieldValue.selection.end == fieldValue.text.length

                        activeFolds.clear()
                        val updatedText = if (appliedCap > 0) {
                            val editedPrefix = if (requiresSingleLineCap) newValue.text.replace("\n", "") else newValue.text
                            if (editedPrefix.isEmpty()) {
                                // Preserve full clear semantics (Cmd+A + Backspace/Delete).
                                ""
                            } else if (wasSelectAll) {
                                // Preserve true replace-all semantics in capped mode
                                // so Cmd+A + Backspace and paste-over-selection work.
                                editedPrefix
                            } else {
                                editedPrefix + viewText.drop(appliedCap)
                            }
                        } else {
                            newValue.text
                        }

                        val nextCompactMode: Boolean
                        val nextRequiresSingleLineCap: Boolean
                        if (updatedText.length <= SINGLE_LINE_SAFE_FIELD_CHARS) {
                            // Small text — no capping needed, skip O(n) indexOf scan.
                            nextCompactMode = false
                            nextRequiresSingleLineCap = false
                        } else {
                            // Single indexOf scan for large text (cache the result).
                            val hasNewline = updatedText.indexOf('\n') >= 0
                            nextCompactMode = updatedText.length > COMPACT_MODE_SAFE_FIELD_CHARS && hasNewline
                            nextRequiresSingleLineCap = !nextCompactMode && !hasNewline && updatedText.length > SINGLE_LINE_SAFE_FIELD_CHARS
                        }
                        val nextRequiresCompactCap =
                            nextCompactMode && updatedText.length > COMPACT_MODE_SAFE_FIELD_CHARS
                        val nextFieldText = when {
                            nextRequiresCompactCap -> updatedText.take(COMPACT_MODE_SAFE_FIELD_CHARS)
                            nextRequiresSingleLineCap -> chunkSingleLineForDisplay(
                                updatedText.take(SINGLE_LINE_SAFE_FIELD_CHARS),
                            )
                            else -> updatedText
                        }
                        val nextSelStart = newValue.selection.start.coerceAtMost(nextFieldText.length)
                        val nextSelEnd = newValue.selection.end.coerceAtMost(nextFieldText.length)
                        fieldValue = TextFieldValue(
                            AnnotatedString(nextFieldText),
                            androidx.compose.ui.text.TextRange(nextSelStart, nextSelEnd),
                            newValue.composition,
                        )

                        selfEditRef.lastText = updatedText
                        onTextChange(updatedText)
                    } else {
                        fieldValue = fieldValue.copy(
                            selection = newValue.selection,
                            composition = newValue.composition,
                        )
                    }
                },
                textStyle = textStyle,
                cursorBrush = SolidColor(ReqLabColors.Primary),
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 1.dp)
                    // In capped mode the Row is unconstrained (verticalScroll), so
                    // fillMaxHeight() would request infinite height.  Use heightIn instead
                    // to allow the BasicTextField to size to its content up to a safe cap.
                    .then(
                        if (isDisplayCapped) Modifier.heightIn(max = 1200.dp)
                        else Modifier.fillMaxHeight()
                    )
                    .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                    .testTag("$testTagPrefix-input")
                    .focusRequester(inputFocusRequester)
                    .drawBehind {
                        val tlr = textLayoutResult ?: return@drawBehind
                        val guideColor  = INDENT_GUIDE_COLOR.copy(alpha = 0.35f)
                        val dashEffect  = PathEffect.dashPathEffect(INDENT_GUIDE_DASH, 0f)
                        var charOff = 0
                        for (li in indentDepths.indices) {
                            val depth = indentDepths[li]
                            if (depth <= 0) { charOff += viewLines[li].length + 1; continue }
                            val s0  = charOff.coerceAtMost(tlr.layoutInput.text.length - 1).coerceAtLeast(0)
                            val vls = tlr.getLineForOffset(s0)
                            val e0  = (charOff + viewLines[li].length).coerceAtMost(tlr.layoutInput.text.length - 1).coerceAtLeast(0)
                            val vle = tlr.getLineForOffset(e0)
                            val top = tlr.getLineTop(vls)
                            val bot = tlr.getLineBottom(vle)
                            for (d in 1..depth) {
                                val x = d * indentGuidePx
                                drawLine(guideColor, Offset(x, top), Offset(x, bot), 1f, pathEffect = dashEffect)
                            }
                            charOff += viewLines[li].length + 1
                        }
                    },
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp, fontFamily = CodeFontFamily)
                        }
                        inner()
                    }
                },
            )
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

// ── Read-only content ───────────────────────────────────────────

@Composable
private fun ReadOnlyCodeContent(
    visibleLines: List<VisibleLine>,
    language: SyntaxLanguage,
    wordWrap: Boolean,
    searchMatches: List<SearchMatch>,
    activeMatchIndex: Int,
    foldRegions: List<FoldRegion>,
    foldState: FoldState,
    lazyListState: LazyListState,
    testTagPrefix: String,
) {
    val foldStartSet = remember(foldRegions) {
        foldRegions.associate { it.startLine to it }
    }

    if (visibleLines.size <= LARGE_LINE_THRESHOLD) {
        SmallCodeView(
            visibleLines = visibleLines,
            language = language,
            wordWrap = wordWrap,
            searchMatches = searchMatches,
            activeMatchIndex = activeMatchIndex,
            foldStartSet = foldStartSet,
            foldState = foldState,
            testTagPrefix = testTagPrefix,
        )
    } else {
        LargeCodeView(
            visibleLines = visibleLines,
            language = language,
            wordWrap = wordWrap,
            searchMatches = searchMatches,
            activeMatchIndex = activeMatchIndex,
            foldStartSet = foldStartSet,
            foldState = foldState,
            lazyListState = lazyListState,
            testTagPrefix = testTagPrefix,
        )
    }
}

// ── Small read-only view (full text selection) ──────────────────

@Composable
private fun SmallCodeView(
    visibleLines: List<VisibleLine>,
    language: SyntaxLanguage,
    wordWrap: Boolean,
    searchMatches: List<SearchMatch>,
    activeMatchIndex: Int,
    foldStartSet: Map<Int, FoldRegion>,
    foldState: FoldState,
    testTagPrefix: String,
) {
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val gutterWidth = (visibleLines.lastOrNull()?.let { it.originalIndex + 1 }?.toString()?.length ?: 1).let { (it * 9 + 24).dp }

    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .then(if (!wordWrap) Modifier.horizontalScroll(hScrollState) else Modifier)
                .testTag("$testTagPrefix-body"),
        ) {
            // Gutter: line numbers + fold indicators
            Column(
                modifier = Modifier
                    .width(gutterWidth)
                    .background(ReqLabColors.SurfaceContainer)
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                visibleLines.forEach { vl ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(IntrinsicSize.Min),
                    ) {
                        // Fold indicator
                        val region = foldStartSet[vl.originalIndex]
                        if (region != null) {
                            Text(
                                text = if (vl.isFolded) ">" else "v",
                                color = ReqLabColors.OnSurfaceDim,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clickable { foldState.toggle(vl.originalIndex) }
                                    .padding(start = 2.dp),
                            )
                        } else {
                            Spacer(Modifier.width(12.dp))
                        }

                        // Line number
                        Text(
                            text = "${vl.originalIndex + 1}",
                            color = ReqLabColors.OnSurfaceDim,
                            fontSize = 12.sp,
                            fontFamily = CodeFontFamily,
                            lineHeight = EditorLineHeight,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }

            // Code content
            val density = LocalDensity.current
            val guideWidthPx = with(density) { INDENT_GUIDE_WIDTH.toPx() }

            Column(modifier = Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                visibleLines.forEachIndexed { displayIdx, vl ->
                    val lineText = if (vl.isFolded) {
                        vl.text + FOLD_MARKER
                    } else {
                        vl.text.ifEmpty { " " }
                    }

                    val highlighted = remember(lineText, language) {
                        highlightLine(lineText, language)
                    }

                    // Apply fold badge
                    val withFold = if (vl.isFolded) {
                        applyFoldBadge(highlighted, vl.text.length)
                    } else {
                        highlighted
                    }

                    // Apply search highlights
                    val lineMatches = searchMatches.filter { it.lineIndex == displayIdx }
                    val globalStart = searchMatches.indexOfFirst {
                        it.lineIndex == displayIdx && it.startOffset == lineMatches.firstOrNull()?.startOffset
                    }
                    val final = if (lineMatches.isNotEmpty() && globalStart >= 0) {
                        applySearchHighlights(withFold, lineMatches, activeMatchIndex, globalStart)
                    } else {
                        withFold
                    }

                    val lineDepth = indentDepth(vl.text)

                    Text(
                        text = final,
                        fontSize = EditorFontSize,
                        fontFamily = CodeFontFamily,
                        softWrap = wordWrap,
                        lineHeight = EditorLineHeight,
                        modifier = if (lineDepth > 0) {
                            Modifier.drawBehind {
                                val guideColor = INDENT_GUIDE_COLOR.copy(alpha = 0.35f)
                                val dashEffect = PathEffect.dashPathEffect(INDENT_GUIDE_DASH, 0f)
                                for (d in 1..lineDepth) {
                                    val x = d * guideWidthPx
                                    drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), 1f, pathEffect = dashEffect)
                                }
                            }
                        } else Modifier,
                    )
                }
            }
        }
    }
}

// ── Large read-only view (virtualised LazyColumn) ───────────────

@Composable
private fun LargeCodeView(
    visibleLines: List<VisibleLine>,
    language: SyntaxLanguage,
    wordWrap: Boolean,
    searchMatches: List<SearchMatch>,
    activeMatchIndex: Int,
    foldStartSet: Map<Int, FoldRegion>,
    foldState: FoldState,
    lazyListState: LazyListState,
    testTagPrefix: String,
) {
    val hScrollState = rememberScrollState()
    val maxLineNum = visibleLines.lastOrNull()?.let { it.originalIndex + 1 } ?: 1
    val gutterWidth = (maxLineNum.toString().length * 9 + 24).dp

    val density = LocalDensity.current
    val guideWidthPx = with(density) { INDENT_GUIDE_WIDTH.toPx() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!wordWrap) Modifier.horizontalScroll(hScrollState) else Modifier)
            .testTag("$testTagPrefix-body"),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
            ) {
            items(visibleLines.size) { displayIdx ->
                val vl = visibleLines[displayIdx]
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    // Gutter cell: fold indicator + line number
                    Row(
                        modifier = Modifier
                            .width(gutterWidth)
                            .fillMaxHeight()
                            .background(ReqLabColors.SurfaceContainer)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        val region = foldStartSet[vl.originalIndex]
                        if (region != null) {
                            Text(
                                text = if (vl.isFolded) ">" else "v",
                                color = ReqLabColors.OnSurfaceDim,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clickable { foldState.toggle(vl.originalIndex) }
                                    .padding(end = 2.dp),
                            )
                        }
                        Text(
                            text = "${vl.originalIndex + 1}",
                            color = ReqLabColors.OnSurfaceDim,
                            fontSize = 12.sp,
                            fontFamily = CodeFontFamily,
                            lineHeight = EditorLineHeight,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }

                    // Code line
                    val lineText = if (vl.isFolded) vl.text + FOLD_MARKER else vl.text.ifEmpty { " " }
                    val highlighted = remember(lineText, language) {
                        highlightLine(lineText, language)
                    }
                    val withFold = if (vl.isFolded) applyFoldBadge(highlighted, vl.text.length) else highlighted

                    val lineMatches = searchMatches.filter { it.lineIndex == displayIdx }
                    val globalStart = searchMatches.indexOfFirst {
                        it.lineIndex == displayIdx && it.startOffset == lineMatches.firstOrNull()?.startOffset
                    }
                    val final = if (lineMatches.isNotEmpty() && globalStart >= 0) {
                        applySearchHighlights(withFold, lineMatches, activeMatchIndex, globalStart)
                    } else {
                        withFold
                    }

                    val lineDepth = indentDepth(vl.text)

                    Text(
                        text = final,
                        fontSize = EditorFontSize,
                        fontFamily = CodeFontFamily,
                        softWrap = wordWrap,
                        lineHeight = EditorLineHeight,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .then(if (displayIdx == 0) Modifier.padding(top = 8.dp) else Modifier)
                            .then(if (displayIdx == visibleLines.lastIndex) Modifier.padding(bottom = 8.dp) else Modifier)
                            .then(
                                if (lineDepth > 0) {
                                    Modifier.drawBehind {
                                        val guideColor = INDENT_GUIDE_COLOR.copy(alpha = 0.35f)
                                        val dashEffect = PathEffect.dashPathEffect(INDENT_GUIDE_DASH, 0f)
                                        for (d in 1..lineDepth) {
                                            val x = d * guideWidthPx
                                            drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), 1f, pathEffect = dashEffect)
                                        }
                                    }
                                } else Modifier
                            ),
                    )
                }
            }
        }

            // ── Scrollbar ────────────────────────────────────────
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

// ── Fold badge ──────────────────────────────────────────────────

/**
 * Appends a tinted "⋯" badge after the fold-start line text to
 * indicate that content has been collapsed.
 */
private val FOLD_BADGE_COLOR = Color(0xFF64B5F6)  // light-blue badge for fold marker

private fun applyFoldBadge(base: AnnotatedString, foldMarkerStart: Int): AnnotatedString {
    return buildAnnotatedString {
        append(base)
        addStyle(
            SpanStyle(
                color = FOLD_BADGE_COLOR.copy(alpha = 0.7f),
                background = FOLD_BADGE_COLOR.copy(alpha = 0.08f),
            ),
            foldMarkerStart,
            (foldMarkerStart + FOLD_MARKER.length).coerceAtMost(base.length + FOLD_MARKER.length),
        )
    }
}
