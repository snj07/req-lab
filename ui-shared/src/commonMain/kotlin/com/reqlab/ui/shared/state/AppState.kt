package com.reqlab.ui.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.RequestKind
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorViewModel
import com.reqlab.editor.ui.SyntaxHighlighterRegistry
import com.reqlab.core.model.ResponseDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.reqlab.ui.shared.mcp.McpSessionState
import com.reqlab.ui.shared.platform.generateUuid
import com.reqlab.ui.shared.platform.currentTimeMillis
import com.reqlab.ui.shared.components.syncParamsFromUrl

import com.reqlab.ui.shared.i18n.AppLanguage

// ── Enumerations ────────────────────────────────────────────────

enum class RequestEditorTab(val label: String) {
    PARAMS("Params"), HEADERS("Headers"), BODY("Body"),
    AUTH("Auth"), PRE_REQUEST("Pre-request"), TESTS("Post-request")
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
    val requestId: String,
    val method: HttpMethodType,
    val name: String,
    val url: String,
    val timestamp: Long,
    val collectionId: String? = null,
    val folderPath: List<String> = emptyList(),
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
    /** Per-type raw body contents keyed by [BodyType.name]. */
    val bodyContents: Map<String, String> = emptyMap(),
    /** Structured form-data rows (FORM_DATA body type). */
    val formDataEntries: List<FormDataEntryState> = emptyList(),
    /** Structured urlencoded rows (X_WWW_FORM_URLENCODED body type). */
    val urlencodedEntries: List<FormDataEntryState> = emptyList(),
    val authType: AuthType? = null,
    val authUsername: String? = null,
    val authPassword: String? = null,
    val authToken: String? = null,
    val authApiKey: String? = null,
    val authApiValue: String? = null,
    val requestRef: String? = null,
    val kind: RequestKind = RequestKind.HTTP,
    val mcpConfig: McpConnectionConfig? = null,
)

/**
 * Immutable snapshot of a form-data/urlencoded row — used in [CollectionNode]
 * and import/export serialization.
 */
data class FormDataEntryState(
    val key: String = "",
    val type: FormEntryType = FormEntryType.TEXT,
    val value: String = "",
    val description: String = "",
    val enabled: Boolean = true,
)

/**
 * Mutable row for structured form-data and x-www-form-urlencoded editors.
 * [type] distinguishes text-value from file-attachment rows.
 */
class MutableFormDataRow(
    key: String = "",
    type: FormEntryType = FormEntryType.TEXT,
    value: String = "",
    description: String = "",
    enabled: Boolean = true,
    val uid: String = generateUuid(),
) {
    var key         by mutableStateOf(key)
    var type        by mutableStateOf(type)
    var value       by mutableStateOf(value)
    var description by mutableStateOf(description)
    var enabled     by mutableStateOf(enabled)
}

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
        BodyType.JSON       -> "application/json"
        BodyType.XML        -> "application/xml"
        BodyType.HTML       -> "text/html"
        BodyType.JAVASCRIPT -> "application/javascript"
        BodyType.FORM_DATA  -> "multipart/form-data"
        BodyType.X_WWW_FORM_URLENCODED -> "application/x-www-form-urlencoded"
        BodyType.GRAPHQL    -> "application/json"
        BodyType.RAW_TEXT   -> "text/plain"
        BodyType.BINARY     -> "application/octet-stream"
        BodyType.NONE       -> "application/json"
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
    var autoSaveRequests     by mutableStateOf(false)
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

    // Sidebar
    var collectionsExpanded  by mutableStateOf(false)
    var environmentsExpanded by mutableStateOf(false)

    // Proxy
    var httpProxy            by mutableStateOf("")
    var httpsProxy           by mutableStateOf("")
    var proxyEnabled         by mutableStateOf(false)

    // Scripting
    /** Namespace prefix for scripts (default "reqlab"). Change to e.g. "api". */
    var scriptPrefix         by mutableStateOf("reqlab")

    // Environment
    /** Name of the last selected environment; restored on app launch. Empty = first env. */
    var selectedEnvName      by mutableStateOf("")
}

// ── Per-tab state (one per open request tab) ────────────────────

