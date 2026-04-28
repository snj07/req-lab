package com.reqlab.ui.shared.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.persistence.ImportExportException
import com.reqlab.ui.shared.persistence.ImportExportNaming
import com.reqlab.ui.shared.persistence.ImportExportRepository
import com.reqlab.ui.shared.persistence.WorkspaceRepository
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import com.reqlab.ui.shared.state.EnvState
import com.reqlab.ui.shared.state.HistoryItem
import com.reqlab.ui.shared.state.LogLevel
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors
import com.reqlab.ui.shared.theme.httpMethodColor
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.reqlab.ui.shared.platform.formatTimestamp
import com.reqlab.ui.shared.platform.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.reqlab.ui.shared.platform.pickFileForImport
import com.reqlab.ui.shared.platform.saveFileForExport
import com.reqlab.ui.shared.platform.generateUuid

private data class TreeNodeHitArea(
    val id: String,
    val isFolder: Boolean,
    val parentCollectionId: String?,
    val top: Float,
    val bottom: Float,
    val label: String,
)

private fun normalizeSidebarSearchQuery(raw: String): String {
    // Strip invisible formatting characters so visually empty input cannot
    // keep a stale filter active (e.g., zero-width spaces from paste/IME).
    return raw
        .filterNot { ch ->
            ch == '\u200B' || ch == '\u200C' || ch == '\u200D' || ch == '\uFEFF'
        }
        .trim()
}

