package com.reqlab.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.reqlab.core.model.AuthConfig
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.NetworkEvent
import com.reqlab.core.network.NetworkLogger
import com.reqlab.core.network.RetryPolicy
import com.reqlab.ui.desktop.components.BottomPanel
import com.reqlab.ui.desktop.components.ConfirmDeleteDialog
import com.reqlab.ui.desktop.components.DirtyCloseDialog
import com.reqlab.ui.desktop.components.DirtyMultiCloseDialog
import com.reqlab.ui.desktop.components.ErrorMessageDialog
import com.reqlab.ui.desktop.components.EnvironmentEditDialog
import com.reqlab.ui.desktop.components.HorizontalSplitPane
import com.reqlab.ui.desktop.components.MethodBadge
import com.reqlab.ui.desktop.components.OperationProgressDialog
import com.reqlab.ui.desktop.components.RequestEditor
import com.reqlab.ui.desktop.components.ResponseViewer
import com.reqlab.ui.desktop.components.SettingsDialog
import com.reqlab.ui.desktop.components.Sidebar
import com.reqlab.ui.desktop.components.TopToolbar
import com.reqlab.ui.desktop.network.NetworkClientFactory
import com.reqlab.ui.desktop.persistence.SettingsRepository
import com.reqlab.ui.desktop.persistence.TabsRepository
import com.reqlab.ui.desktop.persistence.WorkspaceRepository
import com.reqlab.ui.desktop.state.AppState
import com.reqlab.ui.desktop.state.LogLevel
import com.reqlab.ui.desktop.state.RequestTabState
import com.reqlab.ui.desktop.theme.ReqLabColors
import com.reqlab.ui.desktop.theme.ReqLabTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import java.awt.Cursor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main() = application {
    // AppState created here so the theme wrapping the Window reacts to settings.theme.
    val state = remember {
        AppState().also {
            SettingsRepository.load(it.settings)  // restore persisted settings
            TabsRepository.load(it)               // restore last open tabs
            WorkspaceRepository.load(it)          // restore collections + environments
        }
    }

    Window(
        onCloseRequest = {
            TabsRepository.save(state)            // persist tabs on close
            WorkspaceRepository.save(state)       // persist workspace on close
            exitApplication()
        },
        title = "ReqLab",
        state = rememberWindowState(
            size = DpSize(1400.dp, 900.dp),
            position = WindowPosition.Aligned(Alignment.Center),
        ),
        onPreviewKeyEvent = { false },
    ) {
        ReqLabTheme(appTheme = state.settings.theme) {
            DesktopShell(state)
        }
    }
}

// ── Main shell ──────────────────────────────────────────────────

