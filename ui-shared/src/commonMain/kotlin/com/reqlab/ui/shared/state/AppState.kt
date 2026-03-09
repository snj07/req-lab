package com.reqlab.ui.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.ResponseDefinition
import kotlinx.coroutines.Job
import com.reqlab.ui.shared.platform.generateUuid
import com.reqlab.ui.shared.platform.currentTimeMillis

import com.reqlab.ui.shared.i18n.AppLanguage

// ── Enumerations ────────────────────────────────────────────────

enum class RequestEditorTab(val label: String) {
    PARAMS("Params"), HEADERS("Headers"), BODY("Body"),
    AUTH("Auth"), PRE_REQUEST("Pre-request"), TESTS("Tests")
}

enum class ResponseTab(val label: String) {
    BODY("Body"), HEADERS("Headers"), COOKIES("Cookies"), TIMING("Timing"), RAW("Raw")
}

enum class BottomTab(val label: String) {
    CONSOLE("Console"), TEST_RESULTS("Test Results"), LOGS("Logs")
}

enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

enum class AppTheme { DARK, LIGHT, SYSTEM }
enum class ResponseLayout { RIGHT, BOTTOM }

/** Top-level workspace mode — determines which main panel is shown. */
enum class WorkspaceMode(val label: String) {
    HTTP("HTTP"),
    REALTIME("Realtime"),
    GRAPHQL("GraphQL"),
}

enum class HeaderKind { SYSTEM, USER }

// ── Data holders ────────────────────────────────────────────────

data class ConsoleEntry(
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val timestamp: Long = currentTimeMillis(),
)

data class TestResultEntry(val name: String, val passed: Boolean, val message: String = "")

data class HistoryItem(
    val id: String,
    val method: HttpMethodType,
    val name: String,
    val url: String,
    val timestamp: Long,
)

data class CollectionNode(
    val id: String,
    val name: String,
    val isFolder: Boolean = false,
    val method: HttpMethodType? = null,
    val url: String? = null,
    val children: MutableList<CollectionNode> = mutableStateListOf(),
    val preRequestScript: String? = null,
    val testScript: String? = null,
    // Request configuration populated from collection import
    val userHeaders: List<Pair<String, String>> = emptyList(),
    val bodyType: BodyType? = null,
    val bodyContent: String? = null,
    val authType: AuthType? = null,
    val authUsername: String? = null,
    val authPassword: String? = null,
    val authToken: String? = null,
    val authApiKey: String? = null,
    val authApiValue: String? = null,
)

/** Mutable key-value pair used in param / header / variable editors. */
class MutableKeyValue(
    key: String = "",
    value: String = "",
    enabled: Boolean = true,
    secret: Boolean = false,
    kind: HeaderKind = HeaderKind.USER,
    keyLocked: Boolean = false,
    /** Stable unique identifier used as LazyColumn item key (fixes M-7 index-key bug). */
    val uid: String = generateUuid(),
) {
    var key     by mutableStateOf(key)
    var value   by mutableStateOf(value)
    var enabled by mutableStateOf(enabled)
    var secret  by mutableStateOf(secret)
    var kind    by mutableStateOf(kind)
    var keyLocked by mutableStateOf(keyLocked)
}

object SystemHeaderRules {
    const val CONTENT_TYPE = "Content-Type"
    const val ACCEPT = "Accept"
    const val USER_AGENT = "User-Agent"

    val nonDeletableKeys: Set<String> = setOf(CONTENT_TYPE, ACCEPT, USER_AGENT)

    fun isSystemHeader(key: String): Boolean = key in nonDeletableKeys

    fun defaultContentTypeFor(bodyType: BodyType): String = when (bodyType) {
        BodyType.JSON -> "application/json"
        BodyType.FORM_DATA -> "multipart/form-data"
        BodyType.X_WWW_FORM_URLENCODED -> "application/x-www-form-urlencoded"
        BodyType.GRAPHQL -> "application/json"
        BodyType.RAW_TEXT -> "text/plain"
        BodyType.BINARY -> "application/octet-stream"
        BodyType.NONE -> "application/json"
    }
}

