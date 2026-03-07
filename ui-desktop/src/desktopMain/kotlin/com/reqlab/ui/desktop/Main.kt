package com.reqlab.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.reqlab.ui.desktop.components.BottomPanel
import com.reqlab.ui.desktop.components.ConfirmDeleteDialog
import com.reqlab.ui.desktop.components.DirtyCloseDialog
import com.reqlab.ui.desktop.components.DirtyMultiCloseDialog
import com.reqlab.ui.desktop.components.EnvironmentEditDialog
import com.reqlab.ui.desktop.components.ErrorMessageDialog
import com.reqlab.ui.desktop.components.HorizontalSplitPane
import com.reqlab.ui.desktop.components.OperationProgressDialog
import com.reqlab.ui.desktop.components.RequestEditor
import com.reqlab.ui.desktop.components.RequestTabsBar
import com.reqlab.ui.desktop.components.ResponseViewer
import com.reqlab.ui.desktop.components.SettingsDialog
import com.reqlab.ui.desktop.components.Sidebar
import com.reqlab.ui.desktop.components.SidebarResizeDivider
import com.reqlab.ui.desktop.components.TopToolbar
import com.reqlab.ui.desktop.components.saveRequest
import com.reqlab.ui.desktop.components.sendRequest
import com.reqlab.ui.desktop.persistence.SettingsRepository
import com.reqlab.ui.desktop.persistence.TabsRepository
import com.reqlab.ui.desktop.persistence.WorkspaceRepository
import com.reqlab.ui.desktop.state.AppState
import com.reqlab.ui.desktop.state.RequestTabState
import com.reqlab.ui.desktop.theme.ReqLabColors
import com.reqlab.ui.desktop.theme.ReqLabTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
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

