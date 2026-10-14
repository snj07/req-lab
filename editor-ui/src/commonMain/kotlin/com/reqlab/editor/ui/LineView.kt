package com.reqlab.editor.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import com.reqlab.editor.core.DocumentModel
import com.reqlab.editor.core.InlineEditorError
import com.reqlab.editor.core.InlineErrorSeverity
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.StyleBuffer
import com.reqlab.editor.core.TokenType
import androidx.compose.foundation.gestures.waitForUpOrCancellation


@Composable
internal fun LineView(
    docLine: Int,
    document: DocumentModel,
    styleBuffer: StyleBuffer,
    styleClock: Long,
    version: Int,
    cursorOffset: Int,
    selStart: Int,
    selEnd: Int,
    diagnostics: List<InlineEditorError>,
    onTap: (absoluteOffset: Int, extendSelection: Boolean, trailingWhitespace: Boolean) -> Unit,
    onDragTo: ((absoluteOffset: Int) -> Unit)? = null,
    // onWordSelect = { abs ->
    //     viewModel.selectWordAt(abs)
    // },
    onWordSelect: ((absoluteOffset: Int) -> Unit)? = null,
    language: LanguageMode,
    theme: EditorTheme = EditorTheme.Dark,
    searchRanges: List<IntRange> = emptyList(),
    activeSearchRange: IntRange? = null,
    wordWrap: Boolean = true,
    containerWidthPx: Int = 0,
    horizontalScrollPx: Float = 0f,
    horizontalViewportWidthPx: Int = 0,
    monospaceCharWidthPx: Float = 8f,
    /**
     * Pre-computed cursor-blink alpha (0f..1f) from the hoisted InfiniteTransition
     * in EditorRenderer. -1f means "no cursor on this line" (draws nothing).
     */
    cursorVisible: Float = -1f,
    /** Called after every (re-)measure with the new TextLayoutResult. Used by
     *  the drag-selection handler to map pointer coordinates to character offsets
     *  without relying on a hardcoded character-width estimate. */
    onLayoutMeasured: ((androidx.compose.ui.text.TextLayoutResult, Int) -> Unit)? = null,
    /**
     * Pre-computed per-line variable color spans (range relative to line start → Color).
     * Applied on top of the syntax highlighting so `{{token}}` text is always visually distinct.
     */
    variableSpans: List<Pair<IntRange, Color>> = emptyList(),
    /** Matching bracket pair as absolute document offsets, or null. */
    bracketPair: Pair<Int, Int>? = null,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density      = LocalDensity.current
    val primary      = theme.accent

    // cursorVisible is provided by the caller (hoisted to EditorRenderer).
    // A value of -1f means no cursor on this line; treat as fully transparent.
    val effectiveCursorAlpha = cursorVisible.coerceAtLeast(0f)

    val lineText: String = remember(version, docLine, document) {
        if (docLine < document.lineCount) document.lineText(docLine) else ""
    }
    val lineStartOffset: Int = remember(version, docLine, document) {
        if (docLine < document.lineCount) document.lineStart(docLine) else 0
    }
    val horizontalWindow = remember(
        lineText,
        wordWrap,
        horizontalScrollPx,
        horizontalViewportWidthPx,
        monospaceCharWidthPx,
        cursorOffset,
        activeSearchRange,
    ) {
        if (wordWrap) {
            val anchor = activeSearchRange?.first
                ?: (cursorOffset - lineStartOffset).takeIf { it in 0..lineText.length }
                ?: 0
            wrappedLineWindow(lineText.length, anchor)
        } else {
            horizontalLineWindow(
                lineLength = lineText.length,
                scrollPx = horizontalScrollPx,
                viewportWidthPx = horizontalViewportWidthPx,
                charWidthPx = monospaceCharWidthPx,
            )
        }
    }

    val annotated: AnnotatedString = remember(
        lineText,
        styleClock,
        searchRanges,
        activeSearchRange,
        diagnostics,
        variableSpans,
        horizontalWindow,
        theme,
    ) {
        buildLineAnnotatedString(
            lineText        = lineText,
            lineStartOffset = lineStartOffset,
            styleBuffer     = styleBuffer,
            theme           = theme,
            diagnostics     = diagnostics,
            searchRanges    = searchRanges,
            activeSearchRange = activeSearchRange,
            variableSpans   = variableSpans,
            renderStartColumn = horizontalWindow.startColumn,
            renderEndColumn = horizontalWindow.endColumn,
        )
    }

    val textStyle = remember { editorTextStyle() }

    val measured: TextLayoutResult = remember(annotated, wordWrap, containerWidthPx) {
        // Compose's packed Constraints use 18 bits per dimension: max = (1 shl 18) - 1 = 262_143 px.
        // In non-wordWrap mode, measuring an unbounded single line can easily exceed this limit.
        // The visible window is small, but retain a defensive cap for every platform density.
        val constraints = when {
            wordWrap && containerWidthPx > 0 -> Constraints.fixedWidth(containerWidthPx)
            !wordWrap -> Constraints(maxWidth = MAX_SAFE_RENDER_WIDTH_PX)
            else      -> Constraints()
        }
        textMeasurer.measure(
            annotated, textStyle,
            softWrap    = wordWrap,
            constraints = constraints,
        )
    }

    // Notify caller whenever the layout is (re-)computed so the drag handler
    // in EditorRenderer can use getOffsetForPosition() instead of a
    // hardcoded character-width estimate.
    LaunchedEffect(measured) {
        onLayoutMeasured?.invoke(measured, horizontalWindow.startColumn)
    }

    val measuredLatest = rememberUpdatedState(measured)

    val lineLen    = lineText.length
    val lineEndOff = lineStartOffset + lineLen
    val renderLen  = measured.layoutInput.text.length

    val hasSelection  = selStart >= 0 && selEnd > selStart
    val lineSelStart  = if (hasSelection) {
        (selStart - lineStartOffset - horizontalWindow.startColumn).coerceIn(0, renderLen)
    } else -1
    val lineSelEnd = if (hasSelection) {
        (selEnd - lineStartOffset - horizontalWindow.startColumn).coerceIn(0, renderLen)
    } else -1
    val showSelection = hasSelection && lineSelStart < lineSelEnd &&
        selEnd > lineStartOffset && selStart < lineEndOff

    val cursorInLine = cursorOffset >= lineStartOffset && cursorOffset <= lineEndOff
    val cursorCol    = if (cursorInLine)
        (cursorOffset - lineStartOffset - horizontalWindow.startColumn).coerceIn(0, renderLen)
    else -1

    val lineHeightDp = with(density) { measured.size.height.toDp() }.coerceAtLeast(20.dp)
    val lineWidthDp = with(density) {
        if (!wordWrap && horizontalViewportWidthPx > 0) horizontalViewportWidthPx.toDp()
        else measured.size.width.toDp()
    }
    val wrapping = wordWrap && containerWidthPx > 0
    val padStartPx = with(density) { LINE_PAD_START.toPx() }
    val padTopPx = with(density) { LINE_PAD_VERT.toPx() }

    Box(
        modifier = modifier
            .then(if (wrapping) Modifier.fillMaxWidth() else Modifier)
            .semantics { contentDescription = lineText }
            .pointerInput(
                lineStartOffset,
                version,
                document,
                wordWrap,
                containerWidthPx,
                horizontalWindow,
                horizontalScrollPx,
            ) {
                fun toLayout(p: Offset) = Offset(
                    (p.x - padStartPx - horizontalWindow.drawOffsetPx).coerceAtLeast(0f),
                    (p.y - padTopPx).coerceAtLeast(0f),
                )
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val layout = measuredLatest.value
                    val charOff0 = if (down.position.x < padStartPx) 0
                        else offsetInLayout(layout, toLayout(down.position))
                    val absOff0 = lineStartOffset + horizontalWindow.startColumn + charOff0
                    val layoutPosition = toLayout(down.position)
                    val visualLine = layout.getLineForVerticalPosition(layoutPosition.y)
                    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
                    val isTrailingWhitespace = if (wordWrap) {
                        layoutPosition.x > layout.getCursorRect(lineEnd).left
                    } else {
                        down.position.x - padStartPx + horizontalScrollPx >=
                            lineText.length * monospaceCharWidthPx
                    }

                    // Immediately place cursor on first press — no delay.
                    // If a double-click follows, onWordSelect will override this placement.
                    val shiftHeld = currentEvent.keyboardModifiers.isShiftPressed
                    onTap(absOff0, shiftHeld, isTrailingWhitespace)
                    down.consume()

                    val lineHeightPx = with(density) { lineHeightDp.toPx() } + padTopPx * 2f
                    // Drain pointer events until release, handling drag selection
                    // with zero startup delay (drag starts on the very first move event).
                    var released = false
                    var dragged  = false
                    do {
                        val event = awaitPointerEvent()
                        val ptr   = event.changes.firstOrNull() ?: break
                        if (!ptr.pressed) { released = true; break }
                        if (ptr.position != ptr.previousPosition) {
                            dragged = true
                            if (ptr.position.y in 0f..lineHeightPx) {
                                val charOff = if (ptr.position.x < padStartPx) 0
                                    else offsetInLayout(measuredLatest.value, toLayout(ptr.position))
                                onDragTo?.invoke(
                                    lineStartOffset + horizontalWindow.startColumn + charOff,
                                )
                            }
                            ptr.consume()
                        }
                    } while (true)

                    // After a clean tap (no drag), check for double-click within 300 ms.
                    // A second press triggers word selection, overriding the initial cursor.
                    if (released && !dragged && onWordSelect != null) {
                        val secondDown = withTimeoutOrNull(300L) {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                        if (secondDown != null) {
                            onWordSelect.invoke(absOff0)
                            secondDown.consume()
                        }
                    }
                }
            }
            .padding(start = LINE_PAD_START, end = LINE_PAD_END, top = LINE_PAD_VERT, bottom = LINE_PAD_VERT)
            .height(lineHeightDp),
    ) {
        Canvas(
            modifier = if (wrapping) Modifier.matchParentSize()
                       // Clamp to prevent Modifier.size() from producing a Constraints width
                       // above Compose's packed safe limit of 262_143 px.
                       else Modifier.size(width = lineWidthDp, height = lineHeightDp),
        ) {
            translate(left = horizontalWindow.drawOffsetPx) {
                if (showSelection) {
                    val path = measured.getPathForRange(lineSelStart, lineSelEnd)
                    drawPath(path, color = primary.copy(alpha = 0.28f))
                }

                val guides = indentGuideColumns(lineText)
                if (guides.isNotEmpty() && renderLen > 0) {
                    val h = size.height
                    for (col in guides) {
                        val localCol = col - horizontalWindow.startColumn
                        if (localCol !in 0..renderLen) continue
                        val x = measured.getHorizontalPosition(localCol, true)
                        drawLine(
                            color = theme.indentGuide,
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f,
                        )
                    }
                }

                if (bracketPair != null) {
                    for (abs in listOf(bracketPair.first, bracketPair.second)) {
                        val col = abs - lineStartOffset - horizontalWindow.startColumn
                        if (col in 0 until renderLen) {
                            val box = measured.getBoundingBox(col)
                            drawRect(
                                color = theme.bracketMatchBackground,
                                topLeft = Offset(box.left, box.top),
                                size = androidx.compose.ui.geometry.Size(box.width.coerceAtLeast(1f), box.height),
                            )
                            drawRect(
                                color = theme.bracketMatchBorder,
                                topLeft = Offset(box.left + 0.5f, box.top + 0.5f),
                                size = androidx.compose.ui.geometry.Size(
                                    (box.width - 1f).coerceAtLeast(1f),
                                    (box.height - 1f).coerceAtLeast(1f),
                                ),
                                style = Stroke(width = 1f),
                            )
                        }
                    }
                }

                drawText(measured, topLeft = Offset.Zero)

                if (cursorCol >= 0 && effectiveCursorAlpha > 0f) {
                    val cursorRect: Rect = measured.getCursorRect(cursorCol)
                    drawLine(
                        color       = primary.copy(alpha = effectiveCursorAlpha),
                        start       = Offset(cursorRect.left, cursorRect.top),
                        end         = Offset(cursorRect.left, cursorRect.bottom),
                        strokeWidth = 2f,
                    )
                }
            }
        }
    }
}