@Composable
fun Sidebar(state: AppState) {
    SharedTooltipCoordinator()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val collectionsRevision = state.collectionsRevision
    // Plain HashMap — never read inside composition, no state notifications needed.
    val hitAreas = remember { HashMap<String, TreeNodeHitArea>() }

    var draggedRequestId by remember { mutableStateOf<String?>(null) }
    var dropTargetCollectionId by remember { mutableStateOf<String?>(null) }
    var dropTargetRequestId by remember { mutableStateOf<String?>(null) }
    // true = insert after the target row; false = insert before
    var dropInsertAfter by remember { mutableStateOf(false) }
    // Cumulative Y-offset from drag start to compute current cursor Y
    var dragOffsetY by remember { mutableStateOf(0f) }
    // Absolute cursor Y in window coords — set on drag start, accumulated on every delta.
    // Used by the auto-scroll loop so it never drifts when the list scrolls under the cursor.
    var dragCursorAbsY by remember { mutableStateOf(0f) }

    // ── Collection-level drag-to-reorder state ─────────────────────────────
    var draggedCollectionId by remember { mutableStateOf<String?>(null) }
    var dragCollectionOffsetY by remember { mutableStateOf(0f) }
    var dropCollectionTargetId by remember { mutableStateOf<String?>(null) }
    var dropCollectionInsertAfter by remember { mutableStateOf(false) }
    // Hit areas for top-level collection root rows
    val collectionHitAreas = remember { HashMap<String, TreeNodeHitArea>() }

    // ── Sidebar LazyColumn state + bounds for edge-scroll ─────────────────
    val lazyListState = rememberLazyListState()
    var sidebarTopPx by remember { mutableStateOf(0f) }
    var sidebarHeightPx by remember { mutableStateOf(0) }
    val edgeZonePx = with(density) { 72.dp.toPx() }

    // Auto-scroll the sidebar list when the drag cursor is near the top/bottom edge.
    // Uses dragCursorAbsY which is always the true pointer position (set on drag-start,
    // accumulated on every pointer delta). After each scroll tick we shift stored hit-area
    // positions by the same amount so the insertion indicator re-evaluates immediately
    // rather than waiting for the next Compose layout pass.
    LaunchedEffect(draggedRequestId, draggedCollectionId) {
        if (draggedRequestId == null && draggedCollectionId == null) return@LaunchedEffect
        while (true) {
            val relY = dragCursorAbsY - sidebarTopPx
            val scrollPx = when {
                relY >= 0f && relY < edgeZonePx ->
                    -((edgeZonePx - relY) / edgeZonePx * 18f)
                relY > sidebarHeightPx - edgeZonePx && relY <= sidebarHeightPx ->
                    (relY - (sidebarHeightPx - edgeZonePx)) / edgeZonePx * 18f
                else -> 0f
            }
            if (scrollPx != 0f) {
                lazyListState.scrollBy(scrollPx)
                // Items shifted on screen by -scrollPx. Adjust stored hit areas so
                // the drop-target lookup below uses up-to-date positions (the real
                // onGloballyPositioned update will arrive on the next layout pass).
                val shift = -scrollPx
                val activeRequestId   = draggedRequestId
                val activeCollectionId = draggedCollectionId
                val cursorY = dragCursorAbsY
                if (activeRequestId != null) {
                    hitAreas.keys.toList().forEach { id ->
                        hitAreas[id]?.let { hitAreas[id] = it.copy(top = it.top + shift, bottom = it.bottom + shift) }
                    }
                    val target = hitAreas.values
                        .filter { it.id != activeRequestId }
                        .minByOrNull { kotlin.math.abs(((it.top + it.bottom) / 2f) - cursorY) }
                    if (target != null) {
                        if (target.isFolder) {
                            dropTargetCollectionId = target.id
                            dropTargetRequestId    = null
                            dropInsertAfter        = false
                        } else {
                            val midY = (target.top + target.bottom) / 2f
                            dropInsertAfter        = cursorY >= midY
                            dropTargetRequestId    = target.id
                            dropTargetCollectionId = target.parentCollectionId
                        }
                    }
                } else if (activeCollectionId != null) {
                    collectionHitAreas.keys.toList().forEach { id ->
                        collectionHitAreas[id]?.let { collectionHitAreas[id] = it.copy(top = it.top + shift, bottom = it.bottom + shift) }
                    }
                    val target = collectionHitAreas.values
                        .filter { it.id != activeCollectionId }
                        .minByOrNull { kotlin.math.abs(((it.top + it.bottom) / 2f) - cursorY) }
                    if (target != null) {
                        val midY = (target.top + target.bottom) / 2f
                        dropCollectionInsertAfter = cursorY >= midY
                        dropCollectionTargetId    = target.id
                    }
                }
            }
            delay(16L)
        }
    }

    var renameRequestTarget by remember { mutableStateOf<CollectionNode?>(null) }
    var renameRequestValue by remember { mutableStateOf("") }

    var renameCollectionTarget by remember { mutableStateOf<CollectionNode?>(null) }
    var renameCollectionValue by remember { mutableStateOf("") }
    var addFolderTarget by remember { mutableStateOf<CollectionNode?>(null) }
    var addFolderValue by remember { mutableStateOf("") }

    var renameEnvironmentIndex by remember { mutableStateOf(-1) }
    var renameEnvironmentValue by remember { mutableStateOf("") }
    var showCreateEnvironmentDialog by remember { mutableStateOf(false) }
    var createEnvironmentValue by remember { mutableStateOf("") }

    fun launchTracked(
        title: String,
        message: String,
        block: suspend () -> Unit,
    ) {
        val job = scope.launch(ioDispatcher) {
            runCatching { block() }
                .onFailure { e ->
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        state.showError(title = "Import/Export error", message = e.message ?: "Unknown error")
                        state.log("$title failed: ${e.message}", LogLevel.ERROR)
                    }
                }
                .also {
                    withContext(kotlinx.coroutines.Dispatchers.Main) { state.finishOperation() }
                }
        }
        state.startOperation(title = title, message = message, job = job)
    }

    fun persistWorkspaceAsync() {
        scope.launch(ioDispatcher) {
            WorkspaceRepository.save(state)
        }
    }

    Box(
        modifier = Modifier
            .width(state.sidebarWidth.dp)
            .fillMaxHeight()
            .background(ReqLabColors.SurfaceVariant)
            .testTag("sidebar")
            .onGloballyPositioned { coords ->
                val rootPos = coords.positionInRoot()
                sharedSidebarTooltipState.containerRootY = rootPos.y.toInt()
                sidebarTopPx = rootPos.y
                sidebarHeightPx = coords.size.height
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                query = state.sidebarSearchQuery,
                onQueryChanged = { state.sidebarSearchQuery = normalizeSidebarSearchQuery(it) },
                modifier = Modifier.padding(8.dp),
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            ) {
            item {
                SectionHeader(
                    icon = Icons.Default.History,
                    title = Strings.history,
                    expanded = state.historyExpanded,
                    onToggle = { state.historyExpanded = !state.historyExpanded },
                )
            }
            if (state.historyExpanded) {
                // M-4: Filter history items by the sidebar search query so the search
                // bar works consistently across both Collections and History sections.
                val historyQuery = normalizeSidebarSearchQuery(state.sidebarSearchQuery)
                val visibleHistory = if (historyQuery.isBlank()) {
                    state.historyItems
                } else {
                    state.historyItems.filter { item ->
                        item.name.contains(historyQuery, ignoreCase = true) ||
                            item.url.contains(historyQuery, ignoreCase = true)
                    }
                }
                items(visibleHistory, key = { "hist-${it.requestId}-${it.timestamp}" }) { item ->
                    HistoryRow(state = state, item = item, onClick = {
                        state.openHistoryItem(item)
                    })
                }
                item { SectionSpacer() }
            }

            item {
                SectionHeader(
                    icon = Icons.Default.FolderOpen,
                    title = Strings.collections,
                    expanded = state.collectionsExpanded,
                    onToggle = {
                        state.collectionsExpanded = !state.collectionsExpanded
                        state.settings.collectionsExpanded = state.collectionsExpanded
                    },
                    trailing = {
                        Row {
                            IconButton(
                                onClick = {
                                    pickFileForImport { content ->
                                        launchTracked("Importing collection", "Importing collection...") {
                                            val existingIds = state.collections.map { it.id }.toSet()
                                            val importedName = ImportExportRepository.importCollectionFromString(state, content)
                                            state.collections.firstOrNull { it.id !in existingIds }?.let { imported ->
                                                // Collapse the root node and all sub-folders
                                                collapseNodeRecursively(imported, state)
                                            }
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                WorkspaceRepository.save(state)
                                                state.log("Collection imported: $importedName", LogLevel.SUCCESS)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(24.dp).testTag("collection-import-button"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Input,
                                    contentDescription = Strings.importCollection,
                                    tint = ReqLabColors.OnSurfaceDim,
                                    modifier = Modifier.size(14.dp).testTag("collection-import-icon"),
                                )
                            }
                            IconButton(
                                onClick = {
                                    val names = state.collections.map { it.name }.toSet()
                                    val name = ImportExportNaming.generateUniqueCollectionName("New Collection", names)
                                    state.collections.add(
                                        CollectionNode(
                                            id = generateUuid(),
                                            name = name,
                                            isFolder = true,
                                            children = androidx.compose.runtime.mutableStateListOf(),
                                        )
                                    )
                                    state.notifyCollectionsChanged()
                                    persistWorkspaceAsync()
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = Strings.t("new_collection"), tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                )
            }
            if (state.collectionsExpanded) {
                val query = normalizeSidebarSearchQuery(state.sidebarSearchQuery)
                val visibleCollections = if (query.isBlank()) {
                    state.collections
                } else {
                    state.collections.mapNotNull { filterCollectionNode(it, query) }
                }
                visibleCollections.forEach { node ->
                    item(key = node.id) {
                        CollectionTreeNode(
                            node = node,
                            depth = 0,
                            parentCollectionId = null,
                            state = state,
                            onExportCollection = { target ->
                                launchTracked("Exporting collection", "Exporting ${target.name}...") {
                                    val jsonStr = ImportExportRepository.exportCollectionToString(target)
                                    saveFileForExport(jsonStr, "${target.name}.json")
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        state.log("Collection exported: ${target.name}.json", LogLevel.SUCCESS)
                                    }
                                }
                            },
                            onDuplicateCollection = { target ->
                                val existingNames = state.collections.map { it.name }.toSet()
                                val newName = ImportExportNaming.generateUniqueCollectionName(target.name, existingNames)
                                val duplicated = duplicateNode(target, rootNameOverride = newName)
                                state.collections.add(duplicated)
                                state.notifyCollectionsChanged()
                                persistWorkspaceAsync()
                                state.log("Collection duplicated: $newName", LogLevel.SUCCESS)
                            },
                            onRenameCollection = { target ->
                                renameCollectionTarget = target
                                renameCollectionValue = target.name
                            },
                            onDuplicateRequest = { target ->
                                val duplicatedName = duplicateRequestInCollections(state.collections, target.id)
                                if (duplicatedName != null) {
                                    state.notifyCollectionsChanged()
                                    persistWorkspaceAsync()
                                    state.log("Request duplicated: $duplicatedName", LogLevel.SUCCESS)
                                }
                            },
                            onDeleteCollection = { target ->
                                val requestCount = countRequestsInFolder(target)
                                val action = {
                                    val deleted = deleteFolderFromCollections(state.collections, target.id)
                                    if (deleted != null) {
                                        state.notifyCollectionsChanged()
                                        persistWorkspaceAsync()
                                        state.log("Collection deleted: ${target.name}", LogLevel.INFO)
                                    }
                                }
                                if (state.settings.confirmBeforeDelete) {
                                    val message = if (requestCount > 0) {
                                        "Delete \"${target.name}\" and $requestCount request(s)? This cannot be undone."
                                    } else {
                                        "Delete \"${target.name}\"? This cannot be undone."
                                    }
                                    state.showConfirm(
                                        title = "Delete collection?",
                                        message = message,
                                        action = action,
                                    )
                                } else {
                                    action()
                                }
                            },
                            onAddFolder = { target ->
                                addFolderTarget = target
                                addFolderValue = "New Folder"
                            },
                            onAddRequest = { target ->
                                state.addRequestToCollection(target.id)
                                persistWorkspaceAsync()
                            },
                            onRenameRequest = { target ->
                                renameRequestTarget = target
                                renameRequestValue = target.name
                            },
                            onDeleteRequest = { target ->
                                val action = {
                                    deleteRequestFromCollections(state.collections, target.id)
                                    state.notifyCollectionsChanged()
                                    persistWorkspaceAsync()
                                    state.log("Request deleted: ${target.name}", LogLevel.INFO)
                                }
                                if (state.settings.confirmBeforeDelete) {
                                    state.showConfirm(
                                        title = "Delete request?",
                                        message = "Delete \"${target.name}\"? This cannot be undone.",
                                        action = action,
                                    )
                                } else {
                                    action()
                                }
                            },
                            onMoveRequest = { requestId, direction ->
                                if (moveRequestInCollections(state.collections, requestId, direction)) {
                                    state.notifyCollectionsChanged()
                                    persistWorkspaceAsync()
                                }
                            },
                            draggedRequestId = draggedRequestId,
                            dropTargetCollectionId = dropTargetCollectionId,
                            dropTargetRequestId = dropTargetRequestId,
                            dropInsertAfter = dropInsertAfter,
                            onDragStart = { requestId ->
                                draggedRequestId = requestId
                                dragOffsetY = 0f
                                dragCursorAbsY = hitAreas[requestId]?.let { (it.top + it.bottom) / 2f } ?: 0f
                                dropTargetCollectionId = null
                                dropTargetRequestId = null
                                dropInsertAfter = false
                            },
                            onDragDelta = { requestId, deltaY ->
                                if (draggedRequestId == requestId) {
                                    dragOffsetY    += deltaY
                                    dragCursorAbsY += deltaY
                                    // Use absolute cursor position — avoids drift when the list
                                    // has scrolled (baseCenter + offsetY is wrong after a scroll).
                                    val currentY = dragCursorAbsY
                                    val target = hitAreas.values
                                        .filter { it.id != requestId }
                                        .minByOrNull { kotlin.math.abs(((it.top + it.bottom) / 2f) - currentY) }
                                    if (target != null) {
                                        if (target.isFolder) {
                                            dropTargetCollectionId = target.id
                                            dropTargetRequestId    = null
                                            dropInsertAfter        = false
                                        } else {
                                            val midY = (target.top + target.bottom) / 2f
                                            dropInsertAfter        = currentY >= midY
                                            dropTargetRequestId    = target.id
                                            dropTargetCollectionId = target.parentCollectionId
                                        }
                                    }
                                }
                            },
                            onNodePositioned = { area -> hitAreas[area.id] = area },
                            onDragEnd = {
                                dragCursorAbsY = 0f
                                val sourceId = draggedRequestId
                                val targetRequestId = dropTargetRequestId
                                val targetCollectionId = dropTargetCollectionId
                                if (sourceId != null) {
                                    val moved = when {
                                        targetRequestId != null && targetRequestId != sourceId ->
                                            if (dropInsertAfter) {
                                                moveRequestAfterRequest(state.collections, sourceId, targetRequestId)
                                            } else {
                                                moveRequestBeforeRequest(state.collections, sourceId, targetRequestId)
                                            }
                                        targetCollectionId != null ->
                                            moveRequestToCollection(state.collections, sourceId, targetCollectionId)
                                        else -> false
                                    }
                                    if (moved) {
                                        state.notifyCollectionsChanged()
                                        persistWorkspaceAsync()
                                    }
                                }
                                draggedRequestId = null
                                dragOffsetY = 0f
                                dropTargetCollectionId = null
                                dropTargetRequestId = null
                                dropInsertAfter = false
                            },
                            // ── Collection-level drag callbacks ────────────────
                            draggedCollectionId = draggedCollectionId,
                            dropCollectionTargetId = dropCollectionTargetId,
                            dropCollectionInsertAfter = dropCollectionInsertAfter,
                            onCollectionDragStart = { collectionId ->
                                draggedCollectionId = collectionId
                                dragCollectionOffsetY = 0f
                                dragCursorAbsY = collectionHitAreas[collectionId]?.let { (it.top + it.bottom) / 2f } ?: 0f
                                dropCollectionTargetId = null
                                dropCollectionInsertAfter = false
                            },
                            onCollectionDragDelta = { collectionId, deltaY ->
                                if (draggedCollectionId == collectionId) {
                                    dragCollectionOffsetY += deltaY
                                    dragCursorAbsY        += deltaY
                                    val currentY = dragCursorAbsY
                                    val target = collectionHitAreas.values
                                        .filter { it.id != collectionId }
                                        .minByOrNull { kotlin.math.abs(((it.top + it.bottom) / 2f) - currentY) }
                                    if (target != null) {
                                        val midY = (target.top + target.bottom) / 2f
                                        dropCollectionInsertAfter = currentY >= midY
                                        dropCollectionTargetId    = target.id
                                    }
                                }
                            },
                            onCollectionHitAreaPositioned = { area -> collectionHitAreas[area.id] = area },
                            onCollectionDragEnd = {
                                dragCursorAbsY = 0f
                                val sourceId = draggedCollectionId
                                val targetId = dropCollectionTargetId
                                if (sourceId != null && targetId != null) {
                                    val moved = if (dropCollectionInsertAfter) {
                                        moveCollectionAfterCollection(state.collections, sourceId, targetId)
                                    } else {
                                        moveCollectionBeforeCollection(state.collections, sourceId, targetId)
                                    }
                                    if (moved) {
                                        state.notifyCollectionsChanged()
                                        persistWorkspaceAsync()
                                    }
                                }
                                draggedCollectionId = null
                                dragCollectionOffsetY = 0f
                                dropCollectionTargetId = null
                                dropCollectionInsertAfter = false
                            },
                        )
                    }
                }
                item { SectionSpacer() }
            }

            item {
                SectionHeader(
                    icon = Icons.Default.Settings,
                    title = Strings.environments,
                    expanded = state.environmentsExpanded,
                    onToggle = {
                        state.environmentsExpanded = !state.environmentsExpanded
                        state.settings.environmentsExpanded = state.environmentsExpanded
                    },
                    trailing = {
                        Row {
                            IconButton(
                                onClick = {
                                    pickFileForImport { content ->
                                        scope.launch {
                                            launchTracked("Importing environment", "Importing environment...") {
                                                val importedName = ImportExportRepository.importEnvironmentFromString(state, content)
                                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    WorkspaceRepository.save(state)
                                                    state.log("Environment imported: $importedName", LogLevel.SUCCESS)
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(24.dp).testTag("environment-import-button"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Input,
                                    contentDescription = Strings.t("import_environment"),
                                    tint = ReqLabColors.OnSurfaceDim,
                                    modifier = Modifier.size(14.dp).testTag("environment-import-icon"),
                                )
                            }
                            IconButton(
                                onClick = {
                                    createEnvironmentValue = ""
                                    showCreateEnvironmentDialog = true
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = Strings.t("new_environment"), tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                )
            }
            if (state.environmentsExpanded) {
                if (state.environments.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = Strings.noEnvironmentsConfigured,
                                style = MaterialTheme.typography.bodySmall,
                                color = ReqLabColors.OnSurfaceDim,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = Strings.createEnvironment,
                                style = MaterialTheme.typography.bodySmall,
                                color = ReqLabColors.Primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ReqLabColors.Primary.copy(alpha = 0.10f))
                                    .clickable {
                                        createEnvironmentValue = ""
                                        showCreateEnvironmentDialog = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                state.environments.forEachIndexed { index, env ->
                    item(key = "env_$index") {
                        EnvironmentRow(
                            envName = env.name,
                            active = index == state.selectedEnvIndex,
                            onClick = { state.selectedEnvIndex = index },
                            onDoubleClick = {
                                state.selectedEnvIndex = index
                                state.openEnvEdit(index)
                            },
                            onEdit = { state.openEnvEdit(index) },
                            onExport = {
                                scope.launch {
                                    launchTracked("Exporting environment", "Exporting ${env.name}...") {
                                        val jsonStr = ImportExportRepository.exportEnvironmentToString(env)
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            saveFileForExport(jsonStr, "${env.name}.json")
                                            state.log("Environment exported: ${env.name}.json", LogLevel.SUCCESS)
                                        }
                                    }
                                }
                            },
                            onDuplicate = {
                                val names = state.environments.map { it.name }.toSet()
                                val uniqueName = ImportExportNaming.generateUniqueEnvironmentName(env.name, names)
                                val duplicated = EnvState(
                                    name = uniqueName,
                                    variables = env.variables.map { v ->
                                        com.reqlab.ui.shared.state.MutableKeyValue(v.key, v.value, v.enabled, v.secret)
                                    }
                                )
                                state.environments.add(duplicated)
                                state.log("Environment duplicated: $uniqueName", LogLevel.SUCCESS)
                            },
                            onRename = {
                                renameEnvironmentIndex = index
                                renameEnvironmentValue = env.name
                            },
                            onDelete = {
                                val action = {
                                    if (state.environments.isNotEmpty()) {
                                        val deletedName = state.environments[index].name
                                        state.environments.removeAt(index)
                                        state.selectedEnvIndex = if (state.environments.isEmpty()) 0 else state.selectedEnvIndex.coerceIn(0, state.environments.lastIndex)
                                        state.log("Environment deleted: $deletedName", LogLevel.INFO)
                                    }
                                }
                                if (state.settings.confirmBeforeDelete) {
                                    state.showConfirm(
                                        title = "Delete environment?",
                                        message = "Delete \"${env.name}\"? This cannot be undone.",
                                        action = action,
                                    )
                                } else {
                                    action()
                                }
                            },
                        )
                    }
                }
            }
            }
        }
        // Single tooltip overlay — not a Popup, so no hover-exit flicker.
        SidebarTooltipOverlay()
    }

    if (renameCollectionTarget != null) {
        RenameItemDialog(
            title = "Rename folder",
            value = renameCollectionValue,
            onValueChange = { renameCollectionValue = it },
            onDismiss = {
                renameCollectionTarget = null
                renameCollectionValue = ""
            },
            onConfirm = {
                val target = renameCollectionTarget ?: return@RenameItemDialog
                val trimmed = renameCollectionValue.trim()
                if (trimmed.isNotEmpty()) {
                    val renamed = renameFolderInCollections(state.collections, target.id, trimmed)
                    if (renamed) {
                        state.notifyCollectionsChanged()
                        persistWorkspaceAsync()
                    }
                }
                renameCollectionTarget = null
                renameCollectionValue = ""
            },
        )
    }

    if (addFolderTarget != null) {
        RenameItemDialog(
            title = "Add folder",
            value = addFolderValue,
            onValueChange = { addFolderValue = it },
            onDismiss = {
                addFolderTarget = null
                addFolderValue = ""
            },
            onConfirm = {
                val target = addFolderTarget ?: return@RenameItemDialog
                val trimmed = addFolderValue.trim()
                if (trimmed.isNotEmpty()) {
                    val created = addSubfolderInCollections(state.collections, target.id, trimmed)
                    if (created != null) {
                        state.notifyCollectionsChanged()
                        persistWorkspaceAsync()
                        state.log("Folder added: ${created.name}", LogLevel.SUCCESS)
                    }
                }
                addFolderTarget = null
                addFolderValue = ""
            },
        )
    }

    if (renameRequestTarget != null) {
        RenameItemDialog(
            title = "Rename request",
            value = renameRequestValue,
            onValueChange = { renameRequestValue = it },
            onDismiss = {
                renameRequestTarget = null
                renameRequestValue = ""
            },
            onConfirm = {
                val target = renameRequestTarget ?: return@RenameItemDialog
                val trimmed = renameRequestValue.trim()
                if (trimmed.isNotEmpty()) {
                    // Use renameRequestEverywhere so open tabs are also updated (Bug 2 fix)
                    state.renameRequestEverywhere(target.id, trimmed)
                    persistWorkspaceAsync()
                }
                renameRequestTarget = null
                renameRequestValue = ""
            },
        )
    }

    if (renameEnvironmentIndex >= 0) {
        RenameItemDialog(
            title = "Rename environment",
            value = renameEnvironmentValue,
            onValueChange = { renameEnvironmentValue = it },
            onDismiss = {
                renameEnvironmentIndex = -1
                renameEnvironmentValue = ""
            },
            onConfirm = {
                val idx = renameEnvironmentIndex
                if (idx in state.environments.indices) {
                    val trimmed = renameEnvironmentValue.trim()
                    if (trimmed.isNotEmpty()) {
                        val existing = state.environments
                            .filterIndexed { envIndex, _ -> envIndex != idx }
                            .map { it.name }
                            .toSet()
                        state.environments[idx].name = ImportExportNaming.generateUniqueEnvironmentName(trimmed, existing)
                    }
                }
                renameEnvironmentIndex = -1
                renameEnvironmentValue = ""
            },
        )
    }

    if (showCreateEnvironmentDialog) {
        RenameItemDialog(
            title = "Create environment",
            value = createEnvironmentValue,
            onValueChange = { createEnvironmentValue = it },
            confirmText = "Create",
            onDismiss = {
                showCreateEnvironmentDialog = false
                createEnvironmentValue = ""
            },
            onConfirm = {
                val trimmed = createEnvironmentValue.trim()
                if (trimmed.isNotEmpty()) {
                    val existing = state.environments.map { it.name }.toSet()
                    val unique = ImportExportNaming.generateUniqueEnvironmentName(trimmed, existing)
                    state.environments.add(EnvState(unique))
                    state.selectedEnvIndex = state.environments.lastIndex
                    state.log("Environment created: $unique", LogLevel.SUCCESS)
                }
                showCreateEnvironmentDialog = false
                createEnvironmentValue = ""
            },
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ReqLabColors.SurfaceContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier.weight(1f).testTag("sidebar-search-input"),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("${Strings.search}…", color = ReqLabColors.OnSurfaceDim, fontSize = 13.sp)
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear search",
                tint = ReqLabColors.OnSurfaceDim,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = { onQueryChanged("") })
                    .testTag("sidebar-search-clear"),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHovered) ReqLabColors.HoverOverlay else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ReqLabColors.OnSurfaceDim,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(icon, contentDescription = null, tint = ReqLabColors.OnSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = ReqLabColors.OnSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
private fun HistoryRow(state: AppState, item: HistoryItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tooltipIndentPx = with(density) { 28.dp.roundToPx() }

    LaunchedEffect(isHovered) {
        val tooltipId = "history-${item.requestId}-${item.timestamp}"
        if (isHovered) {
            sharedSidebarTooltipState.onHoverEnter(tooltipId, "${item.name}  ${item.url}")
        } else {
            sharedSidebarTooltipState.onHoverExit(tooltipId)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isHovered) ReqLabColors.HoverOverlay else Color.Transparent
            )
            .onGloballyPositioned { coordinates ->
                if (sharedSidebarTooltipState.hoveredItemId == "history-${item.requestId}-${item.timestamp}") {
                    val position = coordinates.positionInRoot()
                    sharedSidebarTooltipState.updateHoverPosition(
                        rowRootY = position.y.toInt(),
                        rowHeightPx = coordinates.size.height,
                        indentXPx = tooltipIndentPx,
                    )
                }
            }
            .hoverable(interactionSource)
            .onPointerEvent(PointerEventType.Press) {
                if (it.buttons.isSecondaryPressed) {
                    showMenu = true
                }
            }
            .clickable(onClick = onClick)
            .testTag("history-row-${item.requestId}")
            .padding(start = 28.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MethodBadge(item.method, compact = true)
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = ReqLabColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // M-6: Show the request timestamp so users can identify when each call was made.
        Text(
            text = formatTimestamp(item.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = ReqLabColors.OnSurfaceDim,
            fontSize = 10.sp,
        )
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(20.dp).testTag("history-actions-${item.requestId}"),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = Strings.t("history_actions"),
                    tint = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(Strings.t("open")) },
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.t("open_in_sidebar")) },
                    onClick = {
                        showMenu = false
                        state.goToCollectionFromHistory(item)
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.t("go_to_collection")) },
                    onClick = {
                        showMenu = false
                        state.goToCollectionFromHistory(item)
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.t("remove_from_history")) },
                    onClick = {
                        showMenu = false
                        state.removeHistoryItem(item.requestId)
                    },
                )
            }
        }
    }
}

@Composable
fun MethodBadge(method: HttpMethodType, compact: Boolean = false) {
    val color = httpMethodColor(method)
    Text(
        text = if (compact) method.name.take(3) else method.name,
        color = color,
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CollectionTreeNode(
    node: CollectionNode,
    depth: Int,
    parentCollectionId: String?,
    state: AppState,
    onExportCollection: (CollectionNode) -> Unit,
    onDuplicateCollection: (CollectionNode) -> Unit,
    onRenameCollection: (CollectionNode) -> Unit,
    onDuplicateRequest: (CollectionNode) -> Unit,
    onDeleteCollection: (CollectionNode) -> Unit,
    onAddFolder: (CollectionNode) -> Unit,
    onAddRequest: (CollectionNode) -> Unit,
    onRenameRequest: (CollectionNode) -> Unit,
    onDeleteRequest: (CollectionNode) -> Unit,
    onMoveRequest: (requestId: String, direction: Int) -> Unit,
    draggedRequestId: String?,
    dropTargetCollectionId: String?,
    dropTargetRequestId: String?,
    dropInsertAfter: Boolean,
    onDragStart: (String) -> Unit,
    onDragDelta: (String, Float) -> Unit,
    onNodePositioned: (TreeNodeHitArea) -> Unit,
    onDragEnd: () -> Unit,
    // ── Collection-level drag params (Bug 3) ──────────────────────────────
    draggedCollectionId: String? = null,
    dropCollectionTargetId: String? = null,
    dropCollectionInsertAfter: Boolean = false,
    onCollectionDragStart: (String) -> Unit = {},
    onCollectionDragDelta: (String, Float) -> Unit = { _, _ -> },
    onCollectionHitAreaPositioned: (TreeNodeHitArea) -> Unit = {},
    onCollectionDragEnd: () -> Unit = {},
) {
    // Read from the session-persistent map rather than a local remember so that
    // Collapse All / Expand All and cross-recomposition state changes take effect.
    val expanded = state.collectionExpandedState[node.id] ?: true
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val indent = (28 + depth * 16).dp
    val isFolderNode = node.isFolder
    val isCollectionRoot = depth == 0 && node.isFolder
    val isRequest = !node.isFolder && node.method != null && node.url != null
    val isSelectedRequest = isRequest && state.selectedRequestId == node.id
    val isDragSource = draggedRequestId == node.id
    val isCollectionDragSource = isCollectionRoot && draggedCollectionId == node.id
    val isDropCollectionTarget = node.isFolder && node.id == dropTargetCollectionId
    val isDropTarget = isRequest && node.id == dropTargetRequestId && node.id != draggedRequestId
    val showInsertionAbove = isDropTarget && !dropInsertAfter
    val showInsertionBelow = isDropTarget && dropInsertAfter
    val showCollectionInsertionAbove = isCollectionRoot && node.id == dropCollectionTargetId && !dropCollectionInsertAfter
    val showCollectionInsertionBelow = isCollectionRoot && node.id == dropCollectionTargetId && dropCollectionInsertAfter

    var showMenu by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val indentPx = with(density) { indent.roundToPx() }

    // Tooltip: communicate hovered item + text to the shared overlay.
    LaunchedEffect(isHovered, isRequest) {
        if (isHovered && isRequest) {
            sharedSidebarTooltipState.onHoverEnter(node.id, node.name)
        } else {
            sharedSidebarTooltipState.onHoverExit(node.id)
        }
    }

    LaunchedEffect(state.sidebarScrollToRequestId, node.id) {
        if (state.sidebarScrollToRequestId == node.id) {
            bringIntoViewRequester.bringIntoView()
            state.sidebarScrollToRequestId = null
        }
    }

    Column {
        // Insertion line ABOVE the row (Postman-style drop indicator for requests)
        if (showInsertionAbove) {
            Box(
                modifier = Modifier
                    .padding(start = indent, end = 4.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ReqLabColors.Primary, RoundedCornerShape(1.dp))
                    .testTag("drop-indicator-above-${node.id}"),
            )
        }
        // Insertion line ABOVE the row for collection drag
        if (showCollectionInsertionAbove) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ReqLabColors.Primary, RoundedCornerShape(1.dp))
                    .testTag("collection-drop-indicator-above-${node.id}"),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .alpha(if (isDragSource || isCollectionDragSource) 0.4f else 1f)
                .bringIntoViewRequester(bringIntoViewRequester)
                .background(
                    when {
                        isSelectedRequest -> ReqLabColors.SelectedItem
                        isDropCollectionTarget -> ReqLabColors.Primary.copy(alpha = 0.14f)
                        isDragSource || isCollectionDragSource -> ReqLabColors.SurfaceHigh
                        isHovered -> ReqLabColors.HoverOverlay
                        else -> Color.Transparent
                    }
                )
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    onNodePositioned(
                        TreeNodeHitArea(
                            id = node.id,
                            isFolder = node.isFolder,
                            parentCollectionId = parentCollectionId,
                            top = position.y,
                            bottom = position.y + coordinates.size.height,
                            label = node.name,
                        )
                    )
                    // Also register as a collection-level hit area when this is a root
                    if (isCollectionRoot) {
                        onCollectionHitAreaPositioned(
                            TreeNodeHitArea(
                                id = node.id,
                                isFolder = true,
                                parentCollectionId = null,
                                top = position.y,
                                bottom = position.y + coordinates.size.height,
                                label = node.name,
                            )
                        )
                    }
                    // Keep tooltip position current (e.g. after scroll).
                    if (sharedSidebarTooltipState.hoveredItemId == node.id) {
                        sharedSidebarTooltipState.updateHoverPosition(
                            rowRootY    = position.y.toInt(),
                            rowHeightPx = coordinates.size.height,
                            indentXPx   = indentPx,
                        )
                    }
                }
                .hoverable(interactionSource)
                .onPointerEvent(PointerEventType.Press) {
                    if (it.buttons.isSecondaryPressed && (isFolderNode || isRequest)) {
                        showMenu = true
                    }
                }
                .pointerInput(node.id, isRequest, isCollectionRoot) {
                    when {
                        isRequest -> detectDragGestures(
                            onDragStart = { onDragStart(node.id) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, _ ->
                                onDragDelta(node.id, change.positionChange().y)
                                change.consume()
                            },
                        )
                        isCollectionRoot -> detectDragGestures(
                            onDragStart = { onCollectionDragStart(node.id) },
                            onDragEnd = { onCollectionDragEnd() },
                            onDragCancel = { onCollectionDragEnd() },
                            onDrag = { change, _ ->
                                onCollectionDragDelta(node.id, change.positionChange().y)
                                change.consume()
                            },
                        )
                    }
                }
                .clickable {
                    if (node.isFolder) {
                        state.collectionExpandedState[node.id] = !expanded
                        if (isCollectionRoot) state.selectedCollectionId = node.id
                    } else if (node.method != null && node.url != null) {
                        state.openRequest(requestId = node.id, name = node.name, method = node.method, url = node.url)
                    }
                }
                .padding(start = indent, end = 8.dp, top = 5.dp, bottom = 5.dp)
                .testTag("collection-node-${node.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (node.isFolder) {
                // Drag indicator for top-level collections (Bug 3)
                if (isCollectionRoot) {
                    Icon(
                        Icons.Default.DragIndicator,
                        contentDescription = Strings.t("drag_to_reorder"),
                        tint = ReqLabColors.OnSurfaceDim.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp).testTag("collection-drag-handle-${node.id}"),
                    )
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
                Icon(
                    imageVector = if (depth == 0) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = ReqLabColors.Tertiary,
                    modifier = Modifier
                        .size(14.dp)
                        .testTag(if (depth == 0) "collection-root-icon-${node.id}" else "collection-subfolder-icon-${node.id}"),
                )
            } else if (node.method != null) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = Strings.t("drag_to_reorder"),
                    tint = ReqLabColors.OnSurfaceDim.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.dp),
                )
                MethodBadge(node.method, compact = true)
            }
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall,
                color = ReqLabColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isSelectedRequest) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ReqLabColors.Primary)
                        .testTag("selected-request-indicator-${node.id}"),
                )
            }

            if (isFolderNode || isRequest) {
            if (isCollectionRoot) {
                IconButton(
                    onClick = { onAddRequest(node) },
                    modifier = Modifier.size(20.dp).testTag("collection-add-${node.id}"),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = Strings.t("add_request"),
                        tint = ReqLabColors.OnSurfaceDim,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(20.dp).testTag("collection-actions-${node.id}"),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = if (isCollectionRoot) Strings.t("collection_actions") else Strings.t("request_actions"),
                        tint = ReqLabColors.OnSurfaceDim,
                        modifier = Modifier.size(14.dp),
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (isFolderNode) {
                        DropdownMenuItem(
                            text = { Text(Strings.t("add_folder")) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onAddFolder(node) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("expand")) },
                            leadingIcon = { Icon(Icons.Default.UnfoldMore, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                state.collectionExpandedState[node.id] = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("collapse")) },
                            leadingIcon = { Icon(Icons.Default.UnfoldLess, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                state.collectionExpandedState[node.id] = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.expandAll) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                state.expandAllCollections()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.collapseAll) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                state.collapseAllCollections()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("add_request")) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onAddRequest(node) },
                        )
                        if (isCollectionRoot) {
                            DropdownMenuItem(
                                text = { Text(Strings.exportCollection) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Input, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = { showMenu = false; onExportCollection(node) },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings.t("duplicate_collection")) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = { showMenu = false; onDuplicateCollection(node) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(Strings.t("rename")) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onRenameCollection(node) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.delete) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onDeleteCollection(node) },
                        )
                    } else if (isRequest) {
                        DropdownMenuItem(
                            text = { Text(Strings.t("duplicate_request")) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onDuplicateRequest(node) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("rename_request")) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onRenameRequest(node) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("move_up")) },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onMoveRequest(node.id, -1) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("move_down")) },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onMoveRequest(node.id, 1) },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.t("delete_request")) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = { showMenu = false; onDeleteRequest(node) },
                        )
                    }
                }
            }
        }
        } // end Row

        // Insertion line BELOW the row (Postman-style drop indicator for requests)
        if (showInsertionBelow) {
            Box(
                modifier = Modifier
                    .padding(start = indent, end = 4.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ReqLabColors.Primary, RoundedCornerShape(1.dp))
                    .testTag("drop-indicator-below-${node.id}"),
            )
        }
        // Insertion line BELOW the row for collection drag
        if (showCollectionInsertionBelow) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ReqLabColors.Primary, RoundedCornerShape(1.dp))
                    .testTag("collection-drop-indicator-below-${node.id}"),
            )
        }

        AnimatedVisibility(
            visible = node.isFolder && expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                node.children.forEach { child ->
                    CollectionTreeNode(
                        node = child,
                        depth = depth + 1,
                        parentCollectionId = if (node.isFolder) node.id else parentCollectionId,
                        state = state,
                        onExportCollection = onExportCollection,
                        onDuplicateCollection = onDuplicateCollection,
                        onRenameCollection = onRenameCollection,
                        onDuplicateRequest = onDuplicateRequest,
                        onDeleteCollection = onDeleteCollection,
                        onAddRequest = onAddRequest,
                        onRenameRequest = onRenameRequest,
                        onDeleteRequest = onDeleteRequest,
                        onAddFolder = onAddFolder,
                        onMoveRequest = onMoveRequest,
                        draggedRequestId = draggedRequestId,
                        dropTargetCollectionId = dropTargetCollectionId,
                        dropTargetRequestId = dropTargetRequestId,
                        dropInsertAfter = dropInsertAfter,
                        onDragStart = onDragStart,
                        onDragDelta = onDragDelta,
                        onNodePositioned = onNodePositioned,
                        onDragEnd = onDragEnd,
                        // Collection drag params are not forwarded to children
                        // since only depth==0 nodes participate in collection reorder
                    )
                }
            }
        }
    } // end outer Column
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EnvironmentRow(
    envName: String,
    active: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }

    var envRowRootY  by remember { mutableStateOf(0) }
    var envRowHeight by remember { mutableStateOf(0) }
    val envDensity   = androidx.compose.ui.platform.LocalDensity.current
    val envIndentPx  = with(envDensity) { 28.dp.roundToPx() }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            sharedSidebarTooltipState.onHoverEnter("env-$envName", envName)
            sharedSidebarTooltipState.updateHoverPosition(
                rowRootY    = envRowRootY,
                rowHeightPx = envRowHeight,
                indentXPx   = envIndentPx,
            )
        } else {
            sharedSidebarTooltipState.onHoverExit("env-$envName")
        }
    }

    val rowBackground by animateColorAsState(
        targetValue = when {
            active -> ReqLabColors.SelectedItem
            isHovered -> ReqLabColors.HoverOverlay
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "envRowBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(rowBackground)
            .hoverable(interactionSource)
            .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) showMenu = true }
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
            .padding(start = 28.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
            .testTag("env-row-$envName")
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                envRowRootY  = pos.y.toInt()
                envRowHeight = coords.size.height
                if (sharedSidebarTooltipState.hoveredItemId == "env-$envName") {
                    sharedSidebarTooltipState.updateHoverPosition(
                        rowRootY    = pos.y.toInt(),
                        rowHeightPx = coords.size.height,
                        indentXPx   = envIndentPx,
                    )
                }
            },

        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (active) ReqLabColors.Secondary else ReqLabColors.OnSurfaceDim),
        )
        Text(
            text = envName,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) ReqLabColors.OnSurface else ReqLabColors.OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("env-name-text-$envName"),
        )
        Box(modifier = Modifier.width(24.dp)) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(24.dp)
                    .alpha(if (isHovered || active || showMenu) 1f else 0f)
                    .testTag("env-actions-$envName"),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = Strings.t("environment_actions"),
                    tint = ReqLabColors.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(Strings.t("edit_environment")) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                    showMenu = false
                    onEdit()
                })
                DropdownMenuItem(
                    text = { Text(Strings.t("export_environment")) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Input, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                    showMenu = false
                    onExport()
                })
                DropdownMenuItem(
                    text = { Text(Strings.t("duplicate_environment")) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                    showMenu = false
                    onDuplicate()
                })
                DropdownMenuItem(
                    text = { Text(Strings.t("rename_environment")) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                    showMenu = false
                    onRename()
                })
                DropdownMenuItem(
                    text = { Text(Strings.t("delete_environment")) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                    showMenu = false
                    onDelete()
                })
            }
        }
    }
}