// ── Environment model ───────────────────────────────────────────

class EnvState(
    name: String,
    variables: List<MutableKeyValue> = emptyList(),
) {
    var name by mutableStateOf(name)
    val variables = mutableStateListOf<MutableKeyValue>().also { it.addAll(variables) }

    /** Produce a flat variable map for resolution (e.g. {{baseUrl}} → value). */
    fun toVariableMap(): Map<String, String> =
        variables.filter { it.enabled }.associate { it.key to it.value }
}

// ── Settings model ──────────────────────────────────────────────

class AppSettings {
    // General
    var autoSaveRequests     by mutableStateOf(true)
    var confirmBeforeDelete  by mutableStateOf(true)
    var defaultTimeoutSec    by mutableStateOf(30)
    var responseLayout       by mutableStateOf(ResponseLayout.RIGHT)

    // Theme
    var theme by mutableStateOf(AppTheme.DARK)

    // Language (i18n)
    var language by mutableStateOf(AppLanguage.EN)

    // Network
    var requestTimeoutSec    by mutableStateOf(30)
    var followRedirects      by mutableStateOf(true)

    // Proxy
    var httpProxy            by mutableStateOf("")
    var httpsProxy           by mutableStateOf("")
    var proxyEnabled         by mutableStateOf(false)
}

// ── Per-tab state (one per open request tab) ────────────────────

