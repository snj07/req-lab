package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.RequestTabState
import com.reqlab.ui.shared.theme.ReqLabColors
import com.reqlab.ui.shared.platform.horizontalResizeCursor

/**
 * The horizontal tab bar that shows all open request tabs with a "+" button.
 * Supports horizontal scrolling, right-click context menus (per-tab), and
 * the active-tab underline indicator.
 */
@Composable
fun RequestTabsBar(
    state: AppState,
    onRequestClose: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseToLeft: (Int) -> Unit,
    onCloseToRight: (Int) -> Unit,
    onCloseAll: () -> Unit,
) {
    val tabScrollState = rememberScrollState()
    // Natural (layout-coordinate) x-position of each tab chip inside the
    // scrollable Row. Populated by onGloballyPositioned on each chip wrapper.
    val tabPositions = remember { mutableStateMapOf<Int, Int>() }

    // Auto-scroll so the active tab is visible whenever it changes
    // (e.g. when the user clicks a request in the sidebar).
    LaunchedEffect(state.activeTabIndex) {
        val x = tabPositions[state.activeTabIndex] ?: return@LaunchedEffect
        tabScrollState.animateScrollTo(x)
    }

    Column(modifier = Modifier.fillMaxWidth().testTag("request-tabs-bar")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(ReqLabColors.Surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Horizontally scrollable chips row
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(tabScrollState)
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                state.openTabs.forEachIndexed { index, tab ->
                    // Box captures the chip's natural x-offset in the Row's
                    // layout coordinate space for auto-scroll targeting.
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            tabPositions[index] = coords.positionInParent().x.toInt()
                        },
                    ) {
                        RequestTabChip(
                            tab = tab,
                            isActive = index == state.activeTabIndex,
                            onClick = {
                                state.activeTabIndex = index
                                state.selectedRequestId = tab.id
                            },
                            onClose = { onRequestClose(index) },
                            onCloseOthers = { onCloseOthers(index) },
                            onCloseToLeft = { onCloseToLeft(index) },
                            onCloseToRight = { onCloseToRight(index) },
                            onCloseAll = onCloseAll,
                            showClose = state.openTabs.size > 1,
                        )
                    }
                }
            }

            // New-tab button
            IconButton(
                onClick = { state.addTabInSelectedCollection() },
                modifier = Modifier.size(28.dp).padding(end = 4.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New tab",
                    tint = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Bottom border
        Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
    }
}

// ── Single tab chip ─────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RequestTabChip(
    tab: RequestTabState,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseToLeft: () -> Unit,
    onCloseToRight: () -> Unit,
    onCloseAll: () -> Unit,
    showClose: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    var showContextMenu by remember { mutableStateOf(false) }

    // Outer Box fills the 36dp row height so the indicator can anchor at the bottom
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(
                when {
                    isActive  -> ReqLabColors.Background
                    isHovered -> ReqLabColors.HoverOverlay
                    else      -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .onPointerEvent(PointerEventType.Press) {
                if (it.buttons.isSecondaryPressed) showContextMenu = true
            }
            .testTag("tab-chip-${tab.id}"),
    ) {
        // Tab label row
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MethodBadge(tab.method, compact = true)
            Text(
                text = tab.name + if (tab.isDirty) " *" else "",
                color = if (isActive) ReqLabColors.OnSurface else ReqLabColors.OnSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showClose && (isActive || isHovered)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onClose)
                        .testTag("tab-close-${tab.id}"),
                )
            }
        }

        // Active-tab underline indicator (2dp, Primary colour)
        if (isActive) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ReqLabColors.Primary)
                    .testTag("tab-active-indicator-${tab.id}"),
            )
        }

        // Right-click context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            if (showClose) DropdownMenuItem(
                text = { Text("Close") },
                onClick = { showContextMenu = false; onClose() },
                modifier = Modifier.testTag("tab-ctx-close"),
            )
            DropdownMenuItem(
                text = { Text("Close Others") },
                onClick = { showContextMenu = false; onCloseOthers() },
                modifier = Modifier.testTag("tab-ctx-close-others"),
            )
            DropdownMenuItem(
                text = { Text("Close Tabs to the Left") },
                onClick = { showContextMenu = false; onCloseToLeft() },
                modifier = Modifier.testTag("tab-ctx-close-left"),
            )
            DropdownMenuItem(
                text = { Text("Close Tabs to the Right") },
                onClick = { showContextMenu = false; onCloseToRight() },
                modifier = Modifier.testTag("tab-ctx-close-right"),
            )
            DropdownMenuItem(
                text = { Text("Close All") },
                onClick = { showContextMenu = false; onCloseAll() },
                modifier = Modifier.testTag("tab-ctx-close-all"),
            )
        }
    }
}

// ── Sidebar resize divider ───────────────────────────────────────

/**
 * Thin draggable vertical bar that lets the user resize the sidebar.
 * Clamps sidebar width between 200 dp and 500 dp.
 */
@Composable
fun SidebarResizeDivider(state: AppState) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (isHovered) 6.dp else 4.dp)
            .background(if (isHovered) ReqLabColors.Primary.copy(alpha = 0.6f) else ReqLabColors.Border)
            .hoverable(interaction)
            .pointerHoverIcon(horizontalResizeCursor)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    state.sidebarWidth = (state.sidebarWidth + dragAmount.x.toInt())
                        .coerceIn(150, 500)
                }
            }
            .testTag("sidebar-resize-divider"),
    )
}
