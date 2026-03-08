package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.reqlab.ui.shared.platform.currentTimeMillis
import com.reqlab.ui.shared.theme.ReqLabColors
import kotlinx.coroutines.delay

/**
 * Shared state for the single sidebar tooltip overlay.
 *
 * We store the hovered item ID, the tooltip text, and the pixel coordinates of
 * the hovered row inside the sidebar container (sidebar-local space). The
 * overlay is rendered once at the Sidebar root — no per-node Popup windows —
 * which eliminates the OS-window boundary hover-leave flicker.
 */
class TooltipState {
    var hoveredItemId    by mutableStateOf<String?>(null)
    var tooltipVisible   by mutableStateOf(false)
    var tooltipText      by mutableStateOf("")
    /** Absolute Y of the sidebar Box top-edge in root (window) pixel coordinates. */
    var containerRootY   by mutableStateOf(0)
    /** Absolute Y of the hovered row top in root pixel coordinates. */
    var tooltipRowRootY  by mutableStateOf(0)
    /** Height of the hovered row in pixels. */
    var tooltipRowHeight by mutableStateOf(0)
    /** Horizontal indent in pixels so the tooltip aligns under the row text. */
    var tooltipIndentXPx by mutableStateOf(0)

    private var hoverStartTime = 0L

    /** Milliseconds elapsed since the current hover started. */
    fun elapsedSinceHoverStart(): Long = currentTimeMillis() - hoverStartTime

    /** Called when a sidebar row gains hover. */
    fun onHoverEnter(itemId: String, text: String) {
        if (hoveredItemId != itemId) {
            hoveredItemId  = itemId
            tooltipText    = text
            hoverStartTime = currentTimeMillis()
            tooltipVisible = false
        }
    }

    /** Called when a sidebar row loses hover. */
    fun onHoverExit(itemId: String) {
        if (hoveredItemId == itemId) {
            hoveredItemId  = null
            tooltipVisible = false
        }
    }

    /**
     * Called from the hovered row's onGloballyPositioned so the overlay knows
     * where to draw without a separate Popup anchor.
     */
    fun updateHoverPosition(rowRootY: Int, rowHeightPx: Int, indentXPx: Int) {
        tooltipRowRootY  = rowRootY
        tooltipRowHeight = rowHeightPx
        tooltipIndentXPx = indentXPx
    }

    /** Sidebar-local Y of the tooltip row, relative to the sidebar container top. */
    val localRowY: Int get() = tooltipRowRootY - containerRootY
}

/** Singleton used by all sidebar items and by the Sidebar composable. */
val sharedSidebarTooltipState = TooltipState()

/**
 * Drives the 300 ms delay before the tooltip becomes visible.
 * Must be composed exactly once, at the Sidebar root.
 */
@Composable
fun SharedTooltipCoordinator(state: TooltipState = sharedSidebarTooltipState) {
    LaunchedEffect(state.hoveredItemId) {
        val itemId = state.hoveredItemId ?: return@LaunchedEffect
        val elapsed = state.elapsedSinceHoverStart()
        val wait    = 300L - elapsed
        if (wait > 0L) delay(wait)
        if (state.hoveredItemId == itemId) {
            state.tooltipVisible = true
        }
    }
}

/**
 * Single, non-Popup tooltip overlay rendered as a child of the Sidebar Box.
 *
 * Because this is an in-tree Box rather than a floating OS window, moving the
 * mouse over the tooltip text does NOT cause the sidebar row to fire a
 * hover-exit — eliminating the flicker loop that occurred with Popup.
 *
 * Call this ONCE inside the Sidebar root Box, after the content Column.
 */
@Composable
fun SidebarTooltipOverlay(state: TooltipState = sharedSidebarTooltipState) {
    if (!state.tooltipVisible || state.hoveredItemId == null) return

    // Bottom edge of the hovered row in sidebar-local pixels
    val yPx = (state.localRowY + state.tooltipRowHeight).coerceAtLeast(0)
    val xPx = state.tooltipIndentXPx

    // wrapContentSize(unbounded=true) lets the tooltip extend past the sidebar
    // width for long names — the sidebar Box does not clip its children.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .zIndex(100f),
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(xPx, yPx) }
                .clip(RoundedCornerShape(4.dp))
                .background(ReqLabColors.SurfaceHigh)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("sidebar-tooltip"),
        ) {
            Text(
                text = state.tooltipText,
                color = ReqLabColors.OnSurface,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}
