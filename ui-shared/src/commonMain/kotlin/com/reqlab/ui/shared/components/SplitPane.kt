package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.reqlab.ui.shared.theme.ReqLabColors
import com.reqlab.ui.shared.platform.horizontalResizeCursor
import com.reqlab.ui.shared.platform.verticalResizeCursor

/**
 * A horizontal split pane (left | right) with a draggable vertical divider.
 *
 * Key fix: uses [rememberUpdatedState] so the drag lambda always reads the
 * *current* [splitFraction] rather than the stale captured value.
 */
@Composable
fun HorizontalSplitPane(
    modifier: Modifier = Modifier,
    splitFraction: Float,
    onSplitChanged: (Float) -> Unit,
    minFraction: Float = 0.20f,
    maxFraction: Float = 0.80f,
    dividerTag: String = "h-split-divider",
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    var containerWidth by remember { mutableStateOf(0) }

    // Always reflects the latest splitFraction without restarting the gesture
    val currentSplit by rememberUpdatedState(splitFraction)
    val currentCallback by rememberUpdatedState(onSplitChanged)

    val dividerInteraction = remember { MutableInteractionSource() }
    val isHovered by dividerInteraction.collectIsHoveredAsState()

    Row(modifier = modifier.onGloballyPositioned { containerWidth = it.size.width }) {
        Box(modifier = Modifier.fillMaxHeight().weight(currentSplit.coerceIn(minFraction, maxFraction))) {
            first()
        }

        // ── Draggable divider ───────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (isHovered) 6.dp else 4.dp)
                .background(if (isHovered) ReqLabColors.Primary.copy(alpha = 0.6f) else ReqLabColors.Border)
                .hoverable(dividerInteraction)
                .pointerHoverIcon(horizontalResizeCursor)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val w = containerWidth
                        if (w > 0) {
                            val delta = dragAmount.x / w.toFloat()
                            currentCallback((currentSplit + delta).coerceIn(minFraction, maxFraction))
                        }
                    }
                }
                .testTag(dividerTag),
        )

        Box(modifier = Modifier.fillMaxHeight().weight(1f - currentSplit.coerceIn(minFraction, maxFraction))) {
            second()
        }
    }
}

/**
 * A vertical split pane (top / bottom) with a draggable horizontal divider.
 *
 * [splitFraction] is how much vertical space the [first] panel occupies.
 */
@Composable
fun VerticalSplitPane(
    modifier: Modifier = Modifier,
    splitFraction: Float,
    onSplitChanged: (Float) -> Unit,
    minFraction: Float = 0.20f,
    maxFraction: Float = 0.85f,
    dividerTag: String = "v-split-divider",
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    var containerHeight by remember { mutableStateOf(0) }

    val currentSplit by rememberUpdatedState(splitFraction)
    val currentCallback by rememberUpdatedState(onSplitChanged)

    val dividerInteraction = remember { MutableInteractionSource() }
    val isHovered by dividerInteraction.collectIsHoveredAsState()

    Column(modifier = modifier.onGloballyPositioned { containerHeight = it.size.height }) {
        Box(modifier = Modifier.fillMaxWidth().weight(currentSplit.coerceIn(minFraction, maxFraction))) {
            first()
        }

        // ── Draggable divider ───────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isHovered) 6.dp else 4.dp)
                .background(if (isHovered) ReqLabColors.Primary.copy(alpha = 0.6f) else ReqLabColors.Border)
                .hoverable(dividerInteraction)
                .pointerHoverIcon(verticalResizeCursor)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val h = containerHeight
                        if (h > 0) {
                            val delta = dragAmount.y / h.toFloat()
                            currentCallback((currentSplit + delta).coerceIn(minFraction, maxFraction))
                        }
                    }
                }
                .testTag(dividerTag),
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f - currentSplit.coerceIn(minFraction, maxFraction))) {
            second()
        }
    }
}