@Composable
private fun RenameItemDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 560.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = ReqLabColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
                cursorBrush = SolidColor(ReqLabColors.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ReqLabColors.SurfaceContainer)
                    .border(1.dp, ReqLabColors.Border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("rename-dialog-input"),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Text(
                    text = Strings.cancel,
                    color = ReqLabColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ReqLabColors.SurfaceContainer)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("rename-dialog-cancel"),
                )
                Text(
                    text = confirmText,
                    color = ReqLabColors.OnPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ReqLabColors.Primary)
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("rename-dialog-save"),
                )
            }
        }
    }
}

private fun duplicateNode(node: CollectionNode, rootNameOverride: String? = null): CollectionNode {
    // Use copy() so every field (including future additions) is guaranteed to be duplicated.
    // Only the id and name are different; children are recursively duplicated with new ids.
    val duplicatedChildren = node.children.map { duplicateNode(it) }
    return node.copy(
        id = generateUuid(),
        name = rootNameOverride ?: node.name,
        children = androidx.compose.runtime.mutableStateListOf<CollectionNode>().also { it.addAll(duplicatedChildren) },
    )
}

fun filterCollectionNode(node: CollectionNode, query: String): CollectionNode? {
    val loweredQuery = query.trim().lowercase()
    if (loweredQuery.isEmpty()) return node

    val selfMatches = node.name.lowercase().contains(loweredQuery) ||
        (node.url?.lowercase()?.contains(loweredQuery) == true)

    // If the folder/collection name itself matches, show it with ALL of its children
    // so the user can see every request inside the matched container.
    if (selfMatches && node.isFolder) return node

    val filteredChildren = node.children.mapNotNull { filterCollectionNode(it, loweredQuery) }.toMutableList()
    return if (selfMatches || filteredChildren.isNotEmpty()) {
        node.copy(children = filteredChildren)
    } else {
        null
    }
}

