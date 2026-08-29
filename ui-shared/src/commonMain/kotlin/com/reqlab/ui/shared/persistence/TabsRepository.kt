package com.reqlab.ui.shared.persistence

import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.AuthType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.FormDataEntryState
import com.reqlab.ui.shared.state.HeaderKind
import com.reqlab.ui.shared.state.MutableFormDataRow
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.state.RequestTabState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.reqlab.ui.shared.platform.PlatformStorage

/**
 * Saves and restores open request tabs using PlatformStorage.
 * Persists: name, method, url, body-type, body-content, params, headers, dirty-flag.
 */
object TabsRepository {

    private const val STORAGE_KEY = "reqlab.tabs"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Save ───────────────────────────────────────────────────────────────

    fun save(state: AppState): Boolean {
        return runCatching {
            val arr = buildJsonArray {
                state.openTabs.forEach { tab ->
                    add(buildJsonObject {
                        put("id",          tab.id)
                        put("requestRef",  tab.requestRef ?: "")
                        put("name",        tab.name)
                        put("method",      tab.method.name)
                        put("url",         tab.url)
                        put("collectionName", tab.collectionName ?: "")
                        put("collectionId", tab.collectionId ?: "")
                        put("folderPath", buildJsonArray { tab.folderPath.forEach { add(JsonPrimitive(it)) } })
                        put("bodyType",    tab.bodyType.name)
                        put("lastRawSubtype", tab.lastRawSubtype.name)
                        put("bodyContent", tab.bodyContent)
                        put("bodyContents", buildJsonObject {
                            tab.bodyContents.forEach { entry ->
                                if (entry.value.isNotBlank()) put(entry.key.name, entry.value)
                            }
                        })
                        put("authType",    tab.authType.name)
                        put("authUsername", tab.authUsername)
                        put("authPassword", tab.authPassword)
                        put("authToken",    tab.authToken)
                        put("authApiKey",   tab.authApiKey)
                        put("authApiValue", tab.authApiValue)
                        put("preRequestScript", tab.preRequestScript)
                        put("testScript", tab.testScript)
                        put("retryEnabled", tab.retryEnabled)
                        put("retryCount", tab.retryCount)
                        put("retryDelayMs", tab.retryDelayMs)
                        tab.lastSavedTimestamp?.let { put("lastSavedTimestamp", it) }
                        put("savedSnapshot", tab.savedSnapshotForPersistence())
                        put("isDirty",     tab.isDirty)
                        put("params",  kvListJson(tab.params))
                        put("headers", kvListJson(tab.headers))
                        put("formRows", formRowListJson(tab.formRows))
                        put("urlencodedRows", formRowListJson(tab.urlencodedRows))
                        put("kind", tab.kind.name)
                        put("mcp", json.parseToJsonElement(
                            json.encodeToString(com.reqlab.core.model.McpConnectionConfig.serializer(), tab.mcpConfig),
                        ))
                    })
                }
            }
            val root = buildJsonObject {
                put("activeIndex", state.activeTabIndex)
                put("tabs", arr)
            }
            PlatformStorage.putString(STORAGE_KEY, root.toString())
            true
        }.getOrElse { false }
    }

    // ── Load ───────────────────────────────────────────────────────────────

