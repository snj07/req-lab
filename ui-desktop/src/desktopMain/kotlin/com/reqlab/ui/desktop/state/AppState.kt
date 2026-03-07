package com.reqlab.ui.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.ResponseDefinition
import kotlinx.coroutines.Job

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

enum class HeaderKind { SYSTEM, USER }

// ── Data holders ────────────────────────────────────────────────

data class ConsoleEntry(
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val timestamp: Long = System.currentTimeMillis(),
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
)

/** Mutable key-value pair used in param / header / variable editors. */
class MutableKeyValue(
    key: String = "",
    value: String = "",
    enabled: Boolean = true,
    secret: Boolean = false,
    kind: HeaderKind = HeaderKind.USER,
    keyLocked: Boolean = false,
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

    // Theme
    var theme by mutableStateOf(AppTheme.DARK)

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
    val id: String = java.util.UUID.randomUUID().toString(),
    name: String = "Untitled",
    method: HttpMethodType = HttpMethodType.GET,
    url: String = "",
) {
    val requestId: String get() = id

    var name     by mutableStateOf(name)
    var method   by mutableStateOf(method)
    var url      by mutableStateOf(url)
    var isDirty  by mutableStateOf(false)
    var lastSavedTimestamp by mutableStateOf<Long?>(null)

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

    // response associated with this tab
    var response    by mutableStateOf<ResponseDefinition?>(null)
    var responseTab by mutableStateOf(ResponseTab.BODY)
    var isLoading   by mutableStateOf(false)

    // last error message (displayed in response panel when response is null + error)
    var lastError   by mutableStateOf<String?>(null)

    fun markDirty() {
        isDirty = true
    }

    fun markSaved() {
        isDirty = false
        lastSavedTimestamp = System.currentTimeMillis()
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

class AppState {
    // ── sidebar ────────
    var sidebarExpanded      by mutableStateOf(true)
    var sidebarWidth         by mutableStateOf(260)
    var historyExpanded      by mutableStateOf(true)
    var collectionsExpanded  by mutableStateOf(true)
    var environmentsExpanded by mutableStateOf(true)
    var sidebarSearchQuery   by mutableStateOf("")
    var selectedCollectionId by mutableStateOf<String?>(null)
    var selectedRequestId    by mutableStateOf<String?>(null)
    var collectionsRevision  by mutableStateOf(0)

    // ── split fractions ─
    var requestResponseSplit by mutableStateOf(0.50f)
    var mainVerticalSplit    by mutableStateOf(0.73f)   // main area / bottom panel

    // ── tabs ───────────
    val openTabs       = mutableStateListOf(RequestTabState())
    var activeTabIndex by mutableStateOf(0)
    val activeTab: RequestTabState? get() = openTabs.getOrNull(activeTabIndex)

    // ── bottom panel ──
    var selectedBottomTab    by mutableStateOf(BottomTab.CONSOLE)
    var bottomPanelExpanded  by mutableStateOf(true)
    var bottomPanelHeight    by mutableStateOf(200)
    val consoleLogs  = mutableStateListOf<ConsoleEntry>()
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

    val selectedEnvironment: EnvState get() = environments.getOrElse(selectedEnvIndex) { environments.first() }

    /** Variable layers for the active environment (used in request variable resolution). */
    fun activeVariableLayers(): List<Map<String, String>> =
        listOf(selectedEnvironment.toVariableMap())

    // ── settings ────────
    val settings = AppSettings()

    // ── demo / history data ──
    val historyItems = mutableStateListOf(
        HistoryItem("h1", HttpMethodType.GET,    "List users",  "http://localhost:8080/users",     System.currentTimeMillis() - 300_000),
        HistoryItem("h2", HttpMethodType.POST,   "Create user", "http://localhost:8080/users",     System.currentTimeMillis() - 600_000),
        HistoryItem("h3", HttpMethodType.DELETE, "Delete user", "http://localhost:8080/users/1",   System.currentTimeMillis() - 900_000),
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
        requestId: String = java.util.UUID.randomUUID().toString(),
        name: String = "Untitled ${openTabs.size + 1}",
        method: HttpMethodType = HttpMethodType.GET,
        url: String = "",
    ) {
        openTabs.add(RequestTabState(id = requestId, name = name, method = method, url = url))
        activeTabIndex = openTabs.size - 1
        selectedRequestId = requestId
    }

    fun closeTab(index: Int) {
        if (openTabs.size <= 1) return
        openTabs.removeAt(index)
        if (activeTabIndex >= openTabs.size) activeTabIndex = openTabs.size - 1
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

    fun notifyCollectionsChanged() {
        // Bump revision for the autosave snapshotFlow.
        // Children lists are now SnapshotStateList (mutableStateListOf), so Compose
        // automatically observes add/remove on them — no .copy() trick needed.
        collectionsRevision++
    }

    /** Add a new request node inside the given collection (folder). Opens it as a tab. */
    fun addRequestToCollection(collectionId: String) {
        val folder = collections.firstOrNull { it.id == collectionId && it.isFolder } ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New Request", siblingNames)
        val requestId = java.util.UUID.randomUUID().toString()
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
            addTab(requestId = requestId ?: java.util.UUID.randomUUID().toString(), name = name, method = method, url = url)
        }
    }

    /** Open the environment edit dialog for the given environment index. */
    fun openEnvEdit(index: Int) {
        editingEnvIndex = index
        showEnvEditDialog = true
    }
}