@Composable
fun DesktopShell(state: AppState = remember { AppState() }) {
    val scope = rememberCoroutineScope()
    var dirtyCloseTabIndex by remember { mutableStateOf<Int?>(null) }
    // Multi-tab batch-close: stores IDs to close; non-empty triggers DirtyMultiCloseDialog
    var multiDirtyIdsToClose by remember { mutableStateOf<List<String>>(emptyList()) }

    // Force-close tabs by ID (no dirty-state check) - descending to avoid index drift
    val forceCloseByIds: (List<String>) -> Unit = { ids ->
        ids
            .mapNotNull { id -> state.openTabs.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            .sortedDescending()
            .forEach { idx -> if (state.openTabs.size > 1) state.closeTab(idx) }
    }
    // Attempt to close tabs by ID – prompts if any are dirty
    val closeManyTabs: (List<String>) -> Unit = { ids ->
        val hasDirty = ids.any { id -> state.openTabs.any { it.id == id && it.isDirty } }
        if (hasDirty) multiDirtyIdsToClose = ids else forceCloseByIds(ids)
    }

    val requestCloseTab: (Int) -> Unit = { index ->
        val tab = state.openTabs.getOrNull(index)
        if (tab == null || state.openTabs.size <= 1) {
            Unit
        } else if (tab.isDirty) {
            dirtyCloseTabIndex = index
        } else {
            state.closeTab(index)
        }
    }

    // ── Persist settings whenever any field changes ───────────────────────
    LaunchedEffect(state) {
        snapshotFlow {
            with(state.settings) {
                "$autoSaveRequests|$confirmBeforeDelete|$defaultTimeoutSec|${theme.name}" +
                    "|$requestTimeoutSec|$followRedirects|$proxyEnabled|$httpProxy|$httpsProxy"
            }
        }.drop(1) // skip initial emission (values were just loaded from disk)
            .collect { withContext(Dispatchers.IO) { SettingsRepository.save(state.settings) } }
    }

    // ── Auto-save tabs when switching or editing (if auto-save is on) ─────
    LaunchedEffect(state) {
        snapshotFlow {
            val t = state.activeTab
            val params = t?.params?.joinToString(";") { p -> "${p.key}:${p.value}:${p.enabled}:${p.secret}" } ?: ""
            val headers = t?.headers?.joinToString(";") { h -> "${h.key}:${h.value}:${h.enabled}:${h.secret}" } ?: ""
            val auth = if (t == null) "" else {
                "${t.authType}|${t.authUsername}|${t.authPassword}|${t.authToken}|${t.authApiKey}|${t.authApiValue}"
            }
            "${state.openTabs.size}|${state.activeTabIndex}|${t?.name}|${t?.url}|${t?.method}|${t?.bodyType}|${t?.bodyContent}|$params|$headers|$auth|${t?.preRequestScript}|${t?.testScript}"
        }.drop(1)
            .collect {
                if (state.settings.autoSaveRequests) {
                    withContext(Dispatchers.IO) { TabsRepository.save(state) }
                }
            }
    }

    LaunchedEffect(state) {
        snapshotFlow {
            val collectionFingerprint = state.collectionsRevision
            val envFingerprint = state.environments.joinToString("|") { env ->
                val vars = env.variables.joinToString(",") { v -> "${v.key}=${v.value}:${v.enabled}:${v.secret}" }
                "${env.name}:[$vars]"
            }
            "$collectionFingerprint#$envFingerprint"
        }.drop(1)
            .collect { withContext(Dispatchers.IO) { WorkspaceRepository.save(state) } }
    }

    LaunchedEffect(state.activeTabIndex, state.openTabs.size) {
        state.selectedRequestId = state.activeTab?.id
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReqLabColors.Background)
            .testTag("desktop-shell")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isMeta = event.isMetaPressed || event.isCtrlPressed
                when {
                    isMeta && event.key == Key.Enter -> {
                        state.activeTab?.let { sendRequest(scope, state, it) }
                        true
                    }
                    isMeta && event.key == Key.LeftBracket && event.isShiftPressed -> {
                        val from = state.activeTabIndex
                        if (from > 0) state.moveTab(from, from - 1)
                        true
                    }
                    isMeta && event.key == Key.RightBracket && event.isShiftPressed -> {
                        val from = state.activeTabIndex
                        if (from < state.openTabs.lastIndex) state.moveTab(from, from + 1)
                        true
                    }
                    isMeta && event.key == Key.S -> {
                        state.activeTab?.let { saveRequest(scope, state, it) }
                        true
                    }
                    isMeta && event.key == Key.W -> {
                        requestCloseTab(state.activeTabIndex)
                        true
                    }
                    isMeta && event.key == Key.N -> {
                        state.addTabInSelectedCollection()
                        true
                    }
                    isMeta && event.key == Key.Comma -> {
                        state.showSettingsDialog = true
                        true
                    }
                    else -> false
                }
            },
    ) {
        // ── Top toolbar ─────────────────────────────────────
        TopToolbar(state)

        // ── Dialog overlays ─────────────────────────────────
        EnvironmentEditDialog(state)
        SettingsDialog(state)
        if (state.showConfirmDialog) {
            ConfirmDeleteDialog(
                title   = state.confirmDialogTitle,
                message = state.confirmDialogMessage,
                onConfirm = { state.resolveConfirm(true) },
                onDismiss = { state.resolveConfirm(false) },
            )
        }
        if (state.showOperationDialog) {
            OperationProgressDialog(
                title = state.operationTitle,
                message = state.operationMessage,
                onCancel = { state.cancelOperation() },
            )
        }
        if (state.showErrorDialog) {
            ErrorMessageDialog(
                title = state.errorDialogTitle,
                message = state.errorDialogMessage,
                onDismiss = { state.dismissError() },
            )
        }
        dirtyCloseTabIndex?.let { closeIndex ->
            DirtyCloseDialog(
                onSave = {
                    val tab = state.openTabs.getOrNull(closeIndex)
                    if (tab != null) {
                        saveRequest(scope, state, tab) {
                            state.closeTab(closeIndex)
                            dirtyCloseTabIndex = null
                        }
                    } else {
                        dirtyCloseTabIndex = null
                    }
                },
                onDiscard = {
                    state.closeTab(closeIndex)
                    dirtyCloseTabIndex = null
                },
                onCancel = { dirtyCloseTabIndex = null },
            )
        }
        if (multiDirtyIdsToClose.isNotEmpty()) {
            val ids = multiDirtyIdsToClose
            val dirtyCount = ids.count { id -> state.openTabs.any { it.id == id && it.isDirty } }
            DirtyMultiCloseDialog(
                dirtyCount = dirtyCount,
                onSaveAll = {
                    scope.launch {
                        withContext(Dispatchers.IO) { TabsRepository.save(state) }
                        state.openTabs.forEach { it.markSaved() }
                        forceCloseByIds(ids)
                        multiDirtyIdsToClose = emptyList()
                    }
                },
                onDiscardAll = {
                    forceCloseByIds(ids)
                    multiDirtyIdsToClose = emptyList()
                },
                onCancel = { multiDirtyIdsToClose = emptyList() },
            )
        }

        // ── Main content ────────────────────────────────────
        Row(modifier = Modifier.weight(1f)) {
            // Sidebar (animated collapse)
            AnimatedVisibility(
                visible = state.sidebarExpanded,
                enter = expandHorizontally(),
                exit = shrinkHorizontally(),
            ) {
                Row {
                    Sidebar(state)
                    // Draggable sidebar resize divider
                    SidebarResizeDivider(state)
                }
            }

            // ── Workspace area ────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Request tabs bar
                RequestTabsBar(
                    state = state,
                    onRequestClose = requestCloseTab,
                    onCloseOthers = { idx ->
                        val ids = state.openTabs.indices.filter { it != idx }.map { state.openTabs[it].id }
                        closeManyTabs(ids)
                    },
                    onCloseToLeft = { idx ->
                        val ids = (0 until idx).map { state.openTabs[it].id }
                        closeManyTabs(ids)
                    },
                    onCloseToRight = { idx ->
                        val ids = (idx + 1..state.openTabs.lastIndex).map { state.openTabs[it].id }
                        closeManyTabs(ids)
                    },
                    onCloseAll = {
                        closeManyTabs(state.openTabs.map { it.id })
                    },
                )

                // Request / Response split
                val tab = state.activeTab
                if (tab != null) {
                    HorizontalSplitPane(
                        modifier = Modifier.weight(1f),
                        splitFraction = state.requestResponseSplit,
                        onSplitChanged = { state.requestResponseSplit = it },
                        first = {
                            RequestEditor(
                                tab = tab,
                                state = state,
                                onSend = { sendRequest(scope, state, tab) },
                                onSave = { saveRequest(scope, state, tab) },
                            )
                        },
                        second = {
                            ResponseViewer(tab)
                        },
                    )
                }

                // ── Bottom panel ────────
                BottomPanel(state)
            }
        }
    }
}

