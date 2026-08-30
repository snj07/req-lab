package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorRenderer
import com.reqlab.editor.ui.EditorTheme
import com.reqlab.editor.ui.EditorViewModel
import com.reqlab.editor.ui.SyntaxHighlighterRegistry
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.platform.copyToClipboard as platformCopyToClipboard
import com.reqlab.ui.shared.platform.readFromClipboard
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.LocalAppColors
import com.reqlab.ui.shared.theme.ReqLabColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pretty-print of read-only bodies larger than this is offloaded off the composition thread. */
internal const val READ_ONLY_FORMAT_OFFLOAD_CHARS = 64_000

internal fun shouldOffloadReadOnlyFormat(length: Int): Boolean = length > READ_ONLY_FORMAT_OFFLOAD_CHARS

// ── Theme Helper ─────────────────────────────────────────────────

@Composable
private fun editorTheme(): EditorTheme {
    val p = LocalAppColors.current
    val isDark = p.background.red < 0.5f
    return EditorTheme(
        background       = p.surface,
        foreground       = p.onSurface,
        lineNumberFg     = p.onSurfaceDim,
        lineNumberBg     = p.surface,
        gutterBorder     = p.border,
        selectionBg      = p.selectedItem,
        cursorLine       = p.surfaceVariant,
        foldIndicator    = p.onSurfaceDim,
        indentGuide      = p.borderLight,
        errorUnderline   = p.error,
        warningUnderline = p.tertiary,
        accent           = p.primary,
        tokenColors      = if (isDark) EditorTheme.Dark.tokenColors else EditorTheme.Light.tokenColors,
    )
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
    testTagPrefix: String = "code-editor",
    onCursorTap: ((Int) -> Unit)? = null,
    lineVariableSpans: ((lineText: String, lineStartOffset: Int) -> List<Pair<IntRange, androidx.compose.ui.graphics.Color>>)? = null,
    allowJson5: Boolean = false,
    /**
     * An already-created [EditorViewModel] to use instead of creating a new one.
     * When provided, [CodeEditor] does NOT dispose it on removal — the caller
     * (e.g. [BodyEditor] via [RequestTabState.getOrCreateBodyViewModel]) owns
     * the lifetime and disposes it when the tab is closed.
     *
     * Leave `null` (default) for all read-only viewers and script editors that
     * do not need persistent undo history across re-compositions.
     */
    externalViewModel: EditorViewModel? = null,
) {
    val isReadOnly = onTextChange == null

    // ── ViewModel (manages all document state) ─────────────────────
    val languageMode = language.toLanguageMode()
    // When an external VM is provided we skip creating an internal one entirely.
    // remember() key includes `hasExternal` so that switching from internal to
    // external (or vice-versa) correctly disposes the old internal VM.
    val hasExternal = externalViewModel != null
    val internalViewModel: EditorViewModel? = remember(languageMode, hasExternal) {
        if (hasExternal) null
        else {
            if (!SyntaxHighlighterRegistry.hasHighlighter(LanguageMode.PLAIN_TEXT)) {
                SyntaxHighlighterRegistry.registerBuiltinHighlighters()
            }
            EditorViewModel(initialText = text, languageMode = languageMode)
        }
    }
    // Only dispose the VM we created; external VMs are owned by RequestTabState.
    DisposableEffect(internalViewModel) { onDispose { internalViewModel?.dispose() } }
    val viewModel: EditorViewModel = externalViewModel ?: internalViewModel!!

    // ── Format / display state ───────────────────────────────
    var isFormatted by remember { mutableStateOf(isReadOnly) }
    var offloadedFormatted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text, isFormatted, language, allowJson5, isReadOnly) {
        if (!isReadOnly || !isFormatted || !shouldOffloadReadOnlyFormat(text.length)) {
            offloadedFormatted = null
            return@LaunchedEffect
        }
        offloadedFormatted = withContext(Dispatchers.Default) {
            autoFormat(text, language, allowJson5)
        }
    }
    val displayText = remember(text, isFormatted, language, allowJson5, offloadedFormatted, isReadOnly) {
        when {
            !isReadOnly || !isFormatted -> text
            shouldOffloadReadOnlyFormat(text.length) -> offloadedFormatted ?: text
            else -> autoFormat(text, language, allowJson5)
        }
    }
    // Keep doc in sync with external text (or formatted text for read-only),
    // but skip no-op updates. Re-applying identical text after paste can
    // trigger an extra cursor/layout cycle and visually shift the gutter.
    LaunchedEffect(displayText) {
        if (viewModel.getFullText() != displayText) {
            viewModel.onExternalTextChanged(displayText)
        }
    }

    // ── Toolbar state ────────────────────────────────────────
    var wordWrap by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableIntStateOf(0) }
    val editorFocus = remember { FocusRequester() }
    // Bump a tick from click handlers; requestFocus in LaunchedEffect so it is
    // not nested inside IconButton/performClick (deadlocks desktop tests).
    var restoreFocusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(restoreFocusTick) {
        if (restoreFocusTick == 0) return@LaunchedEffect
        try { editorFocus.requestFocus() } catch (_: Exception) { }
    }
    val restoreEditorFocus: () -> Unit = { restoreFocusTick++ }

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
    val searchMatchRangesByLine = remember(searchMatches) {
        searchMatches
            .groupBy { it.lineIndex }
            .mapValues { (_, matchesForLine) ->
                matchesForLine.map { it.startOffset until it.endOffset }
            }
    }
    val activeSearchMatch = remember(searchMatches, activeMatchIndex) {
        searchMatches.getOrNull(activeMatchIndex)?.let { it.lineIndex to (it.startOffset until it.endOffset) }
    }

    val toggleSearch: () -> Unit = {
        showSearch = !showSearch
        if (!showSearch) {
            searchQuery = ""
            restoreEditorFocus()
        }
    }
    LaunchedEffect(searchMatches.size) {
        activeMatchIndex = if (searchMatches.isNotEmpty())
            activeMatchIndex.coerceIn(0, searchMatches.size - 1) else 0
    }

    // Scroll the editor to the active search match so the LazyColumn in
    // EditorRenderer follows the cursor to the correct line.
    LaunchedEffect(activeMatchIndex, searchMatches) {
        val match = searchMatches.getOrNull(activeMatchIndex) ?: return@LaunchedEffect
        val lineStart = viewModel.document.lineStart(
            match.lineIndex.coerceIn(0, viewModel.document.lineCount - 1)
        )
        viewModel.moveCursorTo(lineStart + match.startOffset)
    }

    // ── Layout ───────────────────────────────────────────────
    Column(
        modifier = modifier
            .testTag(testTagPrefix)
            .onPreviewKeyEvent { event ->
                if (!enableSearch) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isMeta = event.isMetaPressed || event.isCtrlPressed
                if (isMeta && event.key == Key.F) {
                    toggleSearch()
                    true
                } else {
                    false
                }
            },
    ) {

        // Toolbar
        if (showToolbar) {
            CodeEditorToolbar(
                language = language,
                isReadOnly = isReadOnly,
                wordWrap = wordWrap,
                onToggleWordWrap = if (enableWordWrap) {
                    {
                        wordWrap = !wordWrap
                        restoreEditorFocus()
                    }
                } else null,
                isFormatted = isFormatted,
                onToggleFormat = if (enableFormat) {
                    {
                        if (isReadOnly) {
                            isFormatted = !isFormatted
                        } else {
                            val current = viewModel.getFullText()
                            val formatted = autoFormat(current, language, allowJson5)
                            if (formatted != current) viewModel.replaceDocument(formatted)
                        }
                        restoreEditorFocus()
                    }
                } else null,
                showSearch = showSearch,
                onToggleSearch = if (enableSearch) {
                    toggleSearch
                } else null,
                onCopy = if (enableCopy) {
                    {
                        platformCopyToClipboard(viewModel.getFullText())
                        restoreEditorFocus()
                    }
                } else null,
                onDownload = if (enableDownload) {
                    {
                        onDownload?.invoke()
                        restoreEditorFocus()
                    }
                } else null,
                hasFoldRegions = toolbarFoldRegions.isNotEmpty(),
                onFoldAll   = if (enableFolding && toolbarFoldRegions.isNotEmpty()) {
                    { viewModel.foldAll(); restoreEditorFocus() }
                } else null,
                onUnfoldAll = if (enableFolding && toolbarFoldRegions.isNotEmpty()) {
                    { viewModel.unfoldAll(); restoreEditorFocus() }
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
                onClose = { showSearch = false; searchQuery = ""; restoreEditorFocus() },
                testTagPrefix = testTagPrefix,
            )
        }

        // ── Content (GapBuffer + LazyColumn) ──
        EditorRenderer(
            viewModel      = viewModel,
            isReadOnly     = isReadOnly,
            language       = language.toLanguageMode(),
            theme          = editorTheme(),
            wordWrap       = wordWrap,
            testTagPrefix  = testTagPrefix,
            modifier       = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("$testTagPrefix-input"),
            onTextChange   = onTextChange,
            onPasteRequest = { readFromClipboard() },
            onCopyRequest  = { text -> platformCopyToClipboard(text) },
            searchMatchRangesByLine = searchMatchRangesByLine,
            activeSearchMatch = activeSearchMatch,
            onPrimaryTapOffset = onCursorTap,
            lineVariableSpans = lineVariableSpans,
            focusRequester = editorFocus,
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
            .focusProperties { canFocus = false }
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
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }

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
                .focusRequester(searchFocusRequester)
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

        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(24.dp).focusProperties { canFocus = false },
        ) {
            Icon(Icons.Default.ArrowUpward, "Previous match", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
        IconButton(
            onClick = onNext,
            modifier = Modifier.size(24.dp).focusProperties { canFocus = false },
        ) {
            Icon(Icons.Default.ArrowDownward, "Next match", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(24.dp)
                .focusProperties { canFocus = false }
                .testTag("$testTagPrefix-search-close"),
        ) {
            Icon(Icons.Default.Close, "Close search", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
}
