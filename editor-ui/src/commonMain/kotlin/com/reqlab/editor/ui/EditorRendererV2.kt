package com.reqlab.editor.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.editor.core.InlineEditorError
import com.reqlab.editor.core.LanguageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fully virtualized code editor renderer.
 *
 * This is the generic, reusable Compose entry point for the editor engine.
 * It has no dependencies on any application-specific theme or platform APIs —
 * all customisation is provided via parameters.
 *
 * @param viewModel      State coordinator. Create with `remember { EditorViewModelV2(text, mode) }`.
 * @param isReadOnly     Disable all keyboard editing when true.
 * @param language       Language mode driving syntax highlighting.
 * @param theme          Color theme. Defaults to [EditorTheme.Dark].
 * @param wordWrap       Whether long lines wrap or scroll horizontally.
 * @param onTextChange   Called (debounced 150 ms) whenever the document changes.
 * @param onPasteRequest Called to fetch clipboard text on Ctrl/Cmd+V. Return null to skip.
 * @param onCopyRequest  Called with the selected text on Ctrl/Cmd+C. Write it to the clipboard.
 * @param testTagPrefix  Compose test-tag prefix for integration tests.
 * @param modifier       Layout modifier.
 */
@Composable
fun EditorRendererV2(
    viewModel: EditorViewModelV2,
    isReadOnly: Boolean,
    language: LanguageMode,
    theme: EditorTheme = EditorTheme.Dark,
    wordWrap: Boolean = true,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "",
    onTextChange: ((String) -> Unit)? = null,
    onPasteRequest: (() -> String?)? = null,
    onCopyRequest: ((String) -> Unit)? = null,
) {
    val state        by viewModel.state.collectAsState()
    val listState    = rememberLazyListState()
    val hScrollState = rememberScrollState()
    val scope        = rememberCoroutineScope()
    val focus        = remember { FocusRequester() }
    // Tracks the widest line seen (px) so the dummy spacer keeps hScrollState.maxValue correct.
    var hMaxContentWidthPx by remember { mutableStateOf(0) }
    val horizontalWheelState = rememberScrollableState { delta ->
        if (wordWrap) return@rememberScrollableState 0f
        hScrollState.dispatchRawDelta(-delta)
        delta
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.layoutInfo) {
        val first = listState.firstVisibleItemIndex
        val last  = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first
        viewModel.onVisibleRangeChanged(first, last)
    }

    LaunchedEffect(viewModel, onTextChange) {
        viewModel.textChangedFlow.collectLatest {
            onTextChange?.invoke(viewModel.getFullText())
        }
    }

    LaunchedEffect(Unit) {
        try { focus.requestFocus() } catch (_: Exception) { }
    }

    // Auto-scroll the LazyColumn to keep the cursor line in view,
    // but ONLY when the cursor line leaves the currently visible range.
    LaunchedEffect(state.cursorOffset) {
        val cursorDocLine = viewModel.document.lineAt(state.cursorOffset)
        val displayLine   = viewModel.displayLineMap.displayFromDoc(cursorDocLine)
        if (displayLine < 0) return@LaunchedEffect
        val visInfo  = listState.layoutInfo.visibleItemsInfo
        val firstVis = visInfo.firstOrNull()?.index ?: 0
        val lastVis  = visInfo.lastOrNull()?.index ?: 0
        if (displayLine < firstVis || displayLine > lastVis) {
            listState.animateScrollToItem(displayLine)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.background)
            .border(1.dp, theme.gutterBorder, RoundedCornerShape(8.dp))
            .semantics {
                if (!isReadOnly) {
                    insertTextAtCursor { annotatedString ->
                        viewModel.insertAtCursor(annotatedString.text)
                        true
                    }
                    setText { annotatedString ->
                        viewModel.onExternalTextChanged(annotatedString.text)
                        true
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                if (isReadOnly) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val shift = event.isShiftPressed
                val meta  = event.isMetaPressed || event.isCtrlPressed
                when {
                    event.key == Key.ShiftLeft || event.key == Key.ShiftRight ||
                        event.key == Key.CtrlLeft || event.key == Key.CtrlRight ||
                        event.key == Key.AltLeft || event.key == Key.AltRight ||
                        event.key == Key.MetaLeft || event.key == Key.MetaRight -> false
                    meta && event.key == Key.V -> {
                        scope.launch {
                            val clip = withContext(Dispatchers.Default) { onPasteRequest?.invoke() }
                                ?: return@launch
                            viewModel.insertAtCursor(clip)
                        }
                        true
                    }
                    meta && event.key == Key.A -> { viewModel.selectAll(); true }
                    meta && event.key == Key.C -> {
                        val selected = viewModel.getSelectedText()
                        if (selected.isNotEmpty()) onCopyRequest?.invoke(selected)
                        true
                    }
                    event.key == Key.DirectionLeft  -> { viewModel.moveCursorLeft(shift); true }
                    event.key == Key.DirectionRight -> { viewModel.moveCursorRight(shift); true }
                    event.key == Key.DirectionUp    -> { viewModel.moveCursorUp(shift); true }
                    event.key == Key.DirectionDown  -> { viewModel.moveCursorDown(shift); true }
                    event.key == Key.MoveHome       -> { viewModel.moveCursorToLineStart(shift); true }
                    event.key == Key.MoveEnd        -> { viewModel.moveCursorToLineEnd(shift); true }
                    event.key == Key.Backspace -> {
                        viewModel.deleteBeforeCursor()
                        true
                    }
                    event.key == Key.Delete -> {
                        viewModel.deleteForwardAtCursor()
                        true
                    }
                    event.key == Key.Enter -> {
                        viewModel.insertAtCursor("\n")
                        true
                    }
                    event.key == Key.Tab -> {
                        viewModel.insertAtCursor("  ")
                        true
                    }
                    else -> {
                        val cp = event.utf16CodePoint
                        val altOrMeta = meta || event.isCtrlPressed || event.isAltPressed
                        if (cp > 0 && !cp.toChar().isISOControl() && !altOrMeta) {
                            viewModel.insertAtCursor(cp.toChar().toString())
                            true
                        } else false
                    }
                }
            }
            .focusRequester(focus)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { focus.requestFocus() }
            }
            .scrollable(
                state = horizontalWheelState,
                orientation = Orientation.Horizontal,
                enabled = !wordWrap,
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val lineNumStyle = TextStyle(
                color      = theme.lineNumberFg,
                fontSize   = 13.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace,
                textAlign  = TextAlign.End,
            )
            val gutterWidth = remember(viewModel.document.lineCount) {
                (viewModel.document.lineCount.toString().length * 9 + 40).dp
            }
            val foldStartSet = remember(state.version, state.foldVersion) {
                viewModel.foldRegions.associate { it.startLine - 1 to it }
            }
            val contentWidthPx = (
                with(density) { maxWidth.toPx() } -
                    with(density) { gutterWidth.toPx() } -
                    with(density) { 1.dp.toPx() } -
                    with(density) { 24.dp.toPx() }
                ).toInt().coerceAtLeast(1)

            // ── Invisible spacer that keeps hScrollState.maxValue in sync ────────────────
            // horizontalScroll measures the Spacer at its natural width and sets
            // maxValue = max(0, hMaxContentWidthPx − contentViewportWidth).
            // height = 0.dp ensures no visual impact; elements are still measured.
            if (!wordWrap) {
                val contentViewportWidthDp = maxWidth - gutterWidth - 1.dp
                Box(
                    Modifier
                        .width(contentViewportWidthDp)
                        .height(0.dp)
                        .horizontalScroll(hScrollState),
                ) {
                    Spacer(
                        Modifier
                            // Clamp to Compose Constraints safe maximum (< 2^17 = 131072 px).
                            // Extremely long single-line documents can exceed this limit.
                            .width(with(density) { hMaxContentWidthPx.coerceAtMost(131_000).toDp() })
                            .height(0.dp),
                    )
                }
            }

            val gutterWidthPx = with(density) { gutterWidth.toPx() }

            // ── Single LazyColumn: each item is [gutter | divider | content] ──
            LazyColumn(
                state    = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
                    .then(
                        if (testTagPrefix.isNotEmpty()) Modifier.testTag("$testTagPrefix-line-numbers")
                        else Modifier
                    )
                    .padding(bottom = if (!wordWrap) 12.dp else 0.dp)
                    // ── Global drag handler: extends selection across line boundaries ──────
                    // Runs at PointerEventPass.Initial so it can consume vertical drag events
                    // before LazyColumn's built-in scroll handler sees them, preventing
                    // accidental scroll during text selection drag.
                    .pointerInput(gutterWidthPx) {
                        awaitEachGesture {
                            // Observe DOWN without requiring it to be unconsumed
                            // (LineViewV2.awaitFirstDown also uses requireUnconsumed=false)
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Only intercept if the press is in the text content area
                            val inContent = down.position.x > gutterWidthPx + with(density) { 2.dp.toPx() }
                            if (!inContent) return@awaitEachGesture
                            // Drag loop: fire moveCursorTo(extendSelection=true) and consume
                            // vertical movement so LazyColumn doesn't scroll.
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val ptr   = event.changes.firstOrNull() ?: break
                                if (!ptr.pressed) break
                                if (ptr.position != ptr.previousPosition) {
                                    val docOff = posToDragOffset(
                                        posX         = ptr.position.x,
                                        posY         = ptr.position.y,
                                        listState    = listState,
                                        viewModel    = viewModel,
                                        density      = density,
                                        gutterWidthPx = gutterWidthPx,
                                        hScrollValue = hScrollState.value,
                                    )
                                    viewModel.moveCursorTo(docOff, extendSelection = true)
                                    ptr.consume()
                                }
                            } while (true)
                        }
                    },
            ) {
                items(
                    count = state.totalDisplayLines,
                    key   = { dl -> "row-$dl" },
                ) { displayLine ->
                    val docLine   = viewModel.displayLineMap.docFromDisplay(displayLine)
                    val lineStart = if (docLine < viewModel.document.lineCount)
                        viewModel.document.lineStart(docLine) else 0
                    val lineEnd   = lineStart + (
                        if (docLine < viewModel.document.lineCount)
                            viewModel.document.lineText(docLine).length else 0
                    )
                    val cursorHere    = if (state.cursorOffset in lineStart..lineEnd) state.cursorOffset else -1
                    val foldRegion    = foldStartSet[docLine]
                    val isFolded      = foldRegion != null && !viewModel.displayLineMap.isVisible(docLine + 1)
                    val isFoldable    = foldRegion != null
                    val foldGuideColor = theme.indentGuide
                    val inFoldRegion  = viewModel.foldRegions.any { region ->
                        val start = region.startLine - 1
                        val end   = region.endLine   - 1
                        docLine in (start + 1)..(end - 1)
                    }

                    Row(Modifier.fillMaxWidth()) {

                        // ── Gutter ─────────────────────────────────────
                        Row(
                            modifier = Modifier
                                .width(gutterWidth)
                                .background(theme.background)
                                .padding(end = 4.dp, top = 1.dp, bottom = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            // ① Line number (right-aligned, fills available space)
                            Text(
                                text  = "${docLine + 1}",
                                style = lineNumStyle,
                            )

                            // ② Fold indicator (or guide spacer) — rightmost column
                            if (isFoldable) {
                                val foldTag = if (testTagPrefix.isNotEmpty())
                                    "$testTagPrefix-fold-indicator-$docLine" else ""
                                Box(
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(16.dp)
                                        .then(
                                            if (foldTag.isNotEmpty()) Modifier.testTag(foldTag)
                                            else Modifier
                                        )
                                        .pointerInput(docLine) {
                                            detectTapGestures {
                                                focus.requestFocus()
                                                viewModel.toggleFold(docLine)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text  = if (isFolded) "▸" else "▾",
                                        color = if (isFolded) theme.accent else theme.lineNumberFg,
                                        fontSize = 10.sp,
                                    )
                                }
                            } else if (foldStartSet.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(16.dp),
                                ) {
                                    if (inFoldRegion) {
                                        Canvas(modifier = Modifier.matchParentSize()) {
                                            val x = size.width / 2f
                                            drawLine(
                                                color = foldGuideColor,
                                                start = androidx.compose.ui.geometry.Offset(x, 0f),
                                                end   = androidx.compose.ui.geometry.Offset(x, size.height),
                                                strokeWidth = 1.5f,
                                                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Vertical divider ───────────────────────────
                        Box(Modifier.width(1.dp).fillMaxHeight().background(theme.gutterBorder))

                        // ── Line content ──────────────────────────────────────────────────
                        // One hScrollState is shared. A zero-height dummy Spacer (above the
                        // LazyColumn) sets maxValue via horizontalScroll layout each frame.
                        // Each row's content is shifted with `offset { IntOffset(-value, 0) }`
                        // and clipped by clipToBounds() to the viewport edge.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(if (!wordWrap) Modifier.clipToBounds() else Modifier),
                        ) {
                            LineViewV2(
                                docLine          = docLine,
                                document         = viewModel.document,
                                styleBuffer      = viewModel.styleBuffer,
                                styleClock       = state.styleClock,
                                version          = state.version,
                                cursorOffset     = if (!isReadOnly) cursorHere else -1,
                                selStart         = if (!isReadOnly) state.selectionStart else -1,
                                selEnd           = if (!isReadOnly) state.selectionEnd else -1,
                                diagnostics      = state.diagnostics.filter { it.line - 1 == docLine },
                                onTap            = { abs ->
                                    focus.requestFocus()
                                    viewModel.moveCursorTo(abs)
                                },
                                onDragTo         = { abs ->
                                    viewModel.moveCursorTo(abs, extendSelection = true)
                                },
                                language         = language,
                                theme            = theme,
                                wordWrap         = wordWrap,
                                containerWidthPx = if (wordWrap) contentWidthPx else 0,
                                modifier         = Modifier
                                    .then(
                                        if (wordWrap) Modifier.fillMaxWidth()
                                        else Modifier
                                            // Measure at natural text width (not viewport-constrained):
                                            .wrapContentWidth(unbounded = true)
                                            // Shift left by scroll amount — all rows move together:
                                            .offset { IntOffset(-hScrollState.value, 0) }
                                            // Track widest line so maxValue stays correct:
                                            .onGloballyPositioned { coords ->
                                                val w = coords.size.width
                                                if (w > hMaxContentWidthPx) hMaxContentWidthPx = w
                                            }
                                    )
                                    .padding(start = 8.dp, end = 16.dp, top = 1.dp, bottom = 1.dp),
                            )
                        }
                    }
                }
            }

            // ── Scrollbars (overlay) ──────────────────────────────────────
            if (!wordWrap) {
                HorizontalScrollbar(
                    adapter  = rememberScrollbarAdapter(hScrollState),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = gutterWidth + 1.dp, end = 12.dp)
                        .then(
                            if (testTagPrefix.isNotEmpty()) Modifier.testTag("$testTagPrefix-hscrollbar")
                            else Modifier
                        ),
                )
            }

            VerticalScrollbar(
                adapter  = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .then(
                        if (testTagPrefix.isNotEmpty()) Modifier.testTag("$testTagPrefix-vscrollbar")
                        else Modifier
                    ),
            )
        }
    }
}

/**
 * Maps a pointer position within the LazyColumn coordinate space to a document character offset.
 * Used by the global drag handler to extend selection across line boundaries.
 *
 * Y → displayLine via listState.layoutInfo.visibleItemsInfo
 * X → char offset via monospace character width estimate (13sp × 0.6)
 */
private fun posToDragOffset(
    posX: Float,
    posY: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: EditorViewModelV2,
    density: androidx.compose.ui.unit.Density,
    gutterWidthPx: Float,
    hScrollValue: Int,
): Int {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    // Find the item whose top offset is closest to (but ≤) the pointer y
    val item = visibleItems.lastOrNull { it.offset <= posY.toInt() }
        ?: visibleItems.firstOrNull()
        ?: return 0

    val displayLine = item.index
    val docLine     = viewModel.displayLineMap.docFromDisplay(displayLine)
    if (docLine < 0 || docLine >= viewModel.document.lineCount) return 0

    val lineStart = viewModel.document.lineStart(docLine)
    val lineText  = viewModel.document.lineText(docLine)

    // Content start = gutter + 1dp divider + 8dp start padding = gutter + 9dp
    val contentStartPx = gutterWidthPx + (9f * density.density)
    val xInContent     = posX - contentStartPx + hScrollValue
    // Monospace 13sp: typical character width ≈ 0.6 × font size in pixels (fontSize * density)
    val charWidthPx    = 13f * density.density * 0.6f
    val charOffset     = (xInContent / charWidthPx).toInt().coerceIn(0, lineText.length)

    return lineStart + charOffset
}