class RequestTabState(
    val id: String = generateUuid(),
    name: String = "Untitled",
    method: HttpMethodType = HttpMethodType.GET,
    url: String = "",
    val collectionName: String? = null,
    val folderPath: List<String> = emptyList(),
) {
    val requestId: String get() = id

    var name     by mutableStateOf(name)
    var method   by mutableStateOf(method)
    var url      by mutableStateOf(url)
    var isDirty  by mutableStateOf(false)
    var lastSavedTimestamp by mutableStateOf<Long?>(null)
    private var savedSnapshot by mutableStateOf("")

    var selectedEditorTab by mutableStateOf(RequestEditorTab.PARAMS)

    val params  = mutableStateListOf<MutableKeyValue>()
    val headers = mutableStateListOf(
        MutableKeyValue(SystemHeaderRules.CONTENT_TYPE, "application/json", kind = HeaderKind.SYSTEM, keyLocked = true),
        MutableKeyValue(SystemHeaderRules.ACCEPT, "application/json", kind = HeaderKind.SYSTEM, keyLocked = true),
        MutableKeyValue(SystemHeaderRules.USER_AGENT, "ReqLab/1.0", kind = HeaderKind.SYSTEM, keyLocked = true),
    )

    var bodyType    by mutableStateOf(BodyType.JSON)
    var bodyContent by mutableStateOf("")

    var authType by mutableStateOf(AuthType.NONE)
    var authUsername  by mutableStateOf("")
    var authPassword  by mutableStateOf("")
    var authToken     by mutableStateOf("")
    var authApiKey    by mutableStateOf("")
    var authApiValue  by mutableStateOf("")

    var preRequestScript by mutableStateOf("")
    var testScript       by mutableStateOf("")

    var retryCount by mutableStateOf(1)
    var retryDelayMs by mutableStateOf(250L)

    /**
     * Tracks the set of variable keys injected by the pre-request script.
     * Cleared and repopulated on every send so stale script-set variables
     * are removed before the script re-executes (fixes M-8).
     */
    val scriptInjectedVarKeys: MutableSet<String> = mutableSetOf()

    /**
     * The coroutine Job for the currently in-flight HTTP request.
     * Cancelling this job aborts the request (fixes H-1 race condition).
     */
    @Volatile var currentJob: Job? = null

    // response associated with this tab
    var response    by mutableStateOf<ResponseDefinition?>(null)
    var responseTab by mutableStateOf(ResponseTab.BODY)
    var isLoading   by mutableStateOf(false)

    // last error message (displayed in response panel when response is null + error)
    var lastError   by mutableStateOf<String?>(null)

    init {
        savedSnapshot = currentSnapshot()
    }

    private fun currentSnapshot(): String {
        val paramsSnapshot = params.joinToString(";") { p -> "${p.key}|${p.value}|${p.enabled}|${p.secret}" }
        val headersSnapshot = headers.joinToString(";") { h -> "${h.key}|${h.value}|${h.enabled}|${h.secret}|${h.kind}|${h.keyLocked}" }
        return listOf(
            name,
            method.name,
            url,
            bodyType.name,
            bodyContent,
            authType.name,
            authUsername,
            authPassword,
            authToken,
            authApiKey,
            authApiValue,
            preRequestScript,
            testScript,
            retryCount.toString(),
            retryDelayMs.toString(),
            paramsSnapshot,
            headersSnapshot,
        ).joinToString("#")
    }

    fun currentSnapshotForPersistence(): String = currentSnapshot()

    fun savedSnapshotForPersistence(): String = savedSnapshot

    fun restoreSavedSnapshot(snapshot: String?, legacyDirtyFlag: Boolean = false) {
        savedSnapshot = when {
            snapshot != null -> snapshot
            legacyDirtyFlag -> "__legacy-dirty__"
            else -> currentSnapshot()
        }
        recomputeDirty()
    }

    fun recomputeDirty() {
        isDirty = currentSnapshot() != savedSnapshot
    }

    fun markDirty() {
        recomputeDirty()
    }

    fun markSaved() {
        savedSnapshot = currentSnapshot()
        isDirty = false
        lastSavedTimestamp = currentTimeMillis()
    }

    fun syncSystemHeaders() {
        upsertSystemHeader(SystemHeaderRules.CONTENT_TYPE, SystemHeaderRules.defaultContentTypeFor(bodyType))
        upsertSystemHeader(SystemHeaderRules.ACCEPT, "application/json")
        upsertSystemHeader(SystemHeaderRules.USER_AGENT, "ReqLab/1.0")
    }

    private fun upsertSystemHeader(key: String, value: String) {
        val existing = headers.indexOfFirst { it.key.equals(key, ignoreCase = true) }
        if (existing >= 0) {
            headers[existing].key = key
            headers[existing].value = value
            headers[existing].kind = HeaderKind.SYSTEM
            headers[existing].keyLocked = true
            return
        }
        headers.add(
            MutableKeyValue(
                key = key,
                value = value,
                enabled = true,
                kind = HeaderKind.SYSTEM,
                keyLocked = true,
            )
        )
    }
}

// ── Global application state ────────────────────────────────────

class AppState(openDefaultTab: Boolean = true) {
    // ── workspace mode ──
    var workspaceMode by mutableStateOf(WorkspaceMode.HTTP)

    // ── sidebar ────────
    var sidebarExpanded      by mutableStateOf(true)
    var sidebarWidth         by mutableStateOf(260f)
    var historyExpanded      by mutableStateOf(true)
    var collectionsExpanded  by mutableStateOf(true)
    var environmentsExpanded by mutableStateOf(true)
    var sidebarSearchQuery   by mutableStateOf("")
    var selectedCollectionId by mutableStateOf<String?>(null)
    var selectedRequestId    by mutableStateOf<String?>(null)
    var sidebarScrollToRequestId by mutableStateOf<String?>(null)
    var collectionsRevision  by mutableStateOf(0)

    /** Per-folder expanded state. Absent key → expanded (true) by default. */
    val collectionExpandedState = mutableStateMapOf<String, Boolean>()

    // ── split fractions ─
    var requestResponseSplit by mutableStateOf(0.50f)
    var mainVerticalSplit    by mutableStateOf(0.73f)   // main area / bottom panel

