package com.reqlab.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.components.BottomPanel
import com.reqlab.ui.shared.components.ConfirmDeleteDialog
import com.reqlab.ui.shared.components.DirtyCloseDialog
import com.reqlab.ui.shared.components.DirtyMultiCloseDialog
import com.reqlab.ui.shared.components.EnvironmentEditDialog
import com.reqlab.ui.shared.components.ErrorMessageDialog
import com.reqlab.ui.shared.components.GlobalVariablesDialog
import com.reqlab.ui.shared.components.GraphQLPanel
import com.reqlab.ui.shared.components.HelpAboutDialog
import com.reqlab.ui.shared.components.HorizontalSplitPane
import com.reqlab.ui.shared.components.OperationProgressDialog
import com.reqlab.ui.shared.components.RealtimePanel
import com.reqlab.ui.shared.components.RequestEditor
import com.reqlab.ui.shared.components.RequestTabsBar
import com.reqlab.ui.shared.components.ResponseViewer
import com.reqlab.ui.shared.components.SettingsDialog
import com.reqlab.ui.shared.components.Sidebar
import com.reqlab.ui.shared.components.SidebarResizeDivider
import com.reqlab.ui.shared.components.TopToolbar
import com.reqlab.ui.shared.components.VerticalSplitPane
import com.reqlab.ui.shared.components.saveRequest
import com.reqlab.ui.shared.components.sendRequest
import com.reqlab.ui.shared.persistence.SettingsRepository
import com.reqlab.ui.shared.persistence.TabsRepository
import com.reqlab.ui.shared.persistence.WorkspaceRepository
import com.reqlab.ui.shared.platform.ioDispatcher
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.ResponseLayout
import com.reqlab.ui.shared.state.WorkspaceMode
import com.reqlab.ui.shared.theme.ReqLabColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main composable shell shared between Desktop and Web.
 * Contains: toolbar, sidebar, request/response editors, bottom panel, and all dialog overlays.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Composable
fun MainScreen(state: AppState = remember { AppState() }) {
    val scope = rememberCoroutineScope()
    var dirtyCloseTabIndex by remember { mutableStateOf<Int?>(null) }
    var multiDirtyIdsToClose by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastActiveTabId by remember(state) { mutableStateOf(state.activeTab?.id) }

    val forceCloseByIds: (List<String>) -> Unit = { ids ->
        ids
            .mapNotNull { id -> state.openTabs.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            .sortedDescending()
            .forEach { idx -> state.closeTab(idx) }
    }
    val closeManyTabs: (List<String>) -> Unit = { ids ->
        val hasDirty = ids.any { id -> state.openTabs.any { it.id == id && it.isDirty } }
        if (hasDirty) multiDirtyIdsToClose = ids else forceCloseByIds(ids)
    }

    val requestCloseTab: (Int) -> Unit = { index ->
        val tab = state.openTabs.getOrNull(index)
        if (tab == null) {
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
                    "|${responseLayout.name}|${language.name}|$requestTimeoutSec|$followRedirects|$collectionsExpanded|$environmentsExpanded|$proxyEnabled|$httpProxy|$httpsProxy"
            }
        }.drop(1)
            .collect { withContext(ioDispatcher) { SettingsRepository.save(state.settings) } }
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
            // Use bodyContent length as fingerprint instead of the full content.
            // Including the full body (potentially multi-MB) would create a huge
            // string on the main thread on every keystroke and block the UI.
            val bodyLen = t?.bodyContent?.length ?: 0
            "${state.openTabs.size}|${state.activeTabIndex}|${t?.name}|${t?.url}|${t?.method}|${t?.bodyType}|BL:$bodyLen|$params|$headers|$auth|${t?.preRequestScript}|${t?.testScript}"
        }.drop(1)
            .collect {
                if (state.settings.autoSaveRequests) {
                    withContext(ioDispatcher) { TabsRepository.save(state) }
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
            val globalFingerprint = state.globalVariables.joinToString("|") { gv ->
                "${gv.key}=${gv.value}:${gv.enabled}:${gv.secret}"
            }
            val historyFingerprint = "rev-${state.historyRevision}"
            // Include expand state so collapsing/expanding a collection triggers a save
            val expandedFingerprint = state.collectionExpandedState.entries
                .joinToString(",") { (k, v) -> "$k=$v" }
            "$collectionFingerprint#$envFingerprint#$globalFingerprint#$historyFingerprint#$expandedFingerprint"
        }.drop(1)
            .collect { withContext(ioDispatcher) { WorkspaceRepository.save(state) } }
    }

    LaunchedEffect(state.activeTabIndex, state.openTabs.size) {
        state.syncSidebarToActiveTab()
    }

    LaunchedEffect(state.activeTabIndex, state.openTabs.size, state.settings.autoSaveRequests) {
        val previousId = lastActiveTabId
        val currentId = state.activeTab?.id

        if (state.settings.autoSaveRequests && previousId != null && previousId != currentId) {
            val previousTab = state.openTabs.firstOrNull { it.id == previousId }
            if (previousTab != null && previousTab.isDirty) {
                withContext(ioDispatcher) { TabsRepository.save(state) }
                previousTab.markSaved()
            }
        }

        lastActiveTabId = currentId
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReqLabColors.Background)
            .testTag("main-screen")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isMeta = event.isMetaPressed || event.isCtrlPressed
                when {
                    isMeta && event.key == Key.Enter -> {
                        val activeTab = state.activeTab
                        if (activeTab != null) {
                            if (activeTab.isLoading) {
                                activeTab.currentJob?.cancel()
                                activeTab.isLoading = false
                            } else {
                                sendRequest(scope, state, activeTab)
                            }
                        }
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
        HelpAboutDialog(state)
        GlobalVariablesDialog(state)
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
                        withContext(ioDispatcher) { TabsRepository.save(state) }
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
            AnimatedVisibility(
                visible = state.sidebarExpanded,
                enter = expandHorizontally(),
                exit = shrinkHorizontally(),
            ) {
                Row {
                    Sidebar(state)
                    SidebarResizeDivider(state)
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (state.workspaceMode) {
                    WorkspaceMode.HTTP -> HttpWorkspaceContent(state, scope, requestCloseTab, closeManyTabs)
                    WorkspaceMode.REALTIME -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) { RealtimePanel() }
                    WorkspaceMode.GRAPHQL -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) { GraphQLPanel(state) }
                }

                BottomPanel(state)
            }
        }
    }
}

