package com.reqlab.ui.shared.persistence

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Imports Postman Collection v2 / v2.1 and Postman Environment JSON files into ReqLab format.
 *
 * Detection:
 *  - Collection: `info.schema` field contains "schema.getpostman.com"
 *  - Environment: top-level `values` array is present and the file is not a reqLab-typed doc
 *
 * Script conversion: Postman's `pm.*` namespace is replaced with `reqlab.*`.
 */
object PostmanImporter {

    private const val POSTMAN_SCHEMA_FRAGMENT = "schema.getpostman.com"

    // ── Detection ──────────────────────────────────────────────────────────────

    /**
     * Returns true if [root] looks like a Postman collection (v2 or v2.1),
     * including wrapped payloads where `root["collection"]` holds the actual data.
     */
    fun isPostmanCollection(root: JsonObject): Boolean {
        val inner = root["collection"]?.jsonObject ?: root
        val schema = inner["info"]?.jsonObject?.get("schema")?.jsonPrimitive?.contentOrNull
        return schema?.contains(POSTMAN_SCHEMA_FRAGMENT) == true
    }

    /**
     * Returns true if [root] looks like a Postman environment export.
     * A Postman environment has a `values` array and a `name`, and is not typed as a reqLab document.
     */
    fun isPostmanEnvironment(root: JsonObject): Boolean {
        val notReqLab = root["type"]?.jsonPrimitive?.contentOrNull?.startsWith("reqLab") != true
        val hasValues = root["values"] is JsonArray
        val hasName = root["name"]?.jsonPrimitive?.contentOrNull != null
        return notReqLab && hasValues && hasName
    }

    // ── Collection import ──────────────────────────────────────────────────────