    // ── tabs ───────────
    val openTabs       = mutableStateListOf<RequestTabState>().also {
        if (openDefaultTab) it.add(RequestTabState())
    }
    var activeTabIndex by mutableStateOf(if (openDefaultTab) 0 else -1)
    val activeTab: RequestTabState? get() = openTabs.getOrNull(activeTabIndex)

    // ── bottom panel ──
    var selectedBottomTab    by mutableStateOf(BottomTab.CONSOLE)
    var bottomPanelExpanded  by mutableStateOf(true)
    var bottomPanelHeight    by mutableStateOf(200f)
    val consoleLogs  = mutableStateListOf<ConsoleEntry>()
    /** Structured network-event log shown in the Logs tab (fixes M-3). */
    val networkEventLogs = mutableStateListOf<ConsoleEntry>()
    val testResults  = mutableStateListOf<TestResultEntry>()

    // ── dialogs / overlays ──
    var showSettingsDialog   by mutableStateOf(false)
    var showEnvEditDialog    by mutableStateOf(false)
    var editingEnvIndex      by mutableStateOf(-1)      // index into environments

    // ── import/export operation dialog ──
    var showOperationDialog  by mutableStateOf(false)
    var operationTitle       by mutableStateOf("")
    var operationMessage     by mutableStateOf("")
    private var operationJob: Job? = null

    // ── error dialog ──
    var showErrorDialog      by mutableStateOf(false)
    var errorDialogTitle     by mutableStateOf("Operation failed")
    var errorDialogMessage   by mutableStateOf("")

    // ── confirm-delete dialog ──
    var showConfirmDialog      by mutableStateOf(false)
    var confirmDialogTitle     by mutableStateOf("")
    var confirmDialogMessage   by mutableStateOf("")
    var pendingConfirmAction: (() -> Unit)? = null

    /** Show a [ConfirmDeleteDialog] and run [action] if the user confirms. */
    fun showConfirm(title: String, message: String, action: () -> Unit) {
        confirmDialogTitle   = title
        confirmDialogMessage = message
        pendingConfirmAction = action
        showConfirmDialog    = true
    }
    fun resolveConfirm(confirmed: Boolean) {
        showConfirmDialog = false
        if (confirmed) pendingConfirmAction?.invoke()
        pendingConfirmAction = null
    }

    fun startOperation(title: String, message: String, job: Job) {
        operationTitle = title
        operationMessage = message
        operationJob = job
        showOperationDialog = true
    }

    fun finishOperation() {
        showOperationDialog = false
        operationJob = null
    }

    fun cancelOperation() {
        operationJob?.cancel()
        finishOperation()
        log("Operation canceled", LogLevel.WARNING)
    }

    fun showError(title: String, message: String) {
        errorDialogTitle = title
        errorDialogMessage = message
        showErrorDialog = true
    }

    fun dismissError() {
        showErrorDialog = false
    }

    // ── environment ──
    var selectedEnvIndex by mutableStateOf(0)
    val environments = mutableStateListOf(
        EnvState("Development", listOf(
            MutableKeyValue("baseUrl",   "http://localhost:8080"),
            MutableKeyValue("authToken", "dev-token-1234"),
        )),
        EnvState("Staging", listOf(
            MutableKeyValue("baseUrl",   "https://staging.api.example.com"),
            MutableKeyValue("authToken", "stg-token-abcd"),
        )),
        EnvState("Production", listOf(
            MutableKeyValue("baseUrl",   "https://api.example.com"),
            MutableKeyValue("authToken", "prod-token-xyz", secret = true),
        )),
    )

    // ── global variables (lowest priority, overridden by environment + local) ──
    val globalVariables = mutableStateListOf(
        MutableKeyValue("appName", "ReqLab"),
        MutableKeyValue("apiVersion", "v1"),
    )
    var showGlobalVariablesDialog by mutableStateOf(false)

