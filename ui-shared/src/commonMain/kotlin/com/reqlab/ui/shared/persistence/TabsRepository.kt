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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
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
                        put("name",        tab.name)
                        put("method",      tab.method.name)
                        put("url",         tab.url)
                        put("collectionName", tab.collectionName ?: "")
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

            // Replace the default single empty tab
            state.openTabs.clear()
            tabsJson.forEach { el ->
                val obj = el.jsonObject
                
                val savedId = obj["id"]?.jsonPrimitive?.content ?: return@forEach
                val parsedCollectionName = obj["collectionName"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                val parsedFolderPath = obj["folderPath"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                val reqName = obj["name"]?.jsonPrimitive?.content ?: "Untitled"
                
                // Find matching request node mapped to the newly generated UUIDs
                var realId = savedId
                if (parsedCollectionName != null) {
                    val coll = state.collections.find { it.name == parsedCollectionName }
                    if (coll != null) {
                        var current: com.reqlab.ui.shared.state.CollectionNode = coll
                        for (folder in parsedFolderPath) {
                            val next = current.children.find { it.name == folder && it.isFolder }
                            if (next != null) {
                                current = next
                            } else {
                                break
                            }
                        }
                        val matchingReq = current.children.find { !it.isFolder && it.name == reqName }
                        if (matchingReq != null) {
                            realId = matchingReq.id
                        }
                    }
                }

                val tab = RequestTabState(
                    id     = realId,
                    name   = reqName,
                    method = safeEnum(obj["method"]?.jsonPrimitive?.content, HttpMethodType.GET),
                    url    = obj["url"]?.jsonPrimitive?.content    ?: "",
                    collectionName = parsedCollectionName,
                    folderPath = parsedFolderPath
                )
                tab.bodyType    = safeEnum(obj["bodyType"]?.jsonPrimitive?.content, BodyType.JSON)
                tab.lastRawSubtype = safeEnum(obj["lastRawSubtype"]?.jsonPrimitive?.content, BodyType.JSON)
                tab.bodyContent = obj["bodyContent"]?.jsonPrimitive?.content ?: ""
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

                obj["params"]?.jsonArray?.forEach { kv ->
                    tab.params.add(kvFromJson(kv.jsonObject))
                }
                // Prepend saved headers (after the default pair already there)
                val savedHeaders = obj["headers"]?.jsonArray?.map { kvFromJson(it.jsonObject) } ?: emptyList()
                if (savedHeaders.isNotEmpty()) {
                    tab.headers.clear()
                    tab.headers.addAll(savedHeaders)
                }

                tab.restoreSavedSnapshot(
                    snapshot = obj["savedSnapshot"]?.jsonPrimitive?.content,
                    legacyDirtyFlag = obj["isDirty"]?.jsonPrimitive?.booleanOrNull ?: false,
                )

                // Load structured form rows (may be absent in older saves → empty list)
                obj["formRows"]?.jsonArray?.forEach { el ->
                    tab.formRows.add(formRowFromJson(el.jsonObject))
                }
                obj["urlencodedRows"]?.jsonArray?.forEach { el ->
                    tab.urlencodedRows.add(formRowFromJson(el.jsonObject))
                }

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