    /**
     * Converts a Postman collection JSON object to [ReqLabCollectionDto].
     *
     * @throws ImportExportException if required fields are absent.
     */
    fun importCollection(root: JsonObject): ReqLabCollectionDto {
        val collectionRoot = root["collection"]?.jsonObject ?: root
        val info = collectionRoot["info"]?.jsonObject
            ?: throw ImportExportException("Missing 'info' in Postman collection")
        val name = info["name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw ImportExportException("Missing collection name in Postman JSON")
        val items = collectionRoot["item"]?.jsonArray ?: JsonArray(emptyList())
        val (folders, requests) = parseItems(items)
        return ReqLabCollectionDto(name = name, folders = folders, requests = requests)
    }

    private fun parseItems(items: JsonArray): Pair<List<FolderDto>, List<RequestDto>> {
        val folders = mutableListOf<FolderDto>()
        val requests = mutableListOf<RequestDto>()
        for (element in items) {
            val obj = runCatching { element.jsonObject }.getOrNull() ?: continue
            when {
                obj.containsKey("item") -> folders.add(parseFolder(obj))
                obj.containsKey("request") -> parseRequest(obj)?.let { requests.add(it) }
                // Items with neither "item" nor "request" are ignored
            }
        }
        return folders to requests
    }

    private fun parseFolder(obj: JsonObject): FolderDto {
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: "Unnamed Folder"
        val childItems = obj["item"]?.jsonArray ?: JsonArray(emptyList())
        val (subFolders, requests) = parseItems(childItems)
        return FolderDto(name = name, folders = subFolders, requests = requests)
    }

    private fun parseRequest(obj: JsonObject): RequestDto? {
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: "Unnamed Request"
        val requestObj = obj["request"]?.jsonObject ?: return null
        val method = normalizeMethod(requestObj["method"]?.jsonPrimitive?.contentOrNull)
        val url = parseUrl(requestObj["url"])
        val headers = parseHeaders(requestObj["header"])
        val (bodyType, bodyContent) = parseBody(requestObj["body"], headers)
        val auth = parseAuth(requestObj["auth"])
        val (preRequestScript, testScript) = parseEvents(obj["event"])
        return RequestDto(
            name = name,
            method = method,
            url = url,
            preRequestScript = preRequestScript.takeIf { it.isNotBlank() },
            testScript = testScript.takeIf { it.isNotBlank() },
            userHeaders = headers,
            bodyType = bodyType,
            bodyContent = bodyContent,
            authType = auth.type,
            authUsername = auth.username,
            authPassword = auth.password,
            authToken = auth.token,
            authApiKey = auth.apiKey,
            authApiValue = auth.apiValue,
        )
    }

    private fun normalizeMethod(raw: String?): String = when (raw?.uppercase()) {
        "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD" -> raw!!.uppercase()
        else -> "GET"
    }

    private fun parseUrl(urlElement: JsonElement?): String {
        if (urlElement == null) return ""
        if (urlElement is JsonPrimitive) return urlElement.contentOrNull ?: ""
        val urlObj = runCatching { urlElement.jsonObject }.getOrNull() ?: return ""
        // raw field contains the full URL, including query string and {{variable}} placeholders
        val raw = urlObj["raw"]?.jsonPrimitive?.contentOrNull
        if (!raw.isNullOrBlank()) return raw
        // Reconstruct from host + path + query when raw is absent
        val protocol = urlObj["protocol"]?.jsonPrimitive?.contentOrNull ?: "https"
        val host = urlObj["host"]?.jsonArray?.mapNotNull { hostPart(it) }?.joinToString(".") ?: ""
        val path = urlObj["path"]?.jsonArray?.mapNotNull { pathPart(it) }?.joinToString("/") ?: ""
        val port = urlObj["port"]?.jsonPrimitive?.contentOrNull
        val hostPart = if (port != null) "$host:$port" else host
        val query = urlObj["query"]?.jsonArray?.mapNotNull { el ->
            val q = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val disabled = q["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
            if (disabled) return@mapNotNull null
            val key = q["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = q["value"]?.jsonPrimitive?.contentOrNull ?: ""
            "$key=$value"
        }?.joinToString("&")
        val pathWithQuery = if (!query.isNullOrBlank()) "$path?$query" else path
        return if (hostPart.isNotEmpty()) "$protocol://$hostPart/$pathWithQuery" else pathWithQuery
    }

    private fun hostPart(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> element["value"]?.jsonPrimitive?.contentOrNull
            else -> null
        }

    private fun pathPart(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.trim('/')
            is JsonObject -> element["value"]?.jsonPrimitive?.contentOrNull?.trim('/')
            else -> null
        }

    private fun parseHeaders(headerElement: JsonElement?): List<Pair<String, String>> {
        val array = runCatching { headerElement?.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
            if (disabled) return@mapNotNull null
            val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (key.isBlank()) return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
            key to value
        }
    }

    private data class ParsedBody(val type: String?, val content: String?)

    private fun parseBody(bodyElement: JsonElement?, headers: List<Pair<String, String>>): Pair<String?, String?> {
        if (bodyElement == null || bodyElement is JsonNull) return null to null
        val bodyObj = runCatching { bodyElement.jsonObject }.getOrNull() ?: return null to null
        val mode = bodyObj["mode"]?.jsonPrimitive?.contentOrNull ?: return null to null
        return when (mode.lowercase()) {
            "raw" -> {
                val raw = bodyObj["raw"]?.jsonPrimitive?.contentOrNull ?: ""
                val language = bodyObj["options"]?.jsonObject
                    ?.get("raw")?.jsonObject
                    ?.get("language")?.jsonPrimitive?.contentOrNull ?: "text"
                val bodyType = if (isJsonRawBody(language, raw, headers)) "JSON" else "RAW_TEXT"
                bodyType to raw
            }
            "formdata" -> {
                val entries = bodyObj["formdata"]?.jsonArray?.mapNotNull { el ->
                    val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (disabled) return@mapNotNull null
                    val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
                    "$key=$value"
                }?.joinToString("\n") ?: ""
                "FORM_DATA" to entries.ifEmpty { null }
            }
            "urlencoded" -> {
                val entries = bodyObj["urlencoded"]?.jsonArray?.mapNotNull { el ->
                    val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (disabled) return@mapNotNull null
                    val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
                    "$key=$value"
                }?.joinToString("&") ?: ""
                "X_WWW_FORM_URLENCODED" to entries.ifEmpty { null }
            }
            "binary", "file" -> "BINARY" to null
            "graphql" -> {
                val query = bodyObj["graphql"]?.jsonObject
                    ?.get("query")?.jsonPrimitive?.contentOrNull ?: ""
                "GRAPHQL" to query
            }
            "none" -> null to null
            else -> "RAW_TEXT" to (bodyObj["raw"]?.jsonPrimitive?.contentOrNull)
        }
    }

    private fun isJsonRawBody(language: String?, raw: String, headers: List<Pair<String, String>>): Boolean {
        if (language.equals("json", ignoreCase = true)) return true

        val contentType = headers
            .firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }
            ?.second
            ?.lowercase()
            ?: ""
        if ("json" in contentType || "+json" in contentType) return true

        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

    private data class ParsedAuth(
        val type: String?,
        val username: String?,
        val password: String?,
        val token: String?,
        val apiKey: String?,
        val apiValue: String?,
    )

    private val noAuth = ParsedAuth(null, null, null, null, null, null)

    private fun parseAuth(authElement: JsonElement?): ParsedAuth {
        if (authElement == null || authElement is JsonNull) return noAuth
        val authObj = runCatching { authElement.jsonObject }.getOrNull() ?: return noAuth
        val type = authObj["type"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return noAuth
        return when (type) {
            "bearer" -> {
                val token = authObj["bearer"]?.jsonArray?.kvValue("token")
                ParsedAuth("BEARER", null, null, token, null, null)
            }
            "basic" -> {
                val username = authObj["basic"]?.jsonArray?.kvValue("username")
                val password = authObj["basic"]?.jsonArray?.kvValue("password")
                ParsedAuth("BASIC", username, password, null, null, null)
            }
            "apikey" -> {
                // Postman apikey: key = header name, value = header value
                val apiKey = authObj["apikey"]?.jsonArray?.kvValue("key")
                val apiValue = authObj["apikey"]?.jsonArray?.kvValue("value")
                ParsedAuth("API_KEY", null, null, null, apiKey, apiValue)
            }
            "noauth", "none" -> ParsedAuth("NONE", null, null, null, null, null)
            else -> noAuth
        }
    }

    /** Finds the `value` of the entry whose `key` field equals [keyName] in a Postman [{key,value}] array. */
    private fun JsonArray.kvValue(keyName: String): String? =
        mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            if (obj["key"]?.jsonPrimitive?.contentOrNull == keyName)
                obj["value"]?.jsonPrimitive?.contentOrNull
            else null
        }.firstOrNull()

    private fun parseEvents(eventElement: JsonElement?): Pair<String, String> {
        val events = runCatching { eventElement?.jsonArray }.getOrNull() ?: return "" to ""
        var preRequest = ""
        var test = ""
        for (event in events) {
            val obj = runCatching { event.jsonObject }.getOrNull() ?: continue
            val listen = obj["listen"]?.jsonPrimitive?.contentOrNull ?: continue
            val scriptObj = obj["script"]?.jsonObject ?: continue
            val exec = when (val execEl = scriptObj["exec"]) {
                is JsonArray -> execEl.mapNotNull {
                    runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
                }.joinToString("\n")
                is JsonPrimitive -> execEl.contentOrNull ?: ""
                else -> continue
            }
            val converted = convertScript(exec)
            when (listen.lowercase()) {
                "prerequest" -> preRequest = converted
                "test" -> test = converted
            }
        }
        return preRequest to test
    }

    // ── Script conversion ──────────────────────────────────────────────────────

    /**
     * Converts Postman `pm.*` API calls to the ReqLab `reqlab.*` equivalent.
     *
     * Only the namespace prefix and well-known aliases are rewritten; all other
     * JavaScript is left unchanged so the converted script runs unmodified.
     */
    fun convertScript(script: String): String {
        if (script.isBlank()) return script
        return script
            // ── Modern pm.* API ────────────────────────────────────────────────

            // Test / assertion
            .replace("pm.test(", "reqlab.test(")
            .replace("pm.expect(", "reqlab.expect(")
            // Response
            .replace("pm.response", "reqlab.response")
            // Request (pre-request mutations)
            .replace("pm.request", "reqlab.request")
            // Environment variables
            .replace("pm.environment.get(", "reqlab.environment.get(")
            .replace("pm.environment.set(", "reqlab.environment.set(")
            .replace("pm.environment.unset(", "reqlab.environment.unset(")
            // Global variables → map to environment for ReqLab compatibility
            .replace("pm.globals.get(", "reqlab.environment.get(")
            .replace("pm.globals.set(", "reqlab.environment.set(")
            .replace("pm.globals.unset(", "reqlab.environment.unset(")
            // Variable resolution shorthand
            .replace("pm.variables.get(", "reqlab.environment.get(")
            // Collection variables → map to environment
            .replace("pm.collectionVariables.get(", "reqlab.environment.get(")
            .replace("pm.collectionVariables.set(", "reqlab.environment.set(")
            .replace("pm.collectionVariables.unset(", "reqlab.environment.unset(")
            // sendRequest is not supported — comment it out
            .replace("pm.sendRequest(", "// pm.sendRequest is not supported in ReqLab\n// pm.sendRequest(")

            // ── Legacy postman.* API (pre-pm era) ─────────────────────────────
            // Old Postman sandbox used `postman.*` before the `pm.*` namespace was
            // introduced. Collections exported from older Postman versions still use it.

            // Environment variable helpers
            .replace("postman.getEnvironmentVariable(", "reqlab.environment.get(")
            .replace("postman.setEnvironmentVariable(", "reqlab.environment.set(")
            .replace("postman.clearEnvironmentVariable(", "reqlab.environment.unset(")
            .replace("postman.clearEnvironmentVariables(", "reqlab.environment.clear(")
            // Global variable helpers (map to environment scope in ReqLab)
            .replace("postman.getGlobalVariable(", "reqlab.environment.get(")
            .replace("postman.setGlobalVariable(", "reqlab.environment.set(")
            .replace("postman.clearGlobalVariable(", "reqlab.environment.unset(")
            .replace("postman.clearGlobalVariables(", "reqlab.environment.clear(")
            // setNextRequest — collection runner flow control; not applicable outside a runner
            .replace("postman.setNextRequest(", "// postman.setNextRequest is not supported in ReqLab\n// postman.setNextRequest(")

            // ── Legacy global sandbox shortcuts ───────────────────────────────
            // responseCode.code / responseCode.name before responseCode itself
            .replace("responseCode.code", "response.code")
            .replace("responseCode.name", "response.status")
            // responseBody as a standalone word (avoid matching e.g. responseBodyData)
            .replace(Regex("""\bresponseBody\b"""), "response.text()")
    }

    // ── Environment import ──────────────────────────────────────────────────────

    /**
     * Converts a Postman environment JSON object to [ReqLabEnvironmentDto].
     * Disabled variables are skipped.
     *
     * @throws ImportExportException if required fields are absent.
     */
    fun importEnvironment(root: JsonObject): ReqLabEnvironmentDto {
        val name = root["name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw ImportExportException("Missing environment name in Postman JSON")
        val values = root["values"]?.jsonArray?.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
            if (!enabled) return@mapNotNull null
            val key = obj["key"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
            if (key.isEmpty()) return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
            key to value
        } ?: emptyList()
        return ReqLabEnvironmentDto(name = name, variables = values.toMap())
    }
}