    val selectedEnvironment: EnvState get() = environments.getOrElse(selectedEnvIndex) { environments.first() }

    /**
     * Variable layers for the active environment (used in request variable resolution).
     * Resolution priority (first match wins): Environment → Global.
     * Environment variables override global variables.
     */
    fun activeVariableLayers(): List<Map<String, String>> =
        listOf(
            selectedEnvironment.toVariableMap(),
            globalVariables.filter { it.enabled }.associate { it.key to it.value },
        )

    /**
     * Merges variables set by a pre-request or test script into the active environment.
     * Existing variables with the same key are updated; new keys are appended.
     */
    fun mergeScriptVariables(vars: Map<String, String>) {
        val env = selectedEnvironment
        vars.forEach { (key, value) ->
            val existing = env.variables.firstOrNull { it.key == key }
            if (existing != null) {
                existing.value = value
            } else {
                env.variables.add(MutableKeyValue(key = key, value = value))
            }
        }
    }

    // ── settings ────────
    val settings = AppSettings()

    // ── demo / history data ──
    val historyItems = mutableStateListOf(
        HistoryItem("h1", HttpMethodType.GET,    "List users",  "http://localhost:8080/users",     currentTimeMillis() - 300_000),
        HistoryItem("h2", HttpMethodType.POST,   "Create user", "http://localhost:8080/users",     currentTimeMillis() - 600_000),
        HistoryItem("h3", HttpMethodType.DELETE, "Delete user", "http://localhost:8080/users/1",   currentTimeMillis() - 900_000),
    )

    val collections = mutableStateListOf(
        CollectionNode("c1", "Users API", isFolder = true, children = mutableStateListOf(
            CollectionNode("r1", "Get all users", method = HttpMethodType.GET,  url = "{{baseUrl}}/users"),
            CollectionNode("r2", "Create user",   method = HttpMethodType.POST, url = "{{baseUrl}}/users"),
            CollectionNode("r3", "Update user",   method = HttpMethodType.PUT,  url = "{{baseUrl}}/users/1"),
        )),
        CollectionNode("c2", "Auth", isFolder = true, children = mutableStateListOf(
            CollectionNode("r4", "Login",   method = HttpMethodType.POST, url = "{{baseUrl}}/auth/login"),
            CollectionNode("r5", "Refresh", method = HttpMethodType.POST, url = "{{baseUrl}}/auth/refresh"),
        )),
    )

    // ── actions ──────────────────────────────────────────────────

    fun addTab(
        requestId: String = generateUuid(),
        name: String = "Untitled ${openTabs.size + 1}",
        method: HttpMethodType = HttpMethodType.GET,
        url: String = "",
    ) {
        openTabs.indexOfFirst { it.id == requestId }.takeIf { it >= 0 }?.let { existingIdx ->
            activeTabIndex = existingIdx
            selectedRequestId = requestId
            return
        }
        var cName: String? = null
        val fPath = mutableListOf<String>()
        var found = false
        for (c in collections) {
            if (findPathRecursive(c, requestId, fPath)) {
                cName = c.name
                found = true
                break
            }
        }
        val pathWithoutNode = if (found && fPath.isNotEmpty()) fPath.dropLast(1) else emptyList()
        val node = findNodeById(collections, requestId)
        val tab = RequestTabState(id = requestId, name = name, method = method, url = url, collectionName = cName, folderPath = pathWithoutNode)
        node?.preRequestScript?.takeIf { it.isNotBlank() }?.let { tab.preRequestScript = it }
        node?.testScript?.takeIf { it.isNotBlank() }?.let { tab.testScript = it }
        // Populate body, headers, and auth from collection node
        node?.bodyType?.let { tab.bodyType = it; tab.syncSystemHeaders() }
        node?.bodyContent?.takeIf { it.isNotBlank() }?.let { tab.bodyContent = it }
        node?.authType?.let { tab.authType = it }
        node?.authUsername?.takeIf { it.isNotBlank() }?.let { tab.authUsername = it }
        node?.authPassword?.takeIf { it.isNotBlank() }?.let { tab.authPassword = it }
        node?.authToken?.takeIf { it.isNotBlank() }?.let { tab.authToken = it }
        node?.authApiKey?.takeIf { it.isNotBlank() }?.let { tab.authApiKey = it }
        node?.authApiValue?.takeIf { it.isNotBlank() }?.let { tab.authApiValue = it }
        node?.userHeaders?.forEach { (k, v) ->
            val existing = tab.headers.find { h -> h.key.equals(k, ignoreCase = true) }
            if (existing != null) existing.value = v
            else tab.headers.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
        }
        openTabs.add(tab)
        activeTabIndex = openTabs.size - 1
        selectedRequestId = requestId
    }