/** Returns the text offset within [lr] that corresponds to [position]. */
private fun offsetInLayout(lr: TextLayoutResult, position: androidx.compose.ui.geometry.Offset): Int {
    val textLen = lr.layoutInput.text.length
    if (textLen <= 0) return 0

    val clickedLine = lr.getLineForVerticalPosition(position.y)
    val lastVisualLine = (lr.lineCount - 1).coerceAtLeast(0)

    // End-of-line snapping should only apply on the LAST wrapped visual line.
    // On earlier wrapped rows, x can legitimately be > endX(last line), and those
    // clicks must still place the cursor within that wrapped row.
    if (clickedLine == lastVisualLine) {
        val endX = lr.getCursorRect(textLen).left
        // Clicks beyond rendered text on last visual line are always end-of-line.
        if (position.x >= endX) return textLen

        // For UX parity with common editors, clicking in the right half of the final
        // glyph also snaps to end-of-line (cursor after the last character).
        val lastGlyphStartX = lr.getCursorRect(textLen - 1).left
        val lastGlyphMidX = lastGlyphStartX + ((endX - lastGlyphStartX) * 0.5f)
        if (position.x >= lastGlyphMidX) return textLen
    }

    return lr.getOffsetForPosition(position).coerceIn(0, textLen)
}