@Composable
private fun ColumnScope.HttpWorkspaceContent(
    state: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    onRequestClose: (Int) -> Unit,
    closeManyTabs: (List<String>) -> Unit,
) {
                RequestTabsBar(
                    state = state,
                    onRequestClose = onRequestClose,
                    onRenameTab = { requestId, newName ->
                        state.renameRequestEverywhere(requestId, newName)
                    },
                    onShowInSidebar = { requestId ->
                        state.revealRequestInSidebar(requestId)
                    },
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

                val tab = state.activeTab
                if (tab != null) {
                    if (state.settings.responseLayout == ResponseLayout.RIGHT) {
                        HorizontalSplitPane(
                            modifier = Modifier.weight(1f).testTag("response-layout-right"),
                            splitFraction = state.requestResponseSplit,
                            onSplitChanged = { state.requestResponseSplit = it },
                            first = {
                                RequestEditor(
                                    tab = tab,
                                    state = state,
                                    onSend = { sendRequest(scope, state, tab) },
                                    onCancel = { tab.currentJob?.cancel(); tab.isLoading = false },
                                    onSave = { saveRequest(scope, state, tab) },
                                )
                            },
                            second = {
                                ResponseViewer(tab)
                            },
                        )
                    } else {
                        VerticalSplitPane(
                            modifier = Modifier.weight(1f).testTag("response-layout-bottom"),
                            splitFraction = state.mainVerticalSplit,
                            onSplitChanged = { state.mainVerticalSplit = it },
                            first = {
                                RequestEditor(
                                    tab = tab,
                                    state = state,
                                    onSend = { sendRequest(scope, state, tab) },
                                    onCancel = { tab.currentJob?.cancel(); tab.isLoading = false },
                                    onSave = { saveRequest(scope, state, tab) },
                                )
                            },
                            second = {
                                ResponseViewer(tab)
                            },
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(ReqLabColors.Background)
                            .testTag("empty-workspace-view"),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = Strings.noRequestSelected,
                                color = ReqLabColors.OnSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = Strings.openRequestToStart,
                                color = ReqLabColors.OnSurfaceDim,
                                fontSize = 13.sp,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ReqLabColors.Primary)
                                        .clickable { state.addTabInSelectedCollection() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("empty-create-request"),
                                ) {
                                    Text(Strings.t("create_request"), color = ReqLabColors.OnPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
}
