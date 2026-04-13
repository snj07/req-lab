package com.reqlab.editor.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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

@Composable
internal fun LineViewV2(
    docLine: Int,
    document: DocumentModel,
    styleBuffer: StyleBuffer,
    styleClock: Long,
    version: Int,
    cursorOffset: Int,
    selStart: Int,
    selEnd: Int,
    diagnostics: List<InlineEditorError>,
    onTap: (absoluteOffset: Int) -> Unit,
    onDragTo: ((absoluteOffset: Int) -> Unit)? = null,
    language: LanguageMode,
    theme: EditorTheme = EditorTheme.Dark,
    wordWrap: Boolean = true,
    containerWidthPx: Int = 0,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density      = LocalDensity.current
    val onSurface    = theme.foreground
    val primary      = theme.accent

    val cursorVisible: Float = if (cursorOffset >= 0) {
        val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
        infiniteTransition.animateFloat(
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
        ).value
    } else 0f

    val lineText: String = remember(version, docLine) {
        if (docLine < document.lineCount) document.lineText(docLine) else ""
    }
    val lineStartOffset: Int = remember(version, docLine) {
        if (docLine < document.lineCount) document.lineStart(docLine) else 0
    }

    val annotated: AnnotatedString = remember(lineText, styleClock) {
        buildLineAnnotatedString(
            lineText        = lineText,
            lineStartOffset = lineStartOffset,
            styleBuffer     = styleBuffer,
            language        = language,
            diagnostics     = diagnostics,
            onSurface       = onSurface,
        )
    }

    val textStyle = remember {
        TextStyle(
            fontSize   = 13.sp,
            lineHeight = 20.sp,
            fontFamily = FontFamily.Monospace,
        )
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val measured: TextLayoutResult = remember(annotated, wordWrap, containerWidthPx) {
        // Compose's packed Constraints use 18 bits per dimension: max = (1 shl 18) - 1 = 262_143 px.
        // In non-wordWrap mode, measuring an unbounded single line can easily exceed this limit
        // (50_000 chars × 7.8 px/char ≈ 390_000 px at density=1). Cap the measurement width
        // to MAX_SAFE_RENDER_WIDTH_PX so that the TextLayoutResult.size.width is always safe.
        val constraints = when {
            wordWrap && containerWidthPx > 0 -> Constraints.fixedWidth(containerWidthPx)
            !wordWrap -> Constraints(maxWidth = MAX_SAFE_RENDER_WIDTH_PX)
            else      -> Constraints()
        }
        textMeasurer.measure(
            annotated, textStyle,
            softWrap    = wordWrap,
            constraints = constraints,
        ).also { layoutResult = it }
    }

    val lineLen    = lineText.length
    val lineEndOff = lineStartOffset + lineLen
    val renderLen  = measured.layoutInput.text.length

    val hasSelection  = selStart >= 0 && selEnd > selStart
    val lineSelStart  = if (hasSelection) (selStart - lineStartOffset).coerceIn(0, renderLen) else -1
    val lineSelEnd    = if (hasSelection) (selEnd   - lineStartOffset).coerceIn(0, renderLen) else -1
    val showSelection = hasSelection && lineSelStart < lineSelEnd &&
        selEnd > lineStartOffset && selStart < lineEndOff

    val cursorInLine = cursorOffset >= lineStartOffset && cursorOffset <= lineEndOff
    val cursorCol    = if (cursorInLine)
        (cursorOffset - lineStartOffset).coerceIn(0, minOf(lineLen, renderLen))
    else -1

    val lineHeightDp = with(density) { measured.size.height.toDp() }.coerceAtLeast(20.dp)
    val lineWidthDp  = with(density) { measured.size.width.toDp() }
    val wrapping = wordWrap && containerWidthPx > 0

    Box(
        modifier = modifier
            .then(if (wrapping) Modifier.fillMaxWidth() else Modifier)
            .height(lineHeightDp)
            .pointerInput(lineStartOffset) {
                awaitEachGesture {
                    // ── DOWN: position cursor (always on press, not just confirmed tap) ──
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val lr0 = layoutResult ?: return@awaitEachGesture
                    val charOff0 = offsetInLayout(lr0, down.position)
                    onTap(lineStartOffset + charOff0)
                    down.consume()

                    // ── DRAG: extend selection as pointer moves ──
                    val lineHeightPx = with(density) { lineHeightDp.toPx() }
                    var moved = false
                    do {
                        val event = awaitPointerEvent()
                        val ptr   = event.changes.firstOrNull() ?: break
                        if (!ptr.pressed) break
                        if (ptr.position != ptr.previousPosition) {
                            moved = true
                            // Only handle intra-line drags (p.y within line bounds).
                            // Cross-line drags (y outside [0, lineHeightPx]) are handled
                            // by the global drag handler in EditorRendererV2.
                            if (ptr.position.y in 0f..lineHeightPx) {
                                val lr = layoutResult ?: break
                                val charOff = offsetInLayout(lr, ptr.position)
                                onDragTo?.invoke(lineStartOffset + charOff)
                            }
                            ptr.consume()
                        }
                    } while (true)
                }
            },
    ) {
        Canvas(
            modifier = if (wrapping) Modifier.matchParentSize()
                       // Clamp to prevent Modifier.size() from producing a Constraints width
                       // above Compose's packed safe limit of 262_143 px.
                       else Modifier.size(
                           width  = lineWidthDp.coerceAtMost(with(density) { MAX_SAFE_RENDER_WIDTH_PX.toDp() }),
                           height = lineHeightDp,
                       ),
        ) {
            if (showSelection) {
                val path = measured.getPathForRange(lineSelStart, lineSelEnd)
                drawPath(path, color = primary.copy(alpha = 0.28f))
            }

            drawText(measured, topLeft = Offset.Zero)

            if (cursorCol >= 0 && cursorVisible > 0f) {
                val cursorRect: Rect = measured.getCursorRect(cursorCol)
                drawLine(
                    color       = primary.copy(alpha = cursorVisible),
                    start       = Offset(cursorRect.left, cursorRect.top),
                    end         = Offset(cursorRect.left, cursorRect.bottom),
                    strokeWidth = 2f,
                )
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

private const val MAX_RENDER_CHARS_PER_LINE = 50_000

/**
 * Compose's Constraints representation caps each dimension at (1 shl 18) − 1 = 262_143 px.
 * Using 262_000 gives a small safety margin while maximising visible content.
 * At default density=1.0 and 13 sp monospace (~7.8 px/char) this allows ~33_600 chars
 * of visible horizontal content before the layout is clipped.
 */
private const val MAX_SAFE_RENDER_WIDTH_PX = 262_000

private fun buildLineAnnotatedString(
    lineText: String,
    lineStartOffset: Int,
    styleBuffer: StyleBuffer,
    language: LanguageMode,
    diagnostics: List<InlineEditorError>,
    onSurface: Color,
): AnnotatedString {
    if (lineText.isEmpty()) return AnnotatedString("")

    val renderText = if (lineText.length > MAX_RENDER_CHARS_PER_LINE) {
        lineText.substring(0, MAX_RENDER_CHARS_PER_LINE) + "\u2026"
    } else {
        lineText
    }
    val renderLen = renderText.length
    val styled = styleBuffer.endStyled > lineStartOffset

    return buildAnnotatedString {
        if (!styled) {
            withStyle(SpanStyle(color = onSurface)) { append(renderText) }
        } else {
            var pos = 0
            while (pos < renderLen) {
                val absPos  = lineStartOffset + pos
                val style   = styleBuffer.styleAt(absPos)
                val absNext = styleBuffer.nextStyleChangeAfter(absPos, lineStartOffset + renderLen)
                val nextPos = (absNext - lineStartOffset).coerceIn(pos + 1, renderLen)
                withStyle(SpanStyle(color = colorForToken(style, language))) {
                    append(renderText, pos, nextPos)
                }
                pos = nextPos
            }
        }
        for (err in diagnostics) {
            val colStart = (err.col - 1).coerceAtLeast(0)
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
    }
}