internal val LINE_PAD_START = 8.dp
internal val LINE_PAD_END = 16.dp
internal val LINE_PAD_VERT = 1.dp
internal val EditorFontSize = 13.sp
internal val EditorLineHeight = 20.sp

/** Shared by LineView, gutter numbers, and fold marks so one row shares one line box. */
internal fun editorTextStyle(
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
): TextStyle = TextStyle(
    color = color,
    fontSize = EditorFontSize,
    lineHeight = EditorLineHeight,
    fontFamily = FontFamily.Monospace,
    textAlign = textAlign,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/**
 * Compose's Constraints representation caps each dimension at (1 shl 18) − 1 = 262_143 px.
 * Using 262_000 gives a small safety margin while maximising visible content.
 * At default density=1.0 and 13 sp monospace (~7.8 px/char) this allows ~33_600 chars
 * of visible horizontal content before the layout is clipped.
 */
private const val MAX_SAFE_RENDER_WIDTH_PX = 262_000

internal data class HorizontalLineWindow(
    val startColumn: Int,
    val endColumn: Int,
    val drawOffsetPx: Float,
)

internal fun horizontalLineWindow(
    lineLength: Int,
    scrollPx: Float,
    viewportWidthPx: Int,
    charWidthPx: Float,
    overscanColumns: Int = 64,
): HorizontalLineWindow {
    if (lineLength <= 0) return HorizontalLineWindow(0, 0, 0f)
    val width = charWidthPx.coerceAtLeast(1f)
    val viewport = viewportWidthPx.coerceAtLeast(1)
    val firstVisible = (scrollPx.coerceAtLeast(0f) / width).toInt().coerceIn(0, lineLength)
    val visibleCount = kotlin.math.ceil(viewport / width).toInt().coerceAtLeast(1)
    val start = (firstVisible - overscanColumns).coerceAtLeast(0)
    val end = (firstVisible + visibleCount + overscanColumns).coerceAtMost(lineLength)
    return HorizontalLineWindow(
        startColumn = start,
        endColumn = end,
        drawOffsetPx = start * width - scrollPx,
    )
}

internal fun wrappedLineWindow(
    lineLength: Int,
    anchorColumn: Int,
    maxWindowChars: Int = MAX_WRAPPED_WINDOW_CHARS,
): HorizontalLineWindow {
    if (lineLength <= maxWindowChars) return HorizontalLineWindow(0, lineLength, 0f)
    val anchor = anchorColumn.coerceIn(0, lineLength)
    val start = (anchor - maxWindowChars / 2).coerceIn(0, lineLength - maxWindowChars)
    return HorizontalLineWindow(start, start + maxWindowChars, 0f)
}

private const val MAX_WRAPPED_WINDOW_CHARS = 128_000

private fun buildLineAnnotatedString(
    lineText: String,
    lineStartOffset: Int,
    styleBuffer: StyleBuffer,
    theme: EditorTheme,
    diagnostics: List<InlineEditorError>,
    searchRanges: List<IntRange>,
    activeSearchRange: IntRange?,
    variableSpans: List<Pair<IntRange, Color>> = emptyList(),
    renderStartColumn: Int = 0,
    renderEndColumn: Int = lineText.length,
): AnnotatedString {
    if (lineText.isEmpty()) return AnnotatedString("")

    val sourceStart = renderStartColumn.coerceIn(0, lineText.length)
    val sourceEnd = renderEndColumn.coerceIn(sourceStart, lineText.length)
    val renderText = lineText.substring(sourceStart, sourceEnd)
    val renderLen = renderText.length
    val renderAbsoluteStart = lineStartOffset + sourceStart
    val styled = styleBuffer.endStyled > renderAbsoluteStart

    return buildAnnotatedString {
        if (!styled) {
            withStyle(SpanStyle(color = theme.foreground)) { append(renderText) }
        } else {
            var pos = 0
            while (pos < renderLen) {
                val absPos  = renderAbsoluteStart + pos
                val style   = styleBuffer.styleAt(absPos)
                val absNext = styleBuffer.nextStyleChangeAfter(
                    absPos,
                    renderAbsoluteStart + renderLen,
                )
                val nextPos = (absNext - renderAbsoluteStart).coerceIn(pos + 1, renderLen)
                withStyle(theme.spanStyleFor(style)) {
                    append(renderText, pos, nextPos)
                }
                pos = nextPos
            }
        }
        // ── Variable token colour overlay (overrides syntax colours) ──────────
        for ((range, color) in variableSpans) {
            val spanStart = (range.first - sourceStart).coerceIn(0, renderLen)
            val spanEnd = (range.last + 1 - sourceStart).coerceIn(0, renderLen)
            if (spanStart < spanEnd) {
                addStyle(SpanStyle(color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, background = color.copy(alpha = 0.13f)), spanStart, spanEnd)
            }
        }

        for (err in diagnostics) {
            val colStart = (err.col - 1 - sourceStart).coerceAtLeast(0)
            val spanStart = colStart.coerceAtMost(renderLen)
            val spanEnd   = renderLen.coerceAtLeast(spanStart + 1).coerceAtMost(renderLen)
            if (spanStart < spanEnd) {
                val color = if (err.severity == InlineErrorSeverity.ERROR) {
                    Color(0xFFFF6B6B)
                } else {
                    Color(0xFFFFBB44)
                }
                addStyle(
                    SpanStyle(
                        color = color,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    ),
                    spanStart,
                    spanEnd,
                )
            }
        }

        for (range in searchRanges) {
            val spanStart = (range.first - sourceStart).coerceIn(0, renderLen)
            val spanEnd = (range.last + 1 - sourceStart).coerceIn(0, renderLen)
            if (spanStart < spanEnd) {
                addStyle(
                    SpanStyle(background = SyntaxColors.searchMatch),
                    spanStart,
                    spanEnd,
                )
            }
        }

        activeSearchRange?.let { range ->
            val spanStart = (range.first - sourceStart).coerceIn(0, renderLen)
            val spanEnd = (range.last + 1 - sourceStart).coerceIn(0, renderLen)
            if (spanStart < spanEnd) {
                addStyle(
                    SpanStyle(background = SyntaxColors.searchActive, color = Color.White),
                    spanStart,
                    spanEnd,
                )
            }
        }
    }
}