fun duplicateRequestInCollections(collections: MutableList<CollectionNode>, requestId: String): String? {
    return duplicateRequestInNodeList(collections, requestId)
}

private fun duplicateRequestInNodeList(nodes: MutableList<CollectionNode>, requestId: String): String? {
    nodes.indices.forEach { index ->
        val node = nodes[index]
        if (!node.isFolder && node.id == requestId) {
            val siblingNames = nodes.map { it.name }.toSet()
            val duplicateName = generateUniqueNodeName("${node.name} Copy", siblingNames)
            val duplicated = duplicateNode(node, rootNameOverride = duplicateName)
            nodes.add(index + 1, duplicated)
            return duplicateName
        }
        if (node.children.isNotEmpty()) {
            val duplicatedName = duplicateRequestInNodeList(node.children, requestId)
            if (duplicatedName != null) return duplicatedName
        }
    }
    return null
}

private fun generateUniqueNodeName(base: String, existingNames: Set<String>): String {
    if (base !in existingNames) return base
    var index = 2
    while ("$base $index" in existingNames) {
        index++
    }
    return "$base $index"
}



@Composable
private fun SectionSpacer() {
    Spacer(Modifier.height(8.dp))
}

fun deleteRequestFromCollections(collections: MutableList<CollectionNode>, requestId: String): Boolean {
    val iterator = collections.iterator()
    while (iterator.hasNext()) {
        val node = iterator.next()
        if (!node.isFolder && node.id == requestId) {
            iterator.remove()
            return true
        }
        if (node.children.isNotEmpty()) {
            if (deleteRequestFromCollections(node.children, requestId)) return true
        }
    }
    return false
}

