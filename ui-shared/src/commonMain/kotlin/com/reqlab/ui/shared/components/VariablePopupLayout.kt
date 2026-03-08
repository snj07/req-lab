package com.reqlab.ui.shared.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

private const val MIN_POPUP_MARGIN_PX = 8

fun clampPopupOffsetToViewport(
    candidate: IntOffset,
    popupSize: IntSize,
    viewportSize: IntSize,
    marginPx: Int = MIN_POPUP_MARGIN_PX,
): IntOffset {
    val popupWidth = popupSize.width.coerceAtLeast(1)
    val popupHeight = popupSize.height.coerceAtLeast(1)
    val viewportWidth = viewportSize.width.coerceAtLeast(1)
    val viewportHeight = viewportSize.height.coerceAtLeast(1)

    val minX = marginPx
    val minY = marginPx
    val maxX = (viewportWidth - popupWidth - marginPx).coerceAtLeast(minX)
    val maxY = (viewportHeight - popupHeight - marginPx).coerceAtLeast(minY)

    return IntOffset(
        x = candidate.x.coerceIn(minX, maxX),
        y = candidate.y.coerceIn(minY, maxY),
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

    // Center-relative to absolute
    val centerX = (viewportSize.width - cardSize.width) / 2f
    val centerY = (viewportSize.height - cardSize.height) / 2f
    val absX = centerX + offsetX
    val absY = centerY + offsetY

    // Clamp absolute position so card stays inside viewport with margin
    val maxAbsX = (viewportSize.width - cardSize.width - marginPx).toFloat().coerceAtLeast(marginPx.toFloat())
    val maxAbsY = (viewportSize.height - cardSize.height - marginPx).toFloat().coerceAtLeast(marginPx.toFloat())
    val clampedAbsX = absX.coerceIn(marginPx.toFloat(), maxAbsX)
    val clampedAbsY = absY.coerceIn(marginPx.toFloat(), maxAbsY)

    // Convert back to center-relative
    return Pair(clampedAbsX - centerX, clampedAbsY - centerY)
}