    /** Loads saved tabs into [state]. Call once on startup before the first recomposition. */
    fun load(state: AppState) {
        val stored = PlatformStorage.getString(STORAGE_KEY) ?: return
        runCatching {
            val root     = json.parseToJsonElement(stored).jsonObject
            val tabsJson = root["tabs"]?.jsonArray ?: return
            if (tabsJson.isEmpty()) return

            // Dispose any cached EditorViewModels before clearing to prevent
            // orphaned coroutine scopes when the workspace is restored from disk.
            state.openTabs.forEach { it.disposeBodyViewModels() }
            // Replace the default single empty tab
            state.openTabs.clear()
            tabsJson.forEach { el ->
                val obj = el.jsonObject
                
                val savedId = obj["id"]?.jsonPrimitive?.content ?: return@forEach
                val savedRequestRef = obj["requestRef"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                val parsedCollectionName = obj["collectionName"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                val parsedCollectionId = obj["collectionId"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                val parsedFolderPath = obj["folderPath"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                val reqName = obj["name"]?.jsonPrimitive?.content ?: "Untitled"
                val savedMethod = safeEnum(obj["method"]?.jsonPrimitive?.content, HttpMethodType.GET)
                val savedUrl = obj["url"]?.jsonPrimitive?.content ?: ""

                fun findByRequestRef(nodes: List<com.reqlab.ui.shared.state.CollectionNode>, ref: String): com.reqlab.ui.shared.state.CollectionNode? {
                    nodes.forEach { node ->
                        if (!node.isFolder && node.requestRef == ref) return node
                        if (node.isFolder) {
                            findByRequestRef(node.children, ref)?.let { return it }
                        }
                    }
                    return null
                }
                
                // Find matching request node mapped to the newly generated UUIDs
                var realId = savedId
                val candidateCollections = when {
                    parsedCollectionId != null -> state.collections.filter { it.id == parsedCollectionId }
                    parsedCollectionName != null -> state.collections.filter { it.name == parsedCollectionName }
                    else -> emptyList()
                }
                var resolvedCollection: com.reqlab.ui.shared.state.CollectionNode? = null

                // Strongest match: stable request identity persisted on the tab.
                if (savedRequestRef != null) {
                    val byRef = findByRequestRef(state.collections, savedRequestRef)
                    if (byRef != null) {
                        realId = byRef.id
                    }
                }

                fun resolveFolderScope(root: com.reqlab.ui.shared.state.CollectionNode): com.reqlab.ui.shared.state.CollectionNode {
                    var cursor: com.reqlab.ui.shared.state.CollectionNode = root
                    for (folder in parsedFolderPath) {
                        val next = cursor.children.find { it.name == folder && it.isFolder } ?: break
                        cursor = next
                    }
                    return cursor
                }

                candidateCollections.forEach { coll ->
                    if (resolvedCollection != null) return@forEach
                    val scope = resolveFolderScope(coll)
                    val bySignature = scope.children.firstOrNull {
                        !it.isFolder && it.name == reqName && it.method == savedMethod && (it.url ?: "") == savedUrl
                    }
                    if (bySignature != null) {
                        realId = bySignature.id
                        resolvedCollection = coll
                    }
                }

                if (resolvedCollection == null) {
                    candidateCollections.forEach { coll ->
                        if (resolvedCollection != null) return@forEach
                        val scope = resolveFolderScope(coll)
                        val byName = scope.children.firstOrNull { !it.isFolder && it.name == reqName }
                        if (byName != null) {
                            realId = byName.id
                            resolvedCollection = coll
                        }
                    }
                }

                val resolvedCollectionId = resolvedCollection?.id ?: parsedCollectionId
                val resolvedCollectionName = resolvedCollection?.name ?: parsedCollectionName

                val tab = RequestTabState(
                    id     = realId,
                    requestRef = savedRequestRef,
                    name   = reqName,
                    method = savedMethod,
                    url    = savedUrl,
                    collectionName = resolvedCollectionName,
                    collectionId = resolvedCollectionId,
                    folderPath = parsedFolderPath
                )
                tab.bodyType    = safeEnum(obj["bodyType"]?.jsonPrimitive?.content, BodyType.JSON)
                tab.lastRawSubtype = safeEnum(obj["lastRawSubtype"]?.jsonPrimitive?.content, BodyType.JSON)
                // Only set bodyContent when non-blank. Setting it to "" would insert a
                // blank entry (e.g. "NONE=") into bodyContents, making allBodyContentsSnapshot
                // differ from the saved snapshot that had an empty map → false dirty state.
                obj["bodyContent"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { tab.bodyContent = it }
                obj["bodyContents"]?.jsonObject?.forEach { (typeName, contentEl) ->
                    runCatching { BodyType.valueOf(typeName) }.getOrNull()?.let { type ->
                        tab.bodyContents[type] = contentEl.jsonPrimitive.content
                    }
                }
                tab.authType    = safeEnum(obj["authType"]?.jsonPrimitive?.content, AuthType.NONE)
                tab.authUsername = obj["authUsername"]?.jsonPrimitive?.content ?: ""
                tab.authPassword = obj["authPassword"]?.jsonPrimitive?.content ?: ""
                tab.authToken    = obj["authToken"]?.jsonPrimitive?.content ?: ""
                tab.authApiKey   = obj["authApiKey"]?.jsonPrimitive?.content ?: ""
                tab.authApiValue = obj["authApiValue"]?.jsonPrimitive?.content ?: ""
                tab.preRequestScript = obj["preRequestScript"]?.jsonPrimitive?.content ?: ""
                tab.testScript = obj["testScript"]?.jsonPrimitive?.content ?: ""
                tab.retryEnabled = obj["retryEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
                tab.retryCount = obj["retryCount"]?.jsonPrimitive?.intOrNull ?: 1
                tab.retryDelayMs = obj["retryDelayMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 250L
                tab.lastSavedTimestamp = obj["lastSavedTimestamp"]?.jsonPrimitive?.content?.toLongOrNull()
                tab.kind = runCatching {
                    com.reqlab.core.model.RequestKind.valueOf(obj["kind"]?.jsonPrimitive?.content ?: "HTTP")
                }.getOrDefault(com.reqlab.core.model.RequestKind.HTTP)
                obj["mcp"]?.jsonObject?.let { mcpObj ->
                    tab.mcpConfig = json.decodeFromJsonElement(
                        com.reqlab.core.model.McpConnectionConfig.serializer(),
                        mcpObj,
                    )
                }

                obj["params"]?.jsonArray?.forEach { kv ->
                    tab.params.add(kvFromJson(kv.jsonObject))
                }
                // Prepend saved headers (after the default pair already there)
                val savedHeaders = obj["headers"]?.jsonArray?.map { kvFromJson(it.jsonObject) } ?: emptyList()
                if (savedHeaders.isNotEmpty()) {
                    tab.headers.clear()
                    tab.headers.addAll(savedHeaders)
                }

                // Load form rows BEFORE restoreSavedSnapshot so that recomputeDirty()
                // sees the complete state when comparing against the persisted savedSnapshot.
                // (May be absent in older saves → empty list.)
                obj["formRows"]?.jsonArray?.forEach { el ->
                    tab.formRows.add(formRowFromJson(el.jsonObject))
                }
                obj["urlencodedRows"]?.jsonArray?.forEach { el ->
                    tab.urlencodedRows.add(formRowFromJson(el.jsonObject))
                }

                tab.restoreSavedSnapshot(
                    snapshot = obj["savedSnapshot"]?.jsonPrimitive?.content,
                    legacyDirtyFlag = obj["isDirty"]?.jsonPrimitive?.booleanOrNull ?: false,
                )

                state.openTabs.add(tab)
            }

            val savedIndex = root["activeIndex"]?.jsonPrimitive?.intOrNull ?: 0
            state.activeTabIndex = savedIndex.coerceIn(0, state.openTabs.size - 1)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun formRowListJson(rows: List<MutableFormDataRow>): JsonArray =
        buildJsonArray {
            rows.forEach { r ->
                add(buildJsonObject {
                    put("key",         r.key)
                    put("type",        r.type.name)
                    put("value",       r.value)
                    put("description", r.description)
                    put("enabled",     r.enabled)
                })
            }
        }

    private fun formRowFromJson(obj: JsonObject): MutableFormDataRow =
        MutableFormDataRow(
            key         = obj["key"]?.jsonPrimitive?.content ?: "",
            type        = safeEnum(obj["type"]?.jsonPrimitive?.content, FormEntryType.TEXT),
            value       = obj["value"]?.jsonPrimitive?.content ?: "",
            description = obj["description"]?.jsonPrimitive?.content ?: "",
            enabled     = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
        )

    private fun kvListJson(list: List<MutableKeyValue>): JsonArray =
        buildJsonArray {
            list.forEach { kv ->
                add(buildJsonObject {
                    put("key",     kv.key)
                    put("value",   kv.value)
                    put("enabled", kv.enabled)
                    put("secret",  kv.secret)
                    put("kind", kv.kind.name)
                    put("keyLocked", kv.keyLocked)
                })
            }
        }

    private fun kvFromJson(obj: JsonObject): MutableKeyValue =
        MutableKeyValue(
            key     = obj["key"]?.jsonPrimitive?.content     ?: "",
            value   = obj["value"]?.jsonPrimitive?.content   ?: "",
            enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
            secret  = obj["secret"]?.jsonPrimitive?.booleanOrNull  ?: false,
            kind = safeEnum(obj["kind"]?.jsonPrimitive?.content, HeaderKind.USER),
            keyLocked = obj["keyLocked"]?.jsonPrimitive?.booleanOrNull ?: false,
        )

    private inline fun <reified T : Enum<T>> safeEnum(name: String?, default: T): T =
        if (name == null) default else runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}