fun renameRequestInCollections(collections: MutableList<CollectionNode>, requestId: String, newName: String) {
    collections.forEachIndexed { index, node ->
        if (!node.isFolder && node.id == requestId) {
            collections[index] = node.copy(name = newName)
            return
        }
        if (node.children.isNotEmpty()) {
            renameRequestInCollections(node.children, requestId, newName)
        }
    }
}

fun addSubfolderInCollections(
    collections: MutableList<CollectionNode>,
    parentFolderId: String,
    folderName: String,
): CollectionNode? {
    val parent = findCollectionById(collections, parentFolderId) ?: return null
    if (!parent.isFolder) return null
    val siblingNames = parent.children.filter { it.isFolder }.map { it.name }.toSet()
    val uniqueName = ImportExportNaming.generateUniqueCollectionName(folderName, siblingNames)
    val node = CollectionNode(
        id = generateUuid(),
        name = uniqueName,
        isFolder = true,
        children = androidx.compose.runtime.mutableStateListOf(),
    )
    parent.children.add(node)
    return node
}

fun renameFolderInCollections(
    collections: MutableList<CollectionNode>,
    folderId: String,
    newName: String,
): Boolean {
    return renameFolderInNodeList(collections, folderId, newName)
}

private fun renameFolderInNodeList(
    nodes: MutableList<CollectionNode>,
    folderId: String,
    newName: String,
): Boolean {
    val index = nodes.indexOfFirst { it.isFolder && it.id == folderId }
    if (index >= 0) {
        val siblings = nodes.filterIndexed { i, n -> i != index && n.isFolder }.map { it.name }.toSet()
        val uniqueName = ImportExportNaming.generateUniqueCollectionName(newName, siblings)
        nodes[index] = nodes[index].copy(name = uniqueName)
        return true
    }
    nodes.forEach { node ->
        if (node.isFolder && node.children.isNotEmpty()) {
            if (renameFolderInNodeList(node.children, folderId, newName)) return true
        }
    }
    return false
}