    private fun findPathRecursive(node: CollectionNode, targetId: String, path: MutableList<String>): Boolean {
        if (node.id == targetId) return true
        if (node.isFolder) {
            path.add(node.name)
            for (child in node.children) {
                if (findPathRecursive(child, targetId, path)) return true
            }
            path.removeAt(path.size - 1)
        }
        return false
    }

    private fun findNodeById(nodes: List<CollectionNode>, id: String): CollectionNode? {
        for (node in nodes) {
            if (node.id == id) return node
            if (node.isFolder) findNodeById(node.children, id)?.let { return it }
        }
        return null
    }

    fun closeTab(index: Int) {
        if (index !in openTabs.indices) return
        openTabs.removeAt(index)
        if (openTabs.isEmpty()) {
            activeTabIndex = -1
        } else if (activeTabIndex >= openTabs.size) {
            activeTabIndex = openTabs.size - 1
        }
        selectedRequestId = activeTab?.id
    }

    fun moveTab(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in openTabs.indices || toIndex !in openTabs.indices || fromIndex == toIndex) return
        val tab = openTabs.removeAt(fromIndex)
        openTabs.add(toIndex, tab)
        activeTabIndex = toIndex
        selectedRequestId = tab.id
    }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        consoleLogs.add(0, ConsoleEntry(message, level))
    }

    /**
     * Appends a network-level event to the structured Logs tab.
     * Also echoes to the Console for unified visibility (fixes M-3).
     */
    fun logNetworkEvent(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = ConsoleEntry(message, level)
        networkEventLogs.add(0, entry)
        consoleLogs.add(0, entry)
    }

    fun notifyCollectionsChanged() {
        // Bump revision for the autosave snapshotFlow.
        // Children lists are now SnapshotStateList (mutableStateListOf), so Compose
        // automatically observes add/remove on them — no .copy() trick needed.
        collectionsRevision++
    }

    /** Collapse every folder in the collection tree. */
    fun collapseAllCollections() {
        for (id in allFolderIds(collections)) collectionExpandedState[id] = false
    }

    /** Expand every folder in the collection tree. */
    fun expandAllCollections() {
        for (id in allFolderIds(collections)) collectionExpandedState[id] = true
    }

    private fun allFolderIds(nodes: List<CollectionNode>): List<String> =
        nodes.flatMap { node ->
            if (node.isFolder) listOf(node.id) + allFolderIds(node.children)
            else emptyList()
        }

    /** Add a new request node inside the given collection (folder). Opens it as a tab. */
    fun addRequestToCollection(collectionId: String) {
        val folder = collections.firstOrNull { it.id == collectionId && it.isFolder } ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New Request", siblingNames)
        val requestId = generateUuid()
        val node = CollectionNode(
            id = requestId,
            name = name,
            isFolder = false,
            method = HttpMethodType.GET,
            url = "",
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = collectionId
        selectedRequestId = requestId
        // Also open a tab for the new request
        addTab(requestId = requestId, name = name, method = HttpMethodType.GET, url = "")
    }

    /** Create a request in the currently selected collection, or as an orphan tab if none selected. */
    fun addTabInSelectedCollection() {
        val collId = selectedCollectionId
        val folder = if (collId != null) collections.firstOrNull { it.id == collId && it.isFolder } else null
        if (folder != null) {
            addRequestToCollection(folder.id)
        } else if (collections.isNotEmpty()) {
            // Default to first collection
            val first = collections.first()
            if (first.isFolder) {
                addRequestToCollection(first.id)
            } else {
                addTab()
            }
        } else {
            addTab()
        }
    }

    fun renameRequestEverywhere(requestId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        openTabs.filter { it.id == requestId }.forEach { it.name = trimmed }
        renameRequestNodeById(collections, requestId, trimmed)
        notifyCollectionsChanged()
    }

    fun revealRequestInSidebar(requestId: String) {
        // Always select the request in the sidebar, even if it isn't
        // currently visible inside a collection (Issue 2 fix).
        selectedRequestId = requestId

        // Also switch the active tab to this request so the sidebar
        // selection stays in sync with the tab bar (the LaunchedEffect
        // in MainScreen sets selectedRequestId = activeTab?.id).
        val tabIdx = openTabs.indexOfFirst { it.id == requestId }
        if (tabIdx >= 0) activeTabIndex = tabIdx

        if (expandAncestorsForRequest(collections, requestId)) {
            sidebarScrollToRequestId = requestId
            notifyCollectionsChanged()
        }
    }

    /**
     * Syncs the sidebar to the current active tab: sets [selectedRequestId],
     * expands ancestor folders, and sets the scroll target.
     * Called on startup after tab + workspace restoration, and whenever the
     * active tab changes.
     */
    fun syncSidebarToActiveTab() {
        val tab = activeTab ?: return
        selectedRequestId = tab.id
        if (expandAncestorsForRequest(collections, tab.id)) {
            sidebarScrollToRequestId = tab.id
        }
    }

    private fun renameRequestNodeById(nodes: MutableList<CollectionNode>, requestId: String, newName: String): Boolean {
        nodes.indices.forEach { index ->
            val node = nodes[index]
            if (!node.isFolder && node.id == requestId) {
                nodes[index] = node.copy(name = newName)
                return true
            }
            if (node.isFolder && node.children.isNotEmpty()) {
                if (renameRequestNodeById(node.children, requestId, newName)) return true
            }
        }
        return false
    }

    private fun expandAncestorsForRequest(nodes: List<CollectionNode>, requestId: String): Boolean {
        for (node in nodes) {
            if (!node.isFolder && node.id == requestId) return true
            if (node.isFolder && node.children.isNotEmpty()) {
                if (expandAncestorsForRequest(node.children, requestId)) {
                    collectionExpandedState[node.id] = true
                    return true
                }
            }
        }
        return false
    }

    private fun generateUniqueName(base: String, existingNames: Set<String>): String {
        if (base !in existingNames) return base
        var index = 2
        while ("$base $index" in existingNames) index++
        return "$base $index"
    }

    /** Open a history / collection request as a new tab. */
    fun openRequest(requestId: String? = null, name: String, method: HttpMethodType, url: String) {
        if (requestId != null) {
            selectedRequestId = requestId
        }
        val existing = if (requestId != null) {
            openTabs.indexOfFirst { it.id == requestId }
        } else {
            openTabs.indexOfFirst { it.url == url && it.method == method }
        }
        if (existing >= 0) {
            activeTabIndex = existing
            selectedRequestId = openTabs[existing].id
        } else {
            addTab(requestId = requestId ?: generateUuid(), name = name, method = method, url = url)
        }
    }

    /** Open the environment edit dialog for the given environment index. */
    fun openEnvEdit(index: Int) {
        editingEnvIndex = index
        showEnvEditDialog = true
    }
}