// ── Request tabs bar ────────────────────────────────────────────

@Composable
private fun RequestTabsBar(
    state: AppState,
    onRequestClose: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseToLeft: (Int) -> Unit,
    onCloseToRight: (Int) -> Unit,
    onCloseAll: () -> Unit,
) {
    val tabScrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth().testTag("request-tabs-bar")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(ReqLabColors.Surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tabs – horizontally scrollable so overflow tabs are reachable
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(tabScrollState)
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                state.openTabs.forEachIndexed { index, tab ->
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

            // New tab button
            IconButton(
                onClick = { state.addTabInSelectedCollection() },
                modifier = Modifier.size(28.dp).padding(end = 4.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "New tab", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
            }
        }
        // bottom border
        Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))
    }
}

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

    // Outer Box: fills parent height so the indicator can anchor at the bottom
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
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MethodBadge(tab.method, compact = true)
            Text(
                // ● (unsaved dot) instead of bare " *" — more Postman-like
                text = tab.name + if (tab.isDirty) " ●" else "",
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

        // Active tab indicator: 2dp Primary-coloured bar pinned to the bottom
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

// ── Request execution ───────────────────────────────────────────

private fun sendRequest(scope: CoroutineScope, state: AppState, tab: RequestTabState) {
    if (tab.url.isBlank()) {
        state.log("URL is empty", LogLevel.WARNING)
        return
    }

    scope.launch {
        tab.isLoading = true
        tab.response  = null
        tab.lastError = null
        state.log("→ ${tab.method} ${tab.url}")

        try {
            val request = RequestDefinition(
                id = tab.id,
                name = tab.name,
                method = tab.method,
                url = tab.url,
                queryParams = tab.params.filter { it.enabled }.map { KeyValueEntry(it.key, it.value) },
                headers = tab.headers.filter { it.enabled }.map { KeyValueEntry(it.key, it.value) },
                auth = buildAuthConfig(tab),
                body = RequestBody(type = tab.bodyType, content = tab.bodyContent.ifBlank { null }),
                createdAtEpochMillis = System.currentTimeMillis(),
                updatedAtEpochMillis = System.currentTimeMillis(),
            )

            val logger = object : NetworkLogger {
                override fun debug(message: String) { state.log(message) }
                override fun info(message: String)  { state.log(message) }
                override fun error(message: String, throwable: Throwable?) { state.log(message, LogLevel.ERROR) }
            }
            // Build a fresh client from current settings (timeout, proxy, redirects)
            val client = NetworkClientFactory.build(
                settings = state.settings,
                logger = logger,
                retryPolicy = RetryPolicy(
                    maxAttempts = tab.retryCount.coerceAtLeast(1),
                    baseDelayMs = tab.retryDelayMs.coerceAtLeast(0L),
                    maxDelayMs = (tab.retryDelayMs.coerceAtLeast(0L) * 10L).coerceAtLeast(tab.retryDelayMs),
                ),
            )

            client.execute(request, state.activeVariableLayers()).collect { event ->
                when (event) {
                    is NetworkEvent.Started -> {
                        state.log("Request started", LogLevel.INFO)
                    }
                    is NetworkEvent.RetryScheduled -> {
                        state.log("Retry #${event.attempt} in ${event.delayMs}ms – ${event.reason}", LogLevel.WARNING)
                    }
                    is NetworkEvent.Success -> {
                        tab.response  = event.response
                        tab.lastError = null
                        state.log(
                            "← ${event.response.statusCode} ${event.response.statusText}  (${event.response.metrics.responseTimeMs}ms, ${event.response.metrics.responseSizeBytes}B)",
                            LogLevel.SUCCESS,
                        )
                    }
                    is NetworkEvent.Failure -> {
                        tab.lastError = event.error.message ?: "Unknown error"
                        state.log("✗ ${event.error.message}", LogLevel.ERROR)
                    }
                }
            }
        } catch (e: Exception) {
            tab.lastError = e.message ?: "Unknown error"
            state.log("✗ ${e.message ?: "Unknown error"}", LogLevel.ERROR)
        } finally {
            tab.isLoading = false
        }
    }
}

private fun saveRequest(scope: CoroutineScope, state: AppState, tab: RequestTabState, onSaved: (() -> Unit)? = null) {
    tab.syncSystemHeaders()
    scope.launch {
        withContext(Dispatchers.IO) {
            TabsRepository.save(state)
        }
        tab.markSaved()
        state.log("✓ Request saved: ${tab.name}", LogLevel.SUCCESS)
        onSaved?.invoke()
    }
}

private fun buildAuthConfig(tab: RequestTabState): AuthConfig {
    val params = when (tab.authType) {
        AuthType.BASIC   -> mapOf("username" to tab.authUsername, "password" to tab.authPassword)
        AuthType.BEARER  -> mapOf("token" to tab.authToken)
        AuthType.JWT     -> mapOf("token" to tab.authToken)
        AuthType.API_KEY -> mapOf("key" to tab.authApiKey, "value" to tab.authApiValue)
        else -> emptyMap()
    }
    return AuthConfig(type = tab.authType, params = params)
}

// ── Sidebar resize divider ───────────────────────────────────────

/**
 * A draggable vertical bar that resizes the sidebar.
 * Min width = 200dp, max width = 500dp.
 */
@Composable
private fun SidebarResizeDivider(state: AppState) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (isHovered) 6.dp else 4.dp)
            .background(if (isHovered) ReqLabColors.Primary.copy(alpha = 0.6f) else ReqLabColors.Border)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    state.sidebarWidth = (state.sidebarWidth + dragAmount.x.toInt())
                        .coerceIn(200, 500)
                }
            }
            .testTag("sidebar-resize-divider"),
    )
}