fun deleteFolderFromCollections(
    collections: MutableList<CollectionNode>,
    folderId: String,
): CollectionNode? = deleteFolderFromNodeList(collections, folderId)

private fun deleteFolderFromNodeList(
    nodes: MutableList<CollectionNode>,
    folderId: String,
): CollectionNode? {
    val index = nodes.indexOfFirst { it.isFolder && it.id == folderId }
    if (index >= 0) return nodes.removeAt(index)
    nodes.forEach { node ->
        if (node.isFolder && node.children.isNotEmpty()) {
            val deleted = deleteFolderFromNodeList(node.children, folderId)
            if (deleted != null) return deleted
        }
    }
    return null
}

fun countRequestsInFolder(folder: CollectionNode): Int {
    if (!folder.isFolder) return 0
    return folder.children.sumOf { child ->
        when {
            child.isFolder -> countRequestsInFolder(child)
            else -> 1
        }
    }
}

/** Move a request up (direction = -1) or down (direction = 1) within its parent list. */
fun moveRequestInCollections(collections: MutableList<CollectionNode>, requestId: String, direction: Int): Boolean {
    val index = collections.indexOfFirst { !it.isFolder && it.id == requestId }
    if (index >= 0) {
        val targetIndex = index + direction
        if (targetIndex in collections.indices) {
            val item = collections.removeAt(index)
            collections.add(targetIndex, item)
            return true
        }
        return false
    }
    for (node in collections) {
        if (node.children.isNotEmpty()) {
            if (moveRequestInCollections(node.children, requestId, direction)) return true
        }
    }
    return false
}