class RequestTabState(
    val id: String = generateUuid(),
    name: String = "Untitled",
    method: HttpMethodType = HttpMethodType.GET,
    url: String = "",
    collectionName: String? = null,
    collectionId: String? = null,
    folderPath: List<String> = emptyList(),
    requestRef: String? = null,
) {
    val requestId: String get() = id

    var name     by mutableStateOf(name)
    var requestRef by mutableStateOf(requestRef)
    var method   by mutableStateOf(method)
    var url      by mutableStateOf(url)
    var isDirty  by mutableStateOf(false)
    var lastSavedTimestamp by mutableStateOf<Long?>(null)
    private var savedSnapshot by mutableStateOf("")

    var collectionName by mutableStateOf(collectionName)
    var collectionId by mutableStateOf(collectionId)
    var folderPath by mutableStateOf(folderPath)

    var selectedEditorTab by mutableStateOf(RequestEditorTab.PARAMS)

    val params  = mutableStateListOf<MutableKeyValue>()
    val headers = mutableStateListOf(
        MutableKeyValue(SystemHeaderRules.CONTENT_TYPE, "application/json", kind = HeaderKind.SYSTEM, keyLocked = true),
        MutableKeyValue(SystemHeaderRules.ACCEPT, "application/json", kind = HeaderKind.SYSTEM, keyLocked = true),
        MutableKeyValue(SystemHeaderRules.USER_AGENT, "ReqLab/1.0", kind = HeaderKind.SYSTEM, keyLocked = true),
    )

    var bodyType    by mutableStateOf(BodyType.NONE)

    /**
     * Remembers the last-selected RAW sub-type (JSON, XML, HTML, JS, Text)
     * so that switching away from RAW and back preserves the user's choice.
     */
    var lastRawSubtype by mutableStateOf(BodyType.JSON)

    /**
     * All body contents keyed by [BodyType].
     * Switching [bodyType] automatically resolves to the correct content.
     */
    val bodyContents = mutableStateMapOf<BodyType, String>()

    /** Content for the currently active body type. Gets/sets [bodyContents]\[[bodyType]]. */
    var bodyContent: String
        get() = bodyContents[bodyType] ?: ""
        set(value) { bodyContents[bodyType] = value }

    /** Structured rows for FORM_DATA body type (key/type/value/description). */
    val formRows = mutableStateListOf<MutableFormDataRow>()
    /** Structured rows for X_WWW_FORM_URLENCODED body type (key/value/description). */
    val urlencodedRows = mutableStateListOf<MutableFormDataRow>()

    var authType by mutableStateOf(AuthType.NONE)
    var authUsername  by mutableStateOf("")
    var authPassword  by mutableStateOf("")
    var authToken     by mutableStateOf("")
    var authApiKey    by mutableStateOf("")
    var authApiValue  by mutableStateOf("")

    var preRequestScript by mutableStateOf("")
    var testScript       by mutableStateOf("")
    var kind by mutableStateOf(RequestKind.HTTP)
    var mcpConfig by mutableStateOf(McpConnectionConfig())

    var retryEnabled by mutableStateOf(false)
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
    var currentJob: Job? = null

    // ── Per-tab EditorViewModel cache (survives editor-tab switches) ─────────
    // Keyed by BodyType so JSON/XML/etc. each keep independent undo stacks.
    // Not part of Compose state — not serialized, not tracked for dirty checking.
    private val bodyViewModelCache = HashMap<BodyType, EditorViewModel>()

    fun getOrCreateBodyViewModel(bodyType: BodyType, initialText: String, languageMode: LanguageMode): EditorViewModel {
        if (!SyntaxHighlighterRegistry.hasHighlighter(LanguageMode.PLAIN_TEXT)) {
            SyntaxHighlighterRegistry.registerBuiltinHighlighters()
        }
        return bodyViewModelCache.getOrPut(bodyType) { EditorViewModel(initialText, languageMode) }
    }

    /** Disposes all cached body EditorViewModels. Call before removing this tab. */
    fun disposeBodyViewModels() {
        bodyViewModelCache.values.forEach { it.dispose() }
        bodyViewModelCache.clear()
    }

    // ── URL undo stack (per-tab, plain strings, no Compose state) ───────────
    val urlUndoStack: ArrayDeque<String> = ArrayDeque(32)

    // response associated with this tab
    var response    by mutableStateOf<ResponseDefinition?>(null)
    var responseTab by mutableStateOf(ResponseTab.BODY)
    var isLoading   by mutableStateOf(false)

    // last error message (displayed in response panel when response is null + error)
    var lastError   by mutableStateOf<String?>(null)

    /** Raw SSE/NDJSON payloads collected while a streaming response is in flight. */
    val streamChunks = mutableStateListOf<String>()

    /** Concatenated LLM token text shown live while streaming. */
    var liveStreamText by mutableStateOf("")

    init {
        savedSnapshot = currentSnapshotForDirtyTracking()
    }

    private fun totalBodyLength(): Int = bodyContents.values.sumOf { it.length }

    private fun currentSnapshot(): String {
        val paramsSnapshot = params.joinToString(";") { p -> "${p.key}|${p.value}|${p.enabled}|${p.secret}" }
        val headersSnapshot = headers.joinToString(";") { h -> "${h.key}|${h.value}|${h.enabled}|${h.secret}|${h.kind}|${h.keyLocked}" }
        val formRowsSnapshot = formRows.joinToString(";") { r -> "${r.key}|${r.type.name}|${r.value}|${r.description}|${r.enabled}" }
        val urlencodedRowsSnapshot = urlencodedRows.joinToString(";") { r -> "${r.key}|${r.value}|${r.description}|${r.enabled}" }
        val allBodyContentsSnapshot = bodyContents.entries
            .sortedBy { entry -> entry.key.name }
            .joinToString(";") { entry -> "${entry.key.name}=${entry.value}" }
        return listOf(
            name,
            method.name,
            url,
            bodyType.name,
            lastRawSubtype.name,
            allBodyContentsSnapshot,
            authType.name,
            authUsername,
            authPassword,
            authToken,
            authApiKey,
            authApiValue,
            preRequestScript,
            testScript,
            retryEnabled.toString(),
            retryCount.toString(),
            retryDelayMs.toString(),
            paramsSnapshot,
            headersSnapshot,
            formRowsSnapshot,
            urlencodedRowsSnapshot,
            kind.name,
            mcpConfig.url,
            mcpConfig.transport.name,
            mcpConfig.httpMode.name,
        ).joinToString("#")
    }

    fun currentSnapshotForPersistence(): String = currentSnapshot()

    fun savedSnapshotForPersistence(): String = savedSnapshot

    fun restoreSavedSnapshot(snapshot: String?, legacyDirtyFlag: Boolean = false) {
        savedSnapshot = when {
            snapshot != null -> snapshot
            legacyDirtyFlag -> "__legacy-dirty__"
            else -> currentSnapshotForDirtyTracking()
        }
        recomputeDirty()
    }

    fun recomputeDirty() {
        isDirty = currentSnapshotForDirtyTracking() != savedSnapshot
    }

    private fun currentSnapshotForDirtyTracking(): String {
        val totalBodyLength = totalBodyLength()
        if (totalBodyLength <= 100_000) return currentSnapshot()

        // Lightweight snapshot for huge payloads: avoids allocating multi-MB strings
        // on the UI thread while still tracking meaningful state transitions.
        val bodyLengthFingerprint = bodyContents.entries
            .sortedBy { it.key.name }
            .joinToString(";") { "${it.key.name}:${it.value.length}" }
        val paramsCount = params.size
        val headersCount = headers.size
        val formCount = formRows.size
        val urlEncodedCount = urlencodedRows.size
        return listOf(
            "LARGE",
            name,
            method.name,
            url,
            bodyType.name,
            lastRawSubtype.name,
            bodyLengthFingerprint,
            authType.name,
            retryEnabled.toString(),
            retryCount.toString(),
            retryDelayMs.toString(),
            paramsCount.toString(),
            headersCount.toString(),
            formCount.toString(),
            urlEncodedCount.toString(),
        ).joinToString("#")
    }

    fun markDirty() {
        // For large body content (> 100 KB total across all body types),
        // skip the expensive currentSnapshot() call which serialises the
        // full body into a string — this would be O(n) for multi-MB
        // payloads and blocks the UI thread.  In that case, any edit is
        // unconditionally dirty.  For normal-sized content, use exact
        // snapshot comparison to correctly detect reverts to saved state.
        val totalBodyLength = totalBodyLength()
        if (totalBodyLength > 100_000) {
            isDirty = true
        } else {
            recomputeDirty()
        }
    }

    fun markSaved() {
        savedSnapshot = currentSnapshotForDirtyTracking()
        isDirty = false
        lastSavedTimestamp = currentTimeMillis()
    }

    /**
     * After a rename, replaces only the name prefix of [savedSnapshot] so that
     * dirty tracking still reflects unsaved body/URL changes.
     *
     * Without this fix, renaming a tab permanently corrupts [savedSnapshot] with the
     * old name → subsequent [recomputeDirty] always returns `true`, even when the user
     * reverts every other edit back to the original values.
     *
     * [oldName] is the name the tab had immediately before the rename.
     */
    fun reanchorSavedSnapshotAfterRename(oldName: String) {
        // Snapshot format: name#method#url#…  (components joined with '#')
        // We need to replace the leading name component without touching the rest.
        val sep = '#'
        val prefixWithSep = "$oldName$sep"
        savedSnapshot = when {
            savedSnapshot.startsWith(prefixWithSep) ->
                "$name$sep${savedSnapshot.removePrefix(prefixWithSep)}"
            savedSnapshot == oldName ->
                name
            !isDirty ->
                // Edge case: name itself contained '#' so the prefix didn't match.
                // The tab is clean so a full reanchor is safe.
                currentSnapshotForDirtyTracking()
            else ->
                // Dirty tab and couldn't parse. Leave savedSnapshot unchanged.
                // Cmd+S (markSaved) will fully re-anchor on the next explicit save.
                savedSnapshot
        }
        recomputeDirty()
    }

    fun syncSystemHeaders() {
        // Content-Type always reflects the current body type (derived value).
        upsertSystemHeader(SystemHeaderRules.CONTENT_TYPE, SystemHeaderRules.defaultContentTypeFor(bodyType), overwriteIfExists = true)
        // Accept and User-Agent are only inserted when missing so that user
        // overrides to these values are preserved across body-type changes and
        // request sends.
        upsertSystemHeader(SystemHeaderRules.ACCEPT, "application/json", overwriteIfExists = false)
        upsertSystemHeader(SystemHeaderRules.USER_AGENT, "ReqLab/1.0", overwriteIfExists = false)
    }

    private fun upsertSystemHeader(key: String, value: String, overwriteIfExists: Boolean = true) {
        val existing = headers.indexOfFirst { it.key.equals(key, ignoreCase = true) }
        if (existing >= 0) {
            if (overwriteIfExists) {
                headers[existing].key = key
                headers[existing].value = value
            }
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

class AppState(openDefaultTab: Boolean = true, withDemoData: Boolean = false) {
    // ── workspace mode ──
    var workspaceMode by mutableStateOf(WorkspaceMode.HTTP)

    // ── sidebar ────────
    var sidebarExpanded      by mutableStateOf(true)
    var sidebarWidth         by mutableStateOf(260f)
    var historyExpanded      by mutableStateOf(false)
    var collectionsExpanded  by mutableStateOf(withDemoData)
    var environmentsExpanded by mutableStateOf(withDemoData)
    var sidebarSearchQuery   by mutableStateOf("")
    var selectedCollectionId by mutableStateOf<String?>(null)
    var selectedRequestId    by mutableStateOf<String?>(null)
    var sidebarScrollToRequestId by mutableStateOf<String?>(null)
    var collectionsRevision  by mutableStateOf(0)
    var historyRevision      by mutableStateOf(0)

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

    // ── MCP sessions ──
    // Long-lived, keyed by tab id so an MCP connection (and its loaded tools/
    // resources) survives tab switches. Disposed only when the tab is closed.
    /** Application-lifetime scope for background work that must outlive individual composables. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mcpSessions = mutableMapOf<String, McpSessionState>()

    /** Returns the persistent MCP session for [tabId], creating it on first use. */
    fun getOrCreateMcpSession(tabId: String): McpSessionState =
        mcpSessions.getOrPut(tabId) {
            McpSessionState(appScope, onConsole = { message, level -> logNetworkEvent(message, level) })
        }

    /** Disconnects and forgets the MCP session for [tabId] (called on tab close). */
    fun disposeMcpSession(tabId: String) {
        mcpSessions.remove(tabId)?.let { session -> appScope.launch { session.disconnect() } }
    }

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
    var showHelpDialog       by mutableStateOf(false)
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
    val environments = mutableStateListOf<EnvState>().also {
        if (withDemoData) {
            it.addAll(AppStateDemoData.environments())
        }
    }

    // ── global variables (lowest priority, overridden by environment + local) ──
    val globalVariables = mutableStateListOf<MutableKeyValue>().also {
        if (withDemoData) {
            it.addAll(AppStateDemoData.globalVariables())
        }
    }
    val collectionVariables = mutableStateMapOf<String, String>()
    var showGlobalVariablesDialog by mutableStateOf(false)

    val selectedEnvironment: EnvState? get() = environments.getOrNull(selectedEnvIndex)

    /**
     * Variable layers for the active environment (used in request variable resolution).
        * Resolution priority (first match wins): Environment → Collection → Global.
        * Environment variables override collection and global variables.
     */
    fun activeVariableLayers(): List<Map<String, String>> =
        listOf(
            selectedEnvironment?.toVariableMap().orEmpty(),
            collectionVariables.toMap(),
            globalVariables.filter { it.enabled }.associate { it.key to it.value },
        )

    /**
     * Merges variables set by a pre-request or post-request script into the active environment.
     * Existing variables with the same key are updated; new keys are appended.
     */
    fun mergeScriptVariables(vars: Map<String, String>) {
        val env = selectedEnvironment ?: return
        mergeInto(env.variables, vars)
    }

    fun mergeGlobalScriptVariables(vars: Map<String, String>) {
        mergeInto(globalVariables, vars)
    }

    fun mergeCollectionScriptVariables(vars: Map<String, String>) {
        vars.forEach { (key, value) ->
            collectionVariables[key] = value
        }
    }

    private fun mergeInto(target: MutableList<MutableKeyValue>, vars: Map<String, String>) {
        vars.forEach { (key, value) ->
            val existing = target.firstOrNull { it.key == key }
            if (existing != null) existing.value = value
            else target.add(MutableKeyValue(key = key, value = value))
        }
    }

    // ── settings ────────
    val settings = AppSettings()

    // ── history data ──
    val historyItems = mutableStateListOf<HistoryItem>().also {
        if (withDemoData) {
            it.addAll(AppStateDemoData.historyItems())
        }
    }

    val collections = mutableStateListOf<CollectionNode>().also {
        if (withDemoData) {
            it.addAll(AppStateDemoData.collections())
        }
    }

    init {
        ensureRequestRefsInitialized()
        syncHistoryWithCollections()
    }

    private data class RequestReference(
        val requestId: String,
        val requestRef: String,
        val collectionId: String?,
        val folderPath: List<String>,
        val name: String,
        val method: HttpMethodType,
        val url: String,
    )

    data class ScriptRequestTarget(
        val requestId: String,
        val name: String,
        val method: HttpMethodType,
        val url: String,
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
        var cId: String? = null
        val fPath = mutableListOf<String>()
        var found = false
        for (c in collections) {
            if (findPathRecursive(c, requestId, fPath)) {
                cName = c.name
                cId = c.id
                found = true
                break
            }
        }
        val pathWithoutNode = if (found && fPath.isNotEmpty()) fPath.dropLast(1) else emptyList()
        val node = findNodeById(collections, requestId)
        val tab = RequestTabState(
            id = requestId,
            requestRef = node?.requestRef ?: node?.id,
            name = name,
            method = method,
            url = url,
            collectionName = cName,
            collectionId = cId,
            folderPath = pathWithoutNode,
        )
        // Populate params from the URL query string so the Params tab shows them
        // immediately on first open. CollectionNode has no params field so this is
        // the only opportunity to seed them before the user opens the Params tab.
        if (url.contains('?')) syncParamsFromUrl(tab, url)
        node?.preRequestScript?.takeIf { it.isNotBlank() }?.let { tab.preRequestScript = it }
        node?.testScript?.takeIf { it.isNotBlank() }?.let { tab.testScript = it }
        // Populate body, headers, and auth from collection node
        node?.bodyType?.let { tab.bodyType = it; tab.syncSystemHeaders() }
        node?.bodyContent?.takeIf { it.isNotBlank() }?.let { tab.bodyContent = it }
        node?.bodyContents?.forEach { (typeName, content) ->
            runCatching { BodyType.valueOf(typeName) }.getOrNull()?.let { tab.bodyContents[it] = content }
        }
        // Populate structured form rows from collection node
        if (!node?.formDataEntries.isNullOrEmpty()) {
            tab.formRows.clear()
            node!!.formDataEntries.forEach { e ->
                tab.formRows.add(MutableFormDataRow(e.key, e.type, e.value, e.description, e.enabled))
            }
        }
        if (!node?.urlencodedEntries.isNullOrEmpty()) {
            tab.urlencodedRows.clear()
            node!!.urlencodedEntries.forEach { e ->
                tab.urlencodedRows.add(MutableFormDataRow(e.key, e.type, e.value, e.description, e.enabled))
            }
        }
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
        tab.kind = node?.kind ?: RequestKind.HTTP
        node?.mcpConfig?.let { tab.mcpConfig = it }
        if (tab.kind == RequestKind.MCP && tab.url.isBlank()) {
            tab.url = tab.mcpConfig.url
        }
        // Re-anchor the saved snapshot after all fields (including system headers
        // injected by syncSystemHeaders above) have been populated. Without this,
        // the snapshot captured in RequestTabState.init{} pre-dates the system
        // headers, so recomputeDirty() is always true immediately after opening.
        tab.restoreSavedSnapshot(null)
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

    private fun findRequestBySignature(
        nodes: List<CollectionNode>,
        name: String,
        method: HttpMethodType,
        url: String,
    ): CollectionNode? {
        for (node in nodes) {
            if (!node.isFolder && node.name == name && node.method == method && node.url == url) return node
            if (node.isFolder) findRequestBySignature(node.children, name, method, url)?.let { return it }
        }
        return null
    }

    private fun findRequestBySignatureInSubtree(node: CollectionNode, tab: RequestTabState): CollectionNode? {
        node.children.forEach { child ->
            if (!child.isFolder && child.name == tab.name && child.method == tab.method && child.url == tab.url) {
                return child
            }
            if (child.isFolder) {
                findRequestBySignatureInSubtree(child, tab)?.let { return it }
            }
        }
        return null
    }

    private fun findRequestByNameInSubtree(node: CollectionNode, tab: RequestTabState): CollectionNode? {
        node.children.forEach { child ->
            if (!child.isFolder && child.name == tab.name) return child
            if (child.isFolder) {
                findRequestByNameInSubtree(child, tab)?.let { return it }
            }
        }
        return null
    }

    private fun resolveFolderPathScope(root: CollectionNode, folderPath: List<String>): CollectionNode {
        var cursor = root
        for (folderName in folderPath) {
            val next = cursor.children.firstOrNull { it.isFolder && it.name == folderName } ?: break
            cursor = next
        }
        return cursor
    }

    private fun resolveSidebarRequestId(requestId: String): String {
        val tab = openTabs.firstOrNull { it.id == requestId }
        if (tab == null) {
            if (findNodeById(collections, requestId) != null) return requestId
            return requestId
        }

        val references = buildRequestReferenceMap()
        val byRequestRef = tab.requestRef?.let { ref ->
            references.values.firstOrNull { it.requestRef == ref }
        }
        if (byRequestRef != null) return byRequestRef.requestId

        if (references.containsKey(requestId) && tabMatchesRequestScope(tab, requestId, references)) {
            return requestId
        }

        val collectionId = tab.collectionId
        if (collectionId != null) {
            val root = collections.firstOrNull { it.isFolder && it.id == collectionId }
            if (root != null) {
                val scope = resolveFolderPathScope(root, tab.folderPath)
                val candidate = findRequestBySignatureInSubtree(scope, tab)
                    ?: findRequestByNameInSubtree(scope, tab)
                    ?: findRequestBySignatureInSubtree(root, tab)
                    ?: findRequestByNameInSubtree(root, tab)
                if (candidate != null) return candidate.id
            }
        }

        val collectionName = tab.collectionName
        if (collectionName != null) {
            val roots = collections.filter { it.isFolder && it.name == collectionName }
            val candidateFolders = mutableListOf<CollectionNode>()
            roots.forEach { root ->
                candidateFolders.add(resolveFolderPathScope(root, tab.folderPath))
            }

            candidateFolders.forEach { folder ->
                val candidate = findRequestBySignatureInSubtree(folder, tab)
                if (candidate != null) return candidate.id
            }
            candidateFolders.forEach { folder ->
                val candidate = findRequestByNameInSubtree(folder, tab)
                if (candidate != null) return candidate.id
            }

            roots.forEach { root ->
                val candidate = findRequestBySignatureInSubtree(root, tab)
                if (candidate != null) return candidate.id
            }
            roots.forEach { root ->
                val candidate = findRequestByNameInSubtree(root, tab)
                if (candidate != null) return candidate.id
            }
        }

        val bySignature = findRequestBySignature(
            nodes = collections,
            name = tab.name,
            method = tab.method,
            url = tab.url,
        )
        if (bySignature != null) return bySignature.id

        if (references.containsKey(requestId)) return requestId

        return requestId
    }

    private fun tabMatchesRequestScope(
        tab: RequestTabState,
        requestId: String,
        references: Map<String, RequestReference>,
    ): Boolean {
        val reference = references[requestId] ?: return false
        tab.requestRef?.let { if (reference.requestRef != it) return false }

        // Collection ID is the most reliable discriminator: use it exclusively when available.
        // We intentionally do NOT also check collectionName here — names can be stale after
        // a collection rename between sessions while the ID stays correct.
        val tabCollectionId = tab.collectionId
        if (tabCollectionId != null) {
            return tabCollectionId == reference.collectionId
        }

        // No collection ID available — fall back to name + URL to prevent a stale tab id from
        // matching a same-named request that has a different URL (classic restored-tab problem).
        val tabCollectionName = tab.collectionName
        if (tabCollectionName != null) {
            val referenceCollectionName = reference.collectionId?.let { id ->
                collections.firstOrNull { it.isFolder && it.id == id }?.name
            }
            if (referenceCollectionName != null && referenceCollectionName != tabCollectionName) return false
        }
        if (tab.folderPath.isNotEmpty() && !pathStartsWith(reference.folderPath, tab.folderPath)) return false
        // URL match: guard against stale ids in same-named collections.
        if (reference.url.isNotEmpty() && reference.url != tab.url) return false
        return true
    }

    private fun pathStartsWith(path: List<String>, prefix: List<String>): Boolean {
        if (prefix.size > path.size) return false
        prefix.indices.forEach { index ->
            if (path[index] != prefix[index]) return false
        }
        return true
    }

    fun closeTab(index: Int) {
        if (index !in openTabs.indices) return
        openTabs[index].disposeBodyViewModels()
        disposeMcpSession(openTabs[index].id)
        openTabs.removeAt(index)
        if (openTabs.isEmpty()) {
            activeTabIndex = -1
        } else if (activeTabIndex >= openTabs.size) {
            activeTabIndex = openTabs.size - 1
        }
        selectedRequestId = activeTab?.id
    }

    /**
     * Closes every open tab whose id is in [ids].
     * Safe to call with ids that have no matching open tab (silently ignored).
     * Adjusts [activeTabIndex] correctly even when multiple tabs are removed.
     */
    fun closeTabsByIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toHashSet()
        // Remove in reverse-index order to keep earlier indices valid.
        openTabs.indices.reversed()
            .filter { openTabs[it].id in idSet }
            .forEach { idx ->
                openTabs[idx].disposeBodyViewModels()
                disposeMcpSession(openTabs[idx].id)
                openTabs.removeAt(idx)
            }
        if (openTabs.isEmpty()) {
            activeTabIndex = -1
        } else if (activeTabIndex >= openTabs.size) {
            activeTabIndex = openTabs.size - 1
        }
        selectedRequestId = activeTab?.id
    }

    /** Updates [RequestTabState.collectionName] for every open tab that still has [oldName].
     *  Called after a root collection is renamed so [resolveSidebarRequestId] keeps working. */
    fun updateTabsCollectionName(oldName: String, newName: String) {
        openTabs.forEach { tab ->
            if (tab.collectionName == oldName) tab.collectionName = newName
        }
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
        ensureRequestRefsInitialized()
        syncOpenTabsWithCollections()
        syncHistoryWithCollections()
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
            requestRef = generateUuid(),
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

    fun addMcpConnectionToCollection(collectionId: String) {
        val folder = collections.firstOrNull { it.id == collectionId && it.isFolder } ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New MCP Connection", siblingNames)
        val requestId = generateUuid()
        val mcp = McpConnectionConfig(url = "{{mcpBaseUrl}}")
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.POST,
            url = mcp.url,
            kind = RequestKind.MCP,
            mcpConfig = mcp,
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.POST, url = mcp.url)
    }

    /** Create a request in the selected collection, ensuring a default collection exists when needed. */
    fun addTabInSelectedCollection() {
        val collId = selectedCollectionId
        val folder = if (collId != null) collections.firstOrNull { it.id == collId && it.isFolder } else null
        if (folder != null) {
            addRequestToCollection(folder.id)
        } else {
            val firstFolder = collections.firstOrNull { it.isFolder }
            if (firstFolder != null) {
                addRequestToCollection(firstFolder.id)
            } else {
                val defaultFolder = ensureDefaultCollectionFolder()
                addRequestToCollection(defaultFolder.id)
            }
        }
    }

    fun pruneEmptyGlobalVariables() {
        globalVariables.removeAll { it.key.isBlank() && it.value.isBlank() }
    }

    fun pruneEmptyVariablesForEnvironment(index: Int) {
        val env = environments.getOrNull(index) ?: return
        env.variables.removeAll { it.key.isBlank() && it.value.isBlank() }
    }

    fun renameRequestEverywhere(requestId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        openTabs.filter { it.id == requestId }.forEach { tab ->
            val oldName = tab.name
            tab.name = trimmed
            // Re-anchor the name component of savedSnapshot so that dirty tracking
            // reflects only non-name unsaved changes after a rename from the sidebar
            // or tab chip. Without this, reverting a URL edit after a rename always
            // appears dirty because savedSnapshot still contains the old name.
            tab.reanchorSavedSnapshotAfterRename(oldName)
        }
        renameRequestNodeById(collections, requestId, trimmed)
        notifyCollectionsChanged()
    }

    fun revealRequestInSidebar(requestId: String): Boolean {
        val resolvedRequestId = resolveSidebarRequestId(requestId)
        val targetNode = findNodeById(collections, resolvedRequestId)
        if (targetNode == null || targetNode.isFolder) {
            showError("Request not found", "Request no longer exists in collections.")
            return false
        }
        sidebarExpanded = true
        sidebarSearchQuery = ""

        // Always select the request in the sidebar, even if it isn't
        // currently visible inside a collection (Issue 2 fix).
        selectedRequestId = resolvedRequestId

        // Also switch the active tab to this request so the sidebar
        // selection stays in sync with the tab bar (the LaunchedEffect
        // in MainScreen sets selectedRequestId = activeTab?.id).
        val tabIdx = openTabs.indexOfFirst { it.id == requestId || it.id == resolvedRequestId }
        if (tabIdx >= 0) activeTabIndex = tabIdx

        if (expandAncestorsForRequest(collections, resolvedRequestId)) {
            // Clear first, then set, so duplicate-name tab switches can retrigger
            // scroll even when the same request id is emitted consecutively.
            sidebarScrollToRequestId = null
            sidebarScrollToRequestId = resolvedRequestId
        }
        return true
    }

    fun recordHistory(
        requestId: String,
        method: HttpMethodType,
        name: String,
        url: String,
        aliasRequestIds: Set<String> = emptySet(),
    ) {
        val references = buildRequestReferenceMap()
        val resolvedRequestId = resolveSidebarRequestId(requestId)
        val reference = references[resolvedRequestId]
            ?: references.values.firstOrNull { it.name == name && it.method == method && it.url == url }
            ?: return

        val idsToReplace = (aliasRequestIds + requestId + resolvedRequestId + reference.requestId)
            .filter { it.isNotBlank() }
            .toSet()
        if (idsToReplace.isNotEmpty()) {
            historyItems.removeAll { it.requestId in idsToReplace }
        }
        val item = HistoryItem(
            requestId = reference.requestId,
            method = reference.method,
            name = reference.name,
            url = reference.url,
            timestamp = currentTimeMillis(),
            collectionId = reference.collectionId,
            folderPath = reference.folderPath,
        )
        historyItems.add(0, item)
        historyRevision++
    }

    fun openHistoryItem(item: HistoryItem) {
        val resolvedRequestId = resolveHistoryRequestId(item)
        val target = findNodeById(collections, resolvedRequestId)
        if (target != null && !target.isFolder) {
            openRequest(requestId = resolvedRequestId, name = target.name, method = target.method ?: item.method, url = target.url ?: item.url)
            selectedRequestId = resolvedRequestId
        } else {
            addTab(
                requestId = generateUuid(),
                name = item.name,
                method = item.method,
                url = item.url,
            )
            showError("Request not found", "Request no longer exists in collections. Opened a temporary tab.")
        }
    }

    private fun resolveHistoryRequestId(item: HistoryItem): String {
        if (findNodeById(collections, item.requestId) != null) return item.requestId
        val bySignature = findRequestBySignature(
            nodes = collections,
            name = item.name,
            method = item.method,
            url = item.url,
        )
        return bySignature?.id ?: item.requestId
    }

    fun goToCollectionFromHistory(item: HistoryItem): Boolean {
        val resolvedRequestId = resolveHistoryRequestId(item)
        val revealed = revealRequestInSidebar(resolvedRequestId)
        if (!revealed) return false

        if (openTabs.none { it.id == resolvedRequestId }) {
            val target = findNodeById(collections, resolvedRequestId)
            if (target != null && !target.isFolder) {
                openRequest(
                    requestId = target.id,
                    name = target.name,
                    method = target.method ?: item.method,
                    url = target.url ?: item.url,
                )
            }
        }
        return true
    }

    fun clearHistory() {
        if (historyItems.isEmpty()) return
        historyItems.clear()
        historyRevision++
    }

    /**
     * Resolves a script-provided next-request token to an existing request
     * across collections.
     *
     * Matching order:
     * 1) exact request id
     * 2) exact requestRef
     * 3) exact request name (preferring [preferredCollectionId] when provided)
     */
    fun resolveScriptRequestTarget(token: String, preferredCollectionId: String? = null): ScriptRequestTarget? {
        val normalized = token.trim()
        if (normalized.isEmpty()) return null
        val refs = buildRequestReferenceMap().values.toList()

        refs.firstOrNull { it.requestId == normalized }?.let {
            return ScriptRequestTarget(it.requestId, it.name, it.method, it.url)
        }
        refs.firstOrNull { it.requestRef == normalized }?.let {
            return ScriptRequestTarget(it.requestId, it.name, it.method, it.url)
        }

        val byName = refs.filter { it.name == normalized }
        if (byName.isEmpty()) return null

        val preferred = preferredCollectionId?.let { cid -> byName.filter { it.collectionId == cid } }.orEmpty()
        val winner = when {
            preferred.size == 1 -> preferred.first()
            byName.size == 1 -> byName.first()
            else -> null
        } ?: return null

        return ScriptRequestTarget(winner.requestId, winner.name, winner.method, winner.url)
    }

    fun removeHistoryItem(requestId: String) {
        val removed = historyItems.removeAll { it.requestId == requestId }
        if (removed) {
            historyRevision++
        }
    }

    fun replaceHistoryItems(items: List<HistoryItem>) {
        historyItems.clear()
        historyItems.addAll(items)
        historyRevision++
    }

    /**
     * Syncs the sidebar to the current active tab: sets [selectedRequestId],
     * expands ancestor folders, and sets the scroll target.
     * Called on startup after tab + workspace restoration, and whenever the
     * active tab changes.
     */
    fun syncSidebarToActiveTab() {
        val tab = activeTab ?: return
        val resolvedRequestId = resolveSidebarRequestId(tab.id)
        val wasAlreadySelected = selectedRequestId == resolvedRequestId
        selectedRequestId = resolvedRequestId
        if (!wasAlreadySelected && expandAncestorsForRequest(collections, resolvedRequestId)) {
            // Reset signal first so repeated targets still trigger row scrolling.
            sidebarScrollToRequestId = null
            sidebarScrollToRequestId = resolvedRequestId
        }
    }

    private fun buildRequestReferenceMap(): Map<String, RequestReference> {
        val references = linkedMapOf<String, RequestReference>()
        collectRequestReferences(
            nodes = collections,
            currentCollectionId = null,
            currentFolderPath = emptyList(),
            target = references,
        )
        return references
    }

    private fun collectRequestReferences(
        nodes: List<CollectionNode>,
        currentCollectionId: String?,
        currentFolderPath: List<String>,
        target: MutableMap<String, RequestReference>,
    ) {
        for (node in nodes) {
            if (node.isFolder) {
                val nextCollectionId = currentCollectionId ?: node.id
                val nextFolderPath = if (currentCollectionId == null) {
                    currentFolderPath
                } else {
                    currentFolderPath + node.name
                }
                collectRequestReferences(node.children, nextCollectionId, nextFolderPath, target)
            } else {
                val method = node.method ?: continue
                val url = node.url ?: continue
                val requestRef = node.requestRef ?: node.id
                target[node.id] = RequestReference(
                    requestId = node.id,
                    requestRef = requestRef,
                    collectionId = currentCollectionId,
                    folderPath = currentFolderPath,
                    name = node.name,
                    method = method,
                    url = url,
                )
            }
        }
    }

    private fun syncHistoryWithCollections() {
        val references = buildRequestReferenceMap()
        var changed = false
        val synced = historyItems.mapNotNull { item ->
            val ref = references[item.requestId] ?: run {
                changed = true
                return@mapNotNull null
            }
            val updated = item.copy(
                method = ref.method,
                name = ref.name,
                url = ref.url,
                collectionId = ref.collectionId,
                folderPath = ref.folderPath,
            )
            if (updated != item) changed = true
            updated
        }
        if (changed) {
            historyItems.clear()
            historyItems.addAll(synced)
            historyRevision++
        }
    }

    private fun syncOpenTabsWithCollections() {
        val references = buildRequestReferenceMap()
        val rootNamesById = collections
            .filter { it.isFolder }
            .associate { it.id to it.name }

        openTabs.forEach { tab ->
            val reference = tab.requestRef?.let { ref ->
                references.values.firstOrNull { it.requestRef == ref }
            } ?: references[tab.id] ?: return@forEach
            tab.requestRef = reference.requestRef
            tab.collectionId = reference.collectionId
            tab.collectionName = reference.collectionId?.let { rootNamesById[it] }
            tab.folderPath = reference.folderPath
        }
    }

    private fun ensureRequestRefsInitialized() {
        ensureRequestRefsInitializedRecursive(collections)
    }

    private fun ensureRequestRefsInitializedRecursive(nodes: MutableList<CollectionNode>) {
        nodes.indices.forEach { index ->
            val node = nodes[index]
            if (node.isFolder) {
                if (node.children.isNotEmpty()) ensureRequestRefsInitializedRecursive(node.children)
            } else if (node.requestRef == null) {
                nodes[index] = node.copy(requestRef = generateUuid())
            }
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

    /**
     * Writes the current tab editing state back into the matching [CollectionNode] in the
     * collections tree so that workspace export reflects the latest saved content.
     *
     * Must be called from the main thread (it mutates Compose snapshot state).
     * Returns `true` if the node was found and updated.
     */
    fun syncTabToCollectionNode(tab: RequestTabState): Boolean {
        val updated = syncTabToNodeRecursive(collections, tab)
        if (updated) notifyCollectionsChanged()
        return updated
    }

    private fun syncTabToNodeRecursive(
        nodes: MutableList<CollectionNode>,
        tab: RequestTabState,
    ): Boolean {
        nodes.indices.forEach { index ->
            val node = nodes[index]
            if (!node.isFolder && node.id == tab.id) {
                val userHeadersSnapshot = tab.headers
                    .filter { it.kind == HeaderKind.USER }
                    .map { it.key to it.value }
                val bodyContentsSnapshot: Map<String, String> =
                    tab.bodyContents.entries.associate { it.key.name to it.value }
                val formEntriesSnapshot = tab.formRows.map { r ->
                    FormDataEntryState(r.key, r.type, r.value, r.description, r.enabled)
                }
                val urlencodedSnapshot = tab.urlencodedRows.map { r ->
                    FormDataEntryState(r.key, r.type, r.value, r.description, r.enabled)
                }
                nodes[index] = node.copy(
                    requestRef = node.requestRef ?: tab.requestRef ?: generateUuid(),
                    name   = tab.name,
                    method = tab.method,
                    url    = tab.url,
                    bodyType           = tab.bodyType.takeIf { it != BodyType.NONE },
                    bodyContent        = tab.bodyContent.ifBlank { null },
                    bodyContents       = bodyContentsSnapshot,
                    formDataEntries    = formEntriesSnapshot,
                    urlencodedEntries  = urlencodedSnapshot,
                    authType           = tab.authType.takeIf { it != AuthType.NONE },
                    authUsername       = tab.authUsername.ifBlank { null },
                    authPassword       = tab.authPassword.ifBlank { null },
                    authToken          = tab.authToken.ifBlank { null },
                    authApiKey         = tab.authApiKey.ifBlank { null },
                    authApiValue       = tab.authApiValue.ifBlank { null },
                    userHeaders        = userHeadersSnapshot,
                    preRequestScript   = tab.preRequestScript.ifBlank { null },
                    testScript         = tab.testScript.ifBlank { null },
                )
                return true
            }
            if (node.isFolder && node.children.isNotEmpty()) {
                if (syncTabToNodeRecursive(node.children, tab)) return true
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

    private fun ensureDefaultCollectionFolder(): CollectionNode {
        collections.firstOrNull { it.isFolder }?.let { return it }

        val existingNames = collections.map { it.name }.toSet()
        val folder = CollectionNode(
            id = generateUuid(),
            name = generateUniqueName("Default Collection", existingNames),
            isFolder = true,
            children = mutableStateListOf(),
        )
        collections.add(folder)
        notifyCollectionsChanged()
        selectedCollectionId = folder.id
        return folder
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
