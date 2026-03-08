package com.reqlab.ui.shared.persistence

import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.AuthType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.HeaderKind
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

    fun save(state: AppState) {
        runCatching {
            val arr = buildJsonArray {
                state.openTabs.forEach { tab ->
                    add(buildJsonObject {
                        put("id",          tab.id)
                        put("name",        tab.name)
                        put("method",      tab.method.name)
                        put("url",         tab.url)
                        put("bodyType",    tab.bodyType.name)
                        put("bodyContent", tab.bodyContent)
                        put("authType",    tab.authType.name)
                        put("authUsername", tab.authUsername)
                        put("authPassword", tab.authPassword)
                        put("authToken",    tab.authToken)
                        put("authApiKey",   tab.authApiKey)
                        put("authApiValue", tab.authApiValue)
                        put("preRequestScript", tab.preRequestScript)
                        put("testScript", tab.testScript)
                        put("retryCount", tab.retryCount)
                        put("retryDelayMs", tab.retryDelayMs)
                        tab.lastSavedTimestamp?.let { put("lastSavedTimestamp", it) }
                        put("savedSnapshot", tab.savedSnapshotForPersistence())
                        put("isDirty",     tab.isDirty)
                        put("params",  kvListJson(tab.params))
                        put("headers", kvListJson(tab.headers))
                    })
                }
            }
            val root = buildJsonObject {
                put("activeIndex", state.activeTabIndex)
                put("tabs", arr)
            }
            PlatformStorage.putString(STORAGE_KEY, root.toString())
        }
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
                val tab = RequestTabState(
                    id     = obj["id"]?.jsonPrimitive?.content ?: return@forEach,
                    name   = obj["name"]?.jsonPrimitive?.content   ?: "Untitled",
                    method = safeEnum(obj["method"]?.jsonPrimitive?.content, HttpMethodType.GET),
                    url    = obj["url"]?.jsonPrimitive?.content    ?: "",
                )
                tab.bodyType    = safeEnum(obj["bodyType"]?.jsonPrimitive?.content, BodyType.JSON)
                tab.bodyContent = obj["bodyContent"]?.jsonPrimitive?.content ?: ""
                tab.authType    = safeEnum(obj["authType"]?.jsonPrimitive?.content, AuthType.NONE)
                tab.authUsername = obj["authUsername"]?.jsonPrimitive?.content ?: ""
                tab.authPassword = obj["authPassword"]?.jsonPrimitive?.content ?: ""
                tab.authToken    = obj["authToken"]?.jsonPrimitive?.content ?: ""
                tab.authApiKey   = obj["authApiKey"]?.jsonPrimitive?.content ?: ""
                tab.authApiValue = obj["authApiValue"]?.jsonPrimitive?.content ?: ""
                tab.preRequestScript = obj["preRequestScript"]?.jsonPrimitive?.content ?: ""
                tab.testScript = obj["testScript"]?.jsonPrimitive?.content ?: ""
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

                state.openTabs.add(tab)
            }

            val savedIndex = root["activeIndex"]?.jsonPrimitive?.intOrNull ?: 0
            state.activeTabIndex = savedIndex.coerceIn(0, state.openTabs.size - 1)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

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