fun moveRequestBeforeRequest(
    collections: MutableList<CollectionNode>,
    requestId: String,
    targetRequestId: String,
): Boolean {
    val extracted = extractRequestNode(collections, requestId) ?: return false
    val target = findRequestParentAndIndex(collections, targetRequestId) ?: return false
    val insertIndex = target.second.coerceIn(0, target.first.size)
    target.first.add(insertIndex, extracted)
    return true
}

fun moveRequestAfterRequest(
    collections: MutableList<CollectionNode>,
    requestId: String,
    targetRequestId: String,
): Boolean {
    val extracted = extractRequestNode(collections, requestId) ?: return false
    val target = findRequestParentAndIndex(collections, targetRequestId) ?: return false
    // After extraction the target index may shift by 1 if the source was before the target in the same list
    val insertIndex = (target.second + 1).coerceIn(0, target.first.size)
    target.first.add(insertIndex, extracted)
    return true
}

fun moveRequestToCollection(
    collections: MutableList<CollectionNode>,
    requestId: String,
    targetCollectionId: String,
): Boolean {
    val extracted = extractRequestNode(collections, requestId) ?: return false
    val targetCollection = findCollectionById(collections, targetCollectionId) ?: return false
    targetCollection.children.add(extracted)
    return true
}

private fun extractRequestNode(nodes: MutableList<CollectionNode>, requestId: String): CollectionNode? {
    val index = nodes.indexOfFirst { !it.isFolder && it.id == requestId }
    if (index >= 0) {
        return nodes.removeAt(index)
    }
    for (node in nodes) {
        if (node.children.isNotEmpty()) {
            val extracted = extractRequestNode(node.children, requestId)
            if (extracted != null) return extracted
        }
    }
    return null
}

