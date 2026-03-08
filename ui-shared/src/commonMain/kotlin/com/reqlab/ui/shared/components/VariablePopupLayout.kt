package com.reqlab.ui.shared.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

private const val MIN_POPUP_MARGIN_PX = 8

/**
 * Minimum number of pixels of the card/popup that must remain visible
 * inside the viewport when dragging.  This prevents the old behaviour where
 * the clamp locked horizontal (or vertical) movement to zero whenever the
 * card was as wide (or tall) as the viewport — which happens on Desktop
 * where `Dialog` sizes its window to fit the content.
 */
private const val MIN_VISIBLE_PX = 100

fun clampPopupOffsetToViewport(
    candidate: IntOffset,
    popupSize: IntSize,
    viewportSize: IntSize,
    marginPx: Int = MIN_POPUP_MARGIN_PX,
): IntOffset {
    // Skip clamping when sizes have not been measured yet.
    if (popupSize == IntSize.Zero || viewportSize == IntSize.Zero) return candidate

    val pw = popupSize.width
    val ph = popupSize.height
    val vw = viewportSize.width
    val vh = viewportSize.height
    val vis = MIN_VISIBLE_PX

    // Card left-edge can go as far left as (margin + vis - cardWidth),
    // meaning only `vis` pixels of the right edge remain visible.
    // Card left-edge can go as far right as (viewportWidth - margin - vis),
    // meaning only `vis` pixels of the left edge remain visible.
    val minX = marginPx + vis - pw
    val maxX = vw - marginPx - vis
    val minY = marginPx + vis - ph
    val maxY = vh - marginPx - vis

    return IntOffset(
        x = candidate.x.coerceIn(minX, maxX.coerceAtLeast(minX)),
        y = candidate.y.coerceIn(minY, maxY.coerceAtLeast(minY)),
    )
}

fun applyPopupDragDelta(
    currentOffset: IntOffset,
    dragDx: Float,
    dragDy: Float,
    popupSize: IntSize,
    viewportSize: IntSize,
): IntOffset {
    val candidate = IntOffset(
        x = currentOffset.x + dragDx.toInt(),
        y = currentOffset.y + dragDy.toInt(),
    )
    return clampPopupOffsetToViewport(
        candidate = candidate,
        popupSize = popupSize,
        viewportSize = viewportSize,
    )
}

/**
 * Clamps a center-relative dialog drag offset so the dialog card always stays
 * within the viewport.
 *
 * The dialog is assumed to be placed with `contentAlignment = Alignment.Center`
 * inside a full-screen Box, and [offsetX]/[offsetY] represent the *delta* from
 * that centred baseline position.  Positive X moves right, positive Y moves down.
 *
 * Returns the clamped (offsetX, offsetY) pair as Floats so the caller can
 * accumulate without losing sub-pixel precision.
 */
fun clampDialogOffsetFromCenter(
    offsetX: Float,
    offsetY: Float,
    cardSize: IntSize,
    viewportSize: IntSize,
    marginPx: Int = MIN_POPUP_MARGIN_PX,
): Pair<Float, Float> {
    if (viewportSize.width == 0 || cardSize.width == 0) return Pair(offsetX, offsetY)

    val vis = MIN_VISIBLE_PX.toFloat()
    val m = marginPx.toFloat()

    // Center-relative to absolute
    val centerX = (viewportSize.width - cardSize.width) / 2f
    val centerY = (viewportSize.height - cardSize.height) / 2f
    val absX = centerX + offsetX
    val absY = centerY + offsetY

    // Clamp so at least MIN_VISIBLE_PX of the card remains within the
    // viewport.  This allows dragging even when the card is as wide/tall
    // as the viewport (common on Desktop where Dialog sizes to content).
    val minAbsX = m + vis - cardSize.width
    val maxAbsX = (viewportSize.width - m - vis).coerceAtLeast(minAbsX)
    val minAbsY = m + vis - cardSize.height
    val maxAbsY = (viewportSize.height - m - vis).coerceAtLeast(minAbsY)
    val clampedAbsX = absX.coerceIn(minAbsX, maxAbsX)
    val clampedAbsY = absY.coerceIn(minAbsY, maxAbsY)

    // Convert back to center-relative
    return Pair(clampedAbsX - centerX, clampedAbsY - centerY)
}

/**
 * Float-based viewport clamping that preserves sub-pixel precision.
 *
 * Unlike [clampPopupOffsetToViewport] which works with [IntOffset], this
 * function operates entirely in Float space so fractional drag deltas are
 * never lost during accumulation.
 */
fun clampPopupOffsetToViewportF(
    x: Float,
    y: Float,
    popupSize: IntSize,
    viewportSize: IntSize,
    marginPx: Int = MIN_POPUP_MARGIN_PX,
): Pair<Float, Float> {
    // Skip clamping when sizes have not been measured yet.
    if (popupSize == IntSize.Zero || viewportSize == IntSize.Zero) return Pair(x, y)

    val vw = viewportSize.width
    val vh = viewportSize.height
    val pw = popupSize.width
    val ph = popupSize.height
    val m = marginPx.toFloat()
    val vis = MIN_VISIBLE_PX.toFloat()
    val minX = m + vis - pw
    val maxX = (vw - m - vis).coerceAtLeast(minX)
    val minY = m + vis - ph
    val maxY = (vh - m - vis).coerceAtLeast(minY)
    return Pair(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
}

/**
 * A [Modifier] that starts tracking drag immediately on pointer-down with
 * **no touch-slop threshold**.
 *
 * The standard `detectDragGestures` requires the pointer to move past a
 * platform-defined slop distance before the drag callback fires. On desktop
 * this causes the first few pixels of cursor movement to be silently
 * swallowed, making dialogs feel "sticky" at the start of a drag.
 *
 * This modifier tracks raw pointer events directly, bypassing the slop gate
 * so every pixel of movement is reported from the very first frame.
 */
fun Modifier.draggableNoSlop(
    key: Any? = Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
): Modifier = this.pointerInput(key) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            when (event.type) {
                PointerEventType.Press -> {
                    // Do not aggressively consume Press, otherwise children
                    // (like text fields or buttons) won't get clicks.
                    // event.changes.forEach { it.consume() }
                }
                PointerEventType.Move -> {
                    event.changes.forEach { change ->
                        // Only drag if no child has consumed the event (e.g. scrolling or clicking)
                        if (change.pressed && !change.isConsumed) {
                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            if (dx != 0f || dy != 0f) {
                                change.consume()
                                onDrag(dx, dy)
                            }
                        }
                    }
                }
            }
        }
    }
}
