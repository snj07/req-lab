package com.reqlab.editor.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.editor.core.InlineEditorError
import com.reqlab.editor.core.LanguageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// Keys that must never produce a character when pressed (no text insertion).
private val NON_CHARACTER_KEYS = setOf(
    Key.Function,
    Key.Unknown,
    Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6,
    Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12,
    Key.Escape, Key.Insert,
    Key.PrintScreen, Key.ScrollLock, Key.CapsLock, Key.NumLock,
    Key.PageUp, Key.PageDown,
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
    Key.MoveHome, Key.MoveEnd,
    Key.ShiftLeft, Key.ShiftRight,
    Key.CtrlLeft, Key.CtrlRight,
    Key.AltLeft, Key.AltRight,
    Key.MetaLeft, Key.MetaRight,
)

/**
 * Fully virtualized code editor renderer.
 *
 * This is the generic, reusable Compose entry point for the editor engine.
 * It has no dependencies on any application-specific theme or platform APIs —
 * all customisation is provided via parameters.
 *
 * @param viewModel      State coordinator. Create with `remember { EditorViewModel(text, mode) }`.
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
fun EditorRenderer(
    viewModel: EditorViewModel,
    isReadOnly: Boolean,
    language: LanguageMode,
    theme: EditorTheme = EditorTheme.Dark,
    wordWrap: Boolean = true,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "",
    onTextChange: ((String) -> Unit)? = null,
    onPasteRequest: (() -> String?)? = null,
    onCopyRequest: ((String) -> Unit)? = null,
    /** Per-line search match ranges (lineIndex -> list of start..endExclusive ranges). */
    searchMatchRangesByLine: Map<Int, List<IntRange>> = emptyMap(),
    /** Currently active search match (lineIndex to start..endExclusive range). */
    activeSearchMatch: Pair<Int, IntRange>? = null,
    /** Called whenever the horizontal scroll offset changes (no-wrap mode only). For testing. */
    onHorizontalScroll: ((Int) -> Unit)? = null,
    /** Called once after composition with the internal horizontal ScrollState. For testing. */
    onScrollStateReady: ((androidx.compose.foundation.ScrollState) -> Unit)? = null,
    /** Called when the user primary-clicks/taps in the editor content area. */
    onPrimaryTapOffset: ((Int) -> Unit)? = null,
    /**
     * Optional per-line variable colour resolver. Called with the line text and its absolute
     * start offset in the document; returns a list of (range-relative-to-line, Color) pairs
     * that are overlaid on top of the syntax highlighting so `{{token}}` spans are coloured.
     * Pass `null` (the default) to leave the editor with plain syntax colours.
     */
    lineVariableSpans: ((lineText: String, lineStartOffset: Int) -> List<Pair<IntRange, Color>>)? = null,
) {
    val state        by viewModel.state.collectAsState()
    val listState    = rememberLazyListState()
    val hScrollState = rememberScrollState()
    val scope        = rememberCoroutineScope()
    val focus        = remember { FocusRequester() }
    // Tracks the widest line seen (px) so the dummy spacer keeps hScrollState.maxValue correct.
    // Reset on every document edit (state.version) so deleted/replaced lines shrink the range.
    var hMaxContentWidthPx by remember(state.version) { mutableStateOf(0) }
    // Cache of per-displayLine TextLayoutResults fed back from LineView.
    // Used by the drag handler to map pointer coords → char offset accurately.
    val layoutResultCache = remember { mutableStateMapOf<Int, TextLayoutResult>() }
    // Context-menu state: show a DropdownMenu on secondary-button (right-click) press.
    var contextMenuVisible by remember { mutableStateOf(false) }
    var contextMenuOffset  by remember { mutableStateOf(Offset.Zero) }
    var shiftPressed by remember { mutableStateOf(false) }
    // Plain mutable object (non-state) — tracks previous doc shape to detect paste.
    // Updated inside the scroll LaunchedEffect so there is no cross-coroutine race.
    val prevDocState = remember {
        object {
            var length    = viewModel.document.length
            var lineCount = viewModel.document.lineCount
        }
    }

    // Clear the per-line layout cache whenever the document version changes.
    LaunchedEffect(state.version) {
        layoutResultCache.clear()
    }
    // Notify test / caller whenever the horizontal scroll position changes.
    LaunchedEffect(hScrollState.value) {
        if (!wordWrap) onHorizontalScroll?.invoke(hScrollState.value)
    }

    // Cursor-blink animation hoisted from LineView so exactly ONE
    // InfiniteTransition exists for the whole editor instead of one per
    // visible line.  Lines that don't hold the cursor receive cursorVisible=-1f.
    val cursorBlink = rememberInfiniteTransition(label = "cursorBlink")
    val cursorBlinkAlpha by cursorBlink.animateFloat(
        initialValue = 1f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 530
                0f at 600
                0f at 930
                1f at 1000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "cursorAlpha",
    )

    // Expose hScrollState to tests after first successful composition.
    androidx.compose.runtime.SideEffect {
        onScrollStateReady?.invoke(hScrollState)
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

    // Auto-scroll the LazyColumn to keep the cursor line in view, but suppress after
    // paste.  Keying on BOTH cursorOffset and version means this single effect handles
    // cursor navigation (only cursorOffset changes) and typed/pasted edits (both change).
    // Because the paste guard and the scroll decision live in the SAME coroutine there
    // is no possible race between a "set flag" effect and a "read flag" effect.
    LaunchedEffect(state.cursorOffset, state.version) {
        val newLen       = viewModel.document.length
        val newLineCount = viewModel.document.lineCount
        val charDelta = kotlin.math.abs(newLen - prevDocState.length)
        val lineDelta = newLineCount - prevDocState.lineCount
        prevDocState.length    = newLen
        prevDocState.lineCount = newLineCount
        // Suppress scroll for large edits OR multi-line insertions (paste).
        // Pressing Enter adds exactly 1 line (lineDelta == 1) → still scrolls.
        // Pasting any multi-line content adds ≥ 2 → scroll suppressed.
        if (charDelta >= LARGE_EDIT_SCROLL_SUPPRESS_THRESHOLD_CHARS || lineDelta >= 2) {
            return@LaunchedEffect
        }
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
                shiftPressed = event.isShiftPressed
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val shift = event.isShiftPressed
                val meta  = event.isMetaPressed || event.isCtrlPressed

                // Keep copy/select-all shortcuts working in read-only viewers.
                if (meta && event.key == Key.C) {
                    val selected = viewModel.getSelectedText()
                    if (selected.isNotEmpty()) onCopyRequest?.invoke(selected)
                    return@onPreviewKeyEvent true
                }
                if (meta && event.key == Key.A) {
                    viewModel.selectAll()
                    return@onPreviewKeyEvent true
                }

                if (isReadOnly) return@onPreviewKeyEvent false
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
                    meta && shift && event.key == Key.Z -> { viewModel.redo(); true }
                    meta && event.key == Key.Z -> { viewModel.undo(); true }
                    meta && event.key == Key.C -> {
                        val selected = viewModel.getSelectedText()
                        if (selected.isNotEmpty()) onCopyRequest?.invoke(selected)
                        true
                    }
                    meta && event.key == Key.X -> {
                        val selected = viewModel.getSelectedText()
                        if (selected.isNotEmpty()) {
                            onCopyRequest?.invoke(selected)
                            viewModel.deleteSelection()
                        } else {
                            // No selection → cut the full current line (VS Code / IntelliJ behaviour).
                            val docLine   = viewModel.document.lineAt(state.cursorOffset)
                            val lineText  = viewModel.document.lineText(docLine)
                            val lineStart = viewModel.document.lineStart(docLine)
                            val lineEnd   = lineStart + lineText.length
                            val hasTrailingNewline = lineEnd < viewModel.document.length
                            val cutText = if (hasTrailingNewline) "$lineText\n" else lineText
                            if (cutText.isNotEmpty()) {
                                onCopyRequest?.invoke(cutText)
                                // For a non-last line: delete "line\n" (lineStart..lineEnd+1).
                                // For the last line: also delete the preceding '\n' so no
                                // empty trailing line is left (lineStart-1..lineEnd).
                                val deleteStart = if (hasTrailingNewline) lineStart
                                                  else (lineStart - 1).coerceAtLeast(0)
                                val deleteEnd   = if (hasTrailingNewline) lineEnd + 1 else lineEnd
                                viewModel.moveCursorTo(deleteStart)
                                viewModel.moveCursorTo(deleteEnd, extendSelection = true)
                                viewModel.deleteSelection()
                            }
                        }
                        true
                    }
                    
                    event.isAltPressed && event.key == Key.DirectionLeft  -> { viewModel.moveCursorWordLeft(shift); true }
                    event.isAltPressed && event.key == Key.DirectionRight -> { viewModel.moveCursorWordRight(shift); true }
                    event.key == Key.DirectionLeft  -> { if (meta) viewModel.moveCursorToLineStart(shift) else viewModel.moveCursorLeft(shift); true }
                    event.key == Key.DirectionRight -> { if (meta) viewModel.moveCursorToLineEnd(shift) else viewModel.moveCursorRight(shift); true }
                    event.key == Key.DirectionUp    -> { if (meta) viewModel.moveCursorToDocStart(shift) else viewModel.moveCursorUp(shift); true }
                    event.key == Key.DirectionDown  -> { if (meta) viewModel.moveCursorToDocEnd(shift) else viewModel.moveCursorDown(shift); true }
                    event.key == Key.MoveHome       -> { viewModel.moveCursorToLineStart(shift); true }
                    event.key == Key.MoveEnd        -> { viewModel.moveCursorToLineEnd(shift); true }
                    event.key == Key.PageUp         -> { viewModel.moveCursorPageUp(extendSelection = shift); true }
                    event.key == Key.PageDown       -> { viewModel.moveCursorPageDown(extendSelection = shift); true }
                    event.key == Key.Backspace -> {
                        if (meta || event.isAltPressed) viewModel.deleteWordBeforeCursor()
                        else viewModel.deleteBeforeCursor()
                        true
                    }
                    event.key == Key.Delete -> {
                        viewModel.deleteForwardAtCursor()
                        true
                    }
                    event.key == Key.Enter -> {
                        viewModel.insertNewlineWithAutoIndent()
                        true
                    }
                    event.key == Key.Tab -> {
                        if (shift) viewModel.dedentAtCursor() else viewModel.insertAtCursor("  ")
                        true
                    }
                    else -> {
                        val cp = event.utf16CodePoint
                        val altOrMeta = meta || event.isCtrlPressed || event.isAltPressed
                        val looksLikeFnGlobeGhost =
                            cp == '?'.code && !event.isShiftPressed && event.key != Key.Slash
                        // Guard against fn / function / navigation keys whose utf16CodePoint
                        // happens to be a printable value (e.g. fn → '?' on macOS).
                        if (cp > 0 && !cp.toChar().isISOControl() && !altOrMeta &&
                            event.key !in NON_CHARACTER_KEYS && !looksLikeFnGlobeGhost) {
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
            // Detect secondary-button (right-click) press and show the context menu.
            .pointerInput("contextMenu") {
                awaitEachGesture {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.buttons.isSecondaryPressed) {
                        contextMenuOffset =
                            event.changes.firstOrNull()?.position ?: return@awaitEachGesture
                        contextMenuVisible = true
                    }
                }
            }
            // ── Horizontal scroll (no-wrap mode only) ────────────────────────────────────
            // onPointerEvent fires synchronously on every wheel/trackpad tick, delivering
            // raw deltas immediately without any velocity-accumulation warm-up phase.
            // This is the Desktop-specific fast path; the Scroll event only fires on JVM/Desktop.
            .then(
                if (!wordWrap)
                    @OptIn(ExperimentalComposeUiApi::class)
                    Modifier.onPointerEvent(PointerEventType.Scroll) { event ->
                        val dx = event.changes.sumOf { it.scrollDelta.x.toDouble() }.toFloat()
                        val dy = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                        if (abs(dx) >= abs(dy)) {
                            // Dominant horizontal: handle here, consume to stop LazyColumn
                            // from also applying the vertical component (diagonal jerk).
                            hScrollState.dispatchRawDelta(dx * 34f)
                            event.changes.forEach { it.consume() }
                        }
                        // Pure vertical scroll: not consumed — LazyColumn handles it normally.
                    }
                else Modifier
            )
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
            // Always allocate for at least 2 digits so the gutter does not shift
            // when crossing the 9→10 line boundary (single-digit to double-digit).
            val gutterDigits = maxOf(viewModel.document.lineCount.toString().length, 4)
            val gutterWidth = remember(gutterDigits) {
                (gutterDigits * 9 + 40).dp
            }
            val foldStartSet = remember(state.version, state.foldVersion) {
                viewModel.foldRegions.associate { it.startLine - 1 to it }
            }
            val contentWidthPx = (
                with(density) { maxWidth.toPx() } -
                    with(density) { gutterWidth.toPx() } -
                    with(density) { 1.dp.toPx() }   // divider
                ).toInt().coerceAtLeast(1)

            // The text in LineView has padding(start = 8.dp, end = 16.dp) applied.
            // The TextMeasurer must see the inner width (minus those pads) so that
            // soft-wrapped text fills the column without spilling into the gutter or
            // past the right edge.
            val lineTextWidthPx = (contentWidthPx -
                with(density) { 8.dp.toPx() } -
                with(density) { 16.dp.toPx() }
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
                            // (LineView.awaitFirstDown also uses requireUnconsumed=false)
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
                                // Do not treat right-click drag as text selection.
                                if (event.buttons.isSecondaryPressed &&
                                        !event.buttons.isPrimaryPressed) break
                                if (ptr.position != ptr.previousPosition) {
                                    val docOff = posToDragOffset(
                                        posX              = ptr.position.x,
                                        posY              = ptr.position.y,
                                        listState         = listState,
                                        viewModel         = viewModel,
                                        density           = density,
                                        gutterWidthPx     = gutterWidthPx,
                                        hScrollValue      = hScrollState.value,
                                        layoutResultCache = layoutResultCache,
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
                    val varSpans: List<Pair<IntRange, Color>> = if (lineVariableSpans != null) {
                        val lineTextStr = if (docLine < viewModel.document.lineCount) viewModel.document.lineText(docLine) else ""
                        lineVariableSpans.invoke(lineTextStr, lineStart)
                    } else emptyList()
                    val foldRegion    = foldStartSet[docLine]
                    val isFolded      = foldRegion != null && !viewModel.displayLineMap.isVisible(docLine + 1)
                    val isFoldable    = foldRegion != null
                    val foldGuideColor = theme.indentGuide
                    val inFoldRegion  = viewModel.foldRegions.any { region ->
                        val start = region.startLine - 1
                        val end   = region.endLine   - 1
                        docLine in (start + 1)..(end - 1)
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (!wordWrap) Modifier.clipToBounds() else Modifier) // ← ADD
                        ){

                        // ── Gutter ─────────────────────────────────────
                        Row(
                            modifier = Modifier
                                .width(gutterWidth)
                                .zIndex(1f) 
                                .background(theme.background)
                                .padding(end = 4.dp, top = 1.dp, bottom = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            // ① Line number (right-aligned, fills available space)
                            val lineNumberTag = if (testTagPrefix.isNotEmpty())
                                "$testTagPrefix-line-number-$docLine" else ""
                            Text(
                                text  = "${docLine + 1}",
                                style = lineNumStyle,
                                modifier = if (lineNumberTag.isNotEmpty()) Modifier.testTag(lineNumberTag) else Modifier,
                            )

                            // ② Fold indicator (or guide spacer) — rightmost column
                            val foldTag = if (testTagPrefix.isNotEmpty())
                                "$testTagPrefix-fold-indicator-$docLine" else ""
                            val foldColumnModifier = Modifier
                                .padding(start = 4.dp)
                                .size(16.dp)
                                .then(
                                    if (isFoldable && foldTag.isNotEmpty()) Modifier.testTag(foldTag)
                                    else Modifier
                                )
                            Box(
                                modifier = if (isFoldable) {
                                    foldColumnModifier.pointerInput(docLine) {
                                        detectTapGestures {
                                            focus.requestFocus()
                                            viewModel.toggleFold(docLine)
                                        }
                                    }
                                } else foldColumnModifier,
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isFoldable) {
                                    Text(
                                        text  = if (isFolded) "▸" else "▾",
                                        color = if (isFolded) theme.accent else theme.lineNumberFg,
                                        fontSize = 10.sp,
                                    )
                                } else if (inFoldRegion) {
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

                        // ── Vertical divider ───────────────────────────
                        Box(Modifier.width(1.dp).fillMaxHeight().background(theme.gutterBorder))

                        // ── Line content ──────────────────────────────────────────────────
                        // One hScrollState is shared. A zero-height dummy Spacer (above the
                        // LazyColumn) sets maxValue via horizontalScroll layout each frame.
                        // Each row's content is shifted with GPU translationX from hScrollState.
                        // and clipped by clipToBounds() to the viewport edge.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(if (!wordWrap) Modifier.clipToBounds() else Modifier)
                                // In no-wrap mode, LineView is only as wide as its text.
                                // Clicks in the empty area to the RIGHT of a short line
                                // don't hit LineView. This fallback handler catches
                                // unconsumed taps and snaps cursor to line end.
                                // detectTapGestures uses awaitFirstDown(requireUnconsumed=true)
                                // so it only fires when LineView didn't already handle the tap.
                                .then(
                                    if (!wordWrap && !isReadOnly)
                                        Modifier.pointerInput(lineStart, lineEnd) {
                                            detectTapGestures {
                                                focus.requestFocus()
                                                viewModel.moveCursorTo(lineEnd)
                                            }
                                        }
                                    else Modifier
                                ),
                        ) {
                            LineView(
                                docLine          = docLine,
                                document         = viewModel.document,
                                styleBuffer      = viewModel.styleBuffer,
                                styleClock       = state.styleClock,
                                version          = state.version,
                                cursorOffset     = if (!isReadOnly) cursorHere else -1,
                                selStart         = state.selectionStart,
                                selEnd           = state.selectionEnd,
                                diagnostics      = state.diagnostics.filter { it.line - 1 == docLine },
                                onTap            = { abs ->
                                    focus.requestFocus()
                                    viewModel.moveCursorTo(abs, extendSelection = shiftPressed)
                                    onPrimaryTapOffset?.invoke(abs)
                                },
                                onDragTo         = { abs ->
                                    viewModel.moveCursorTo(abs, extendSelection = true)
                                },
                                onWordSelect     = { abs ->
                                    focus.requestFocus()
                                    viewModel.selectWordAt(abs)
                                },
                                language         = language,
                                theme            = theme,
                                searchRanges     = searchMatchRangesByLine[docLine].orEmpty(),
                                activeSearchRange = activeSearchMatch
                                    ?.takeIf { it.first == docLine }
                                    ?.second,
                                wordWrap         = wordWrap,
                                containerWidthPx = if (wordWrap) lineTextWidthPx else 0,
                                // Blink alpha from the single hoisted InfiniteTransition.
                                // Only the line that holds the cursor gets the live value;
                                // all others get -1f so the Canvas skips the cursor draw.
                                cursorVisible    = if (cursorHere >= 0) cursorBlinkAlpha else -1f,
                                variableSpans    = varSpans,
                                onLayoutMeasured = { lr -> layoutResultCache[displayLine] = lr },
                                modifier         = Modifier
                                    .then(
                                        if (wordWrap) Modifier.fillMaxWidth()
                                        else Modifier
                                            .wrapContentWidth(unbounded = true, align = androidx.compose.ui.Alignment.Start)
                                            .graphicsLayer { translationX = -hScrollState.value.toFloat() }
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
            val scrollbarStyle = ScrollbarStyle(
                minimalHeight    = 48.dp,
                thickness        = 8.dp,
                shape            = RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor     = theme.foreground.copy(alpha = 0.28f),
                hoverColor       = theme.foreground.copy(alpha = 0.55f),
            )
            if (!wordWrap) {
                HorizontalScrollbar(
                    adapter  = rememberScrollbarAdapter(hScrollState),
                    style    = scrollbarStyle,
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
                style    = scrollbarStyle,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .then(
                        if (testTagPrefix.isNotEmpty()) Modifier.testTag("$testTagPrefix-vscrollbar")
                        else Modifier
                    ),
            )

            // ── Right-click context menu ────────────────────────────────────────
            // Use a zero-size Box offset to the right-click position as the anchor,
            // so DropdownMenu's offset= parameter is relative to that point rather
            // than to the BoxWithConstraints top-left corner.
            val cmDensity = LocalDensity.current
            val hasSelection = state.selectionStart >= 0 &&
                state.selectionEnd > state.selectionStart
            Box(
                Modifier.offset {
                    IntOffset(
                        contextMenuOffset.x.roundToInt(),
                        contextMenuOffset.y.roundToInt(),
                    )
                }
            ) {
            DropdownMenu(
                expanded         = contextMenuVisible,
                onDismissRequest = { contextMenuVisible = false },
                offset           = DpOffset.Zero,
            ) {
                DropdownMenuItem(
                    text    = { Text("Copy") },
                    enabled = hasSelection,
                    onClick = {
                        contextMenuVisible = false
                        val sel = viewModel.getSelectedText()
                        if (sel.isNotEmpty()) onCopyRequest?.invoke(sel)
                    },
                    modifier = if (testTagPrefix.isNotEmpty())
                        Modifier.testTag("$testTagPrefix-context-copy") else Modifier,
                )
                DropdownMenuItem(
                    text    = { Text("Cut") },
                    enabled = hasSelection && !isReadOnly,
                    onClick = {
                        contextMenuVisible = false
                        val sel = viewModel.getSelectedText()
                        if (sel.isNotEmpty()) {
                            onCopyRequest?.invoke(sel)
                            viewModel.deleteBeforeCursor()
                        }
                    },
                    modifier = if (testTagPrefix.isNotEmpty())
                        Modifier.testTag("$testTagPrefix-context-cut") else Modifier,
                )
                DropdownMenuItem(
                    text    = { Text("Paste") },
                    enabled = !isReadOnly,
                    onClick = {
                        contextMenuVisible = false
                        scope.launch {
                            val clip = withContext(Dispatchers.Default) {
                                onPasteRequest?.invoke()
                            } ?: return@launch
                            viewModel.insertAtCursor(clip)
                        }
                    },
                    modifier = if (testTagPrefix.isNotEmpty())
                        Modifier.testTag("$testTagPrefix-context-paste") else Modifier,
                )
                DropdownMenuItem(
                    text    = { Text("Select All") },
                    onClick = {
                        contextMenuVisible = false
                        viewModel.selectAll()
                    },
                    modifier = if (testTagPrefix.isNotEmpty())
                        Modifier.testTag("$testTagPrefix-context-selectall") else Modifier,
                )
            }
            } // end context-menu anchor Box
        }
    }
}

private const val LARGE_EDIT_SCROLL_SUPPRESS_THRESHOLD_CHARS = 100_000

/**
 * Maps a pointer position within the LazyColumn coordinate space to a document character offset.
 * Used by the global drag handler to extend selection across line boundaries.
 *
 * Y → displayLine via listState.layoutInfo.visibleItemsInfo
 * X,Y → char offset via TextLayoutResult.getOffsetForPosition() when a cached layout is
 *       available, falling back to a monospace character-width estimate otherwise.
 */
private fun posToDragOffset(
    posX: Float,
    posY: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: EditorViewModel,
    density: androidx.compose.ui.unit.Density,
    gutterWidthPx: Float,
    hScrollValue: Int,
    layoutResultCache: Map<Int, TextLayoutResult>,
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

    // Content text starts after: gutter + 1dp divider + 8dp start padding.
    val contentStartPx = gutterWidthPx + (9f * density.density)

    // ── Accurate path: use cached TextLayoutResult ───────────────────────────
    val lr = layoutResultCache[displayLine]
    if (lr != null) {
        val xInCanvas = (posX - contentStartPx + hScrollValue).coerceAtLeast(0f)
        // Subtract 1dp top-padding applied inside LineView.
        val yInCanvas = (posY - item.offset - (1f * density.density)).coerceAtLeast(0f)
        val charOffset = lr.getOffsetForPosition(
            androidx.compose.ui.geometry.Offset(xInCanvas, yInCanvas)
        ).coerceIn(0, lineText.length)
        return lineStart + charOffset
    }

    // ── Fallback: monospace estimate (used before first composition) ─────────
    val xInContent  = posX - contentStartPx + hScrollValue
    val charWidthPx = 13f * density.density * 0.6f
    val snapToLineEndThreshold = (lineText.length * charWidthPx) - (charWidthPx * 0.35f)
    val charOffset = if (xInContent >= snapToLineEndThreshold) {
        lineText.length
    } else {
        ((xInContent / charWidthPx) + 0.5f).toInt().coerceIn(0, lineText.length)
    }
    return lineStart + charOffset
}