private fun findRequestParentAndIndex(
    nodes: MutableList<CollectionNode>,
    requestId: String,
): Pair<MutableList<CollectionNode>, Int>? {
    val index = nodes.indexOfFirst { !it.isFolder && it.id == requestId }
    if (index >= 0) return nodes to index
    for (node in nodes) {
        if (node.children.isNotEmpty()) {
            val found = findRequestParentAndIndex(node.children, requestId)
            if (found != null) return found
        }
    }
    return null
}

private fun findCollectionById(nodes: MutableList<CollectionNode>, collectionId: String): CollectionNode? {
    nodes.forEach { node ->
        if (node.isFolder && node.id == collectionId) {
            return node
        }
        if (node.children.isNotEmpty()) {
            val child = findCollectionById(node.children, collectionId)
            if (child != null) return child
        }
    }
    return null
}

/**
 * Recursively sets the expanded state to false for a node and all its
 * folder children, so imported collections appear fully collapsed.
 */
private fun collapseNodeRecursively(node: CollectionNode, state: AppState) {
    if (node.isFolder) {
        state.collectionExpandedState[node.id] = false
        for (child in node.children) {
            collapseNodeRecursively(child, state)
        }
    }
}

// ── Collection-level drag-to-reorder helpers (Bug 3) ──────────────────────────

/**
 * Moves the collection identified by [collectionId] to be immediately BEFORE
 * the collection identified by [targetCollectionId] in [collections].
 * Returns true if the move was performed.
 */
fun moveCollectionBeforeCollection(
    collections: MutableList<CollectionNode>,
    collectionId: String,
    targetCollectionId: String,
): Boolean {
    val fromIndex = collections.indexOfFirst { it.id == collectionId }
    val toIndex   = collections.indexOfFirst { it.id == targetCollectionId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return false
    val item = collections.removeAt(fromIndex)
    val insertIndex = if (fromIndex < toIndex) toIndex - 1 else toIndex
    collections.add(insertIndex, item)
    return true
}

/**
 * Moves the collection identified by [collectionId] to be immediately AFTER
 * the collection identified by [targetCollectionId] in [collections].
 * Returns true if the move was performed.
 */
fun moveCollectionAfterCollection(
    collections: MutableList<CollectionNode>,
    collectionId: String,
    targetCollectionId: String,
): Boolean {
    val fromIndex = collections.indexOfFirst { it.id == collectionId }
    val toIndex   = collections.indexOfFirst { it.id == targetCollectionId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return false
    val item = collections.removeAt(fromIndex)
    val insertIndex = if (fromIndex < toIndex) toIndex else toIndex + 1
    collections.add(insertIndex, item)
    return true
}
