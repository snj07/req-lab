package com.reqlab.ui.shared.mcp

import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.McpConnectionState
import com.reqlab.core.model.McpGetPromptResult
import com.reqlab.core.model.McpHttpMode
import com.reqlab.core.model.McpInitializeResult
import com.reqlab.core.model.McpLogEntry
import com.reqlab.core.model.McpLogEntryKind
import com.reqlab.core.model.McpPrompt
import com.reqlab.core.model.McpReadResourceResult
import com.reqlab.core.model.McpResource
import com.reqlab.core.model.McpTool
import com.reqlab.core.model.McpToolResult
import com.reqlab.core.model.McpTransportType
import com.reqlab.core.model.ResponseDefinition
import com.reqlab.core.model.ResponseMetrics
import com.reqlab.core.network.mcp.McpClient
import com.reqlab.core.network.mcp.mcpStdioSupported
import com.reqlab.ui.shared.state.LogLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Result of the most recent MCP operation (tool call / resource read / prompt get),
 * shaped so the UI can render it through the shared REST [ResponseViewer].
 */
data class McpOperationResult(
    val kind: String,
    val label: String,
    val bodyJson: String,
    val isError: Boolean,
    val headers: List<KeyValueEntry>,
    val elapsedMs: Long,
    val sizeBytes: Long,
    val timestampMs: Long,
) {
    fun toResponseDefinition(
        requestId: String,
        okStatusText: String = "Success",
        errorStatusText: String = "Error",
    ): ResponseDefinition {
        val code = if (isError) 500 else 200
        return ResponseDefinition(
            requestId = requestId,
            statusCode = code,
            statusText = if (isError) errorStatusText else okStatusText,
            headers = headers,
            cookies = emptyList(),
            bodyText = bodyJson,
            contentType = "application/json",
            executedAtEpochMillis = timestampMs,
            metrics = ResponseMetrics(
                statusCode = code,
                responseTimeMs = elapsedMs,
                responseSizeBytes = sizeBytes,
            ),
        )
    }
}

class McpSessionState(
    private val scope: CoroutineScope,
    /** Optional bridge that forwards MCP activity to the global console. */
    private val onConsole: ((String, LogLevel) -> Unit)? = null,
    private val clientFactory: (CoroutineScope) -> McpClient = { McpClient(it) },
) {
    var client: McpClient? = null
        private set
    private val _connectionState = MutableStateFlow(McpConnectionState.DISCONNECTED)
    val connectionState: StateFlow<McpConnectionState> = _connectionState
    private val _tools = MutableStateFlow<List<McpTool>>(emptyList())
    val tools: StateFlow<List<McpTool>> = _tools
    private val _resources = MutableStateFlow<List<McpResource>>(emptyList())
    val resources: StateFlow<List<McpResource>> = _resources
    private val _prompts = MutableStateFlow<List<McpPrompt>>(emptyList())
    val prompts: StateFlow<List<McpPrompt>> = _prompts
    private val _logs = MutableStateFlow<List<McpLogEntry>>(emptyList())
    val logs: StateFlow<List<McpLogEntry>> = _logs
    private val _lastToolResult = MutableStateFlow<McpToolResult?>(null)
    val lastToolResult: StateFlow<McpToolResult?> = _lastToolResult
    private val _lastToolName = MutableStateFlow<String?>(null)
    val lastToolName: StateFlow<String?> = _lastToolName
    private val _lastResourceResult = MutableStateFlow<McpReadResourceResult?>(null)
    val lastResourceResult: StateFlow<McpReadResourceResult?> = _lastResourceResult
    private val _lastPromptResult = MutableStateFlow<McpGetPromptResult?>(null)
    val lastPromptResult: StateFlow<McpGetPromptResult?> = _lastPromptResult
    private val _lastOperation = MutableStateFlow<McpOperationResult?>(null)
    val lastOperation: StateFlow<McpOperationResult?> = _lastOperation
    private val _subscribedUris = MutableStateFlow<Set<String>>(emptySet())
    val subscribedUris: StateFlow<Set<String>> = _subscribedUris
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _initializeResult = MutableStateFlow<McpInitializeResult?>(null)
    val initializeResult: StateFlow<McpInitializeResult?> = _initializeResult
    var confirmStdio: Boolean = false
    /** Cmd/Ctrl+Enter target set by the MCP panel for the active section. */
    var pendingShortcut: (() -> Unit)? = null
    private var logJob: Job? = null
    private var notifJob: Job? = null
    private var callJob: Job? = null
    private var connectedFingerprint: String? = null

    fun stdioAvailable(): Boolean = mcpStdioSupported

    /** True when the connected server advertises the resources/subscribe capability. */
    fun supportsSubscribe(): Boolean =
        _initializeResult.value?.capabilities?.resources?.subscribe == true

    suspend fun connect(config: McpConnectionConfig, variableLayers: List<Map<String, String>> = emptyList()) {
        if (config.transport == McpTransportType.STDIO && !confirmStdio) {
            _error.value = "Confirm stdio before connecting (local process execution)"
            return
        }
        disconnect()
        val created = clientFactory(scope)
        client = created
        logJob = scope.launch {
            created.logs.collect { entry ->
                _logs.value = (_logs.value + entry).takeLast(200)
                onConsole?.invoke(consoleMessage(entry), consoleLevel(entry.kind))
            }
        }
        notifJob = scope.launch {
            created.notifications.collect { n -> handleNotification(created, n) }
        }
        yield()
        _connectionState.value = McpConnectionState.CONNECTING
        try {
            val init = created.connect(config, variableLayers)
            _initializeResult.value = init
            connectedFingerprint = connectionFingerprint(config)
            _connectionState.value = McpConnectionState.CONNECTED
            if (init.capabilities.tools != null) _tools.value = created.listTools()
            if (init.capabilities.resources != null) _resources.value = created.listResources()
            if (init.capabilities.prompts != null) _prompts.value = created.listPrompts()
            _error.value = null
        } catch (e: Exception) {
            _connectionState.value = McpConnectionState.ERROR
            _error.value = e.message
            throw e
        }
    }

    suspend fun callSelectedTool(name: String, arguments: JsonElement?) {
        runCall("tool", name) {
            val result = client?.callTool(name, arguments) ?: return@runCall null
            _lastToolResult.value = result
            _lastToolName.value = name
            OpBody(latestWireJson() ?: mcpPrettyJson.encodeToString(McpToolResult.serializer(), result), result.isError)
        }
    }

    suspend fun readSelectedResource(uri: String) {
        runCall("resource", uri) {
            val result = client?.readResource(uri) ?: return@runCall null
            _lastResourceResult.value = result
            OpBody(latestWireJson() ?: mcpPrettyJson.encodeToString(McpReadResourceResult.serializer(), result), false)
        }
    }

    suspend fun getSelectedPrompt(name: String, arguments: Map<String, String> = emptyMap()) {
        runCall("prompt", name) {
            val result = client?.getPrompt(name, arguments) ?: return@runCall null
            _lastPromptResult.value = result
            OpBody(latestWireJson() ?: mcpPrettyJson.encodeToString(McpGetPromptResult.serializer(), result), false)
        }
    }

    suspend fun subscribeResource(uri: String) {
        runCall("subscribe", uri) {
            client?.subscribeResource(uri) ?: return@runCall null
            _subscribedUris.value = _subscribedUris.value + uri
            onConsole?.invoke("MCP subscribed to $uri", LogLevel.INFO)
            null
        }
    }

    suspend fun unsubscribeResource(uri: String) {
        runCall("unsubscribe", uri) {
            client?.unsubscribeResource(uri) ?: return@runCall null
            _subscribedUris.value = _subscribedUris.value - uri
            onConsole?.invoke("MCP unsubscribed from $uri", LogLevel.INFO)
            null
        }
    }

    private class OpBody(val bodyJson: String, val isError: Boolean)

    private suspend fun runCall(kind: String, label: String, block: suspend () -> OpBody?) {
        _busy.value = true
        _error.value = null
        val start = Clock.System.now().toEpochMilliseconds()
        try {
            val body = block() ?: return
            val elapsed = Clock.System.now().toEpochMilliseconds() - start
            _lastOperation.value = McpOperationResult(
                kind = kind,
                label = label,
                bodyJson = body.bodyJson,
                isError = body.isError,
                headers = client?.lastResponseHeaders?.toKeyValueEntries().orEmpty(),
                elapsedMs = elapsed,
                sizeBytes = body.bodyJson.length.toLong(),
                timestampMs = Clock.System.now().toEpochMilliseconds(),
            )
        } catch (e: CancellationException) {
            _busy.value = false
            throw e
        } catch (e: Exception) {
            val elapsed = Clock.System.now().toEpochMilliseconds() - start
            val msg = e.message ?: e.toString()
            _error.value = msg
            onConsole?.invoke("MCP \u2717 $msg", LogLevel.ERROR)
            _lastOperation.value = McpOperationResult(
                kind = kind,
                label = label,
                bodyJson = latestWireJson() ?: mcpPrettyJson.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject { put("error", msg) },
                ),
                isError = true,
                headers = client?.lastResponseHeaders?.toKeyValueEntries().orEmpty(),
                elapsedMs = elapsed,
                sizeBytes = 0,
                timestampMs = Clock.System.now().toEpochMilliseconds(),
            )
        } finally {
            _busy.value = false
        }
    }

    private suspend fun handleNotification(activeClient: McpClient, notification: com.reqlab.core.model.JsonRpcEnvelope) {
        if (notification.method != "notifications/resources/updated") return
        val uri = (notification.params as? JsonObject)?.get("uri")?.jsonPrimitive?.contentOrNull ?: return
        onConsole?.invoke("MCP resource updated: $uri", LogLevel.INFO)
        if (uri !in _subscribedUris.value) return
        runCatching {
            val result = activeClient.readResource(uri)
            _lastResourceResult.value = result
            _lastOperation.value = McpOperationResult(
                kind = "resource",
                label = uri,
                bodyJson = mcpPrettyWireJson(activeClient.lastReceivedPayload.orEmpty())
                    .ifBlank { mcpPrettyJson.encodeToString(McpReadResourceResult.serializer(), result) },
                isError = false,
                headers = activeClient.lastResponseHeaders?.toKeyValueEntries().orEmpty(),
                elapsedMs = 0,
                sizeBytes = 0,
                timestampMs = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    fun launchCall(block: suspend () -> Unit) {
        callJob?.cancel()
        callJob = scope.launch {
            try {
                block()
            } catch (_: CancellationException) {
                _busy.value = false
            }
        }
    }

    fun cancelCall() {
        callJob?.cancel()
        callJob = null
        _busy.value = false
    }

    fun isReconnectNeeded(config: McpConnectionConfig): Boolean =
        when (_connectionState.value) {
            McpConnectionState.ERROR -> true
            McpConnectionState.CONNECTED ->
                connectedFingerprint != null && connectedFingerprint != connectionFingerprint(config)
            else -> false
        }

    suspend fun disconnect() {
        callJob?.cancel()
        callJob = null
        logJob?.cancel()
        logJob = null
        notifJob?.cancel()
        notifJob = null
        runCatching { client?.disconnect() }
        client = null
        connectedFingerprint = null
        _connectionState.value = McpConnectionState.DISCONNECTED
        _tools.value = emptyList()
        _resources.value = emptyList()
        _prompts.value = emptyList()
        _initializeResult.value = null
        _lastToolResult.value = null
        _lastToolName.value = null
        _lastResourceResult.value = null
        _lastPromptResult.value = null
        _lastOperation.value = null
        _subscribedUris.value = emptySet()
        _busy.value = false
    }

    fun negotiatedLabel(): String {
        val init = _initializeResult.value ?: return ""
        val mode = client?.negotiatedHttpMode ?: McpHttpMode.AUTO
        return "${init.protocolVersion} \u00B7 $mode \u00B7 ${init.serverInfo.name}"
    }

    fun sessionId(): String? = client?.sessionId

    private fun latestWireJson(): String? =
        client?.lastReceivedPayload?.takeIf { it.isNotBlank() }?.let(::mcpPrettyWireJson)

    private fun consoleLevel(kind: McpLogEntryKind): LogLevel = when (kind) {
        McpLogEntryKind.ERROR -> LogLevel.ERROR
        McpLogEntryKind.RECEIVED -> LogLevel.SUCCESS
        else -> LogLevel.INFO
    }

    private fun consoleMessage(entry: McpLogEntry): String {
        val marker = when (entry.kind) {
            McpLogEntryKind.SENT -> "\u2192"
            McpLogEntryKind.RECEIVED -> "\u2190"
            McpLogEntryKind.NOTIFICATION -> "\u25C8"
            McpLogEntryKind.ERROR -> "\u2717"
            McpLogEntryKind.STATE -> "\u2022"
            McpLogEntryKind.OAUTH -> "\u26BF"
        }
        return "MCP $marker ${entry.summary}"
    }
}

private fun Map<String, List<String>>.toKeyValueEntries(): List<KeyValueEntry> =
    entries.flatMap { (key, values) -> values.map { KeyValueEntry(key = key, value = it) } }

internal val mcpPrettyJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

/** Pretty-print a JSON-RPC wire frame without re-encoding through MCP data classes. */
internal val mcpWirePretty = Json {
    prettyPrint = true
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

internal fun mcpPrettyWireJson(raw: String): String =
    runCatching {
        mcpWirePretty.encodeToString(JsonElement.serializer(), mcpWirePretty.parseToJsonElement(raw))
    }.getOrDefault(raw)

internal fun mcpDefaultArgsJson(schema: JsonObject): String {
    val properties = schema["properties"] as? JsonObject
    if (properties.isNullOrEmpty()) return "{}"
    val obj = buildJsonObject {
        properties.forEach { (key, spec) ->
            val type = ((spec as? JsonObject)?.get("type") as? JsonPrimitive)?.content
            when (type) {
                "number", "integer" -> put(key, 0)
                "boolean" -> put(key, false)
                "object" -> put(key, buildJsonObject {})
                "array" -> put(key, buildJsonArray {})
                else -> put(key, "")
            }
        }
    }
    return mcpPrettyJson.encodeToString(JsonObject.serializer(), obj)
}

internal data class McpSchemaField(
    val name: String,
    val type: String,
    val required: Boolean,
    val enumValues: List<String> = emptyList(),
    val title: String? = null,
)

internal fun mcpSchemaFields(schema: JsonObject): List<McpSchemaField> {
    val properties = schema["properties"] as? JsonObject ?: return emptyList()
    val required = (schema["required"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?.toSet()
        .orEmpty()
    return properties.entries.map { (key, spec) ->
        val obj = spec as? JsonObject
        val type = (obj?.get("type") as? JsonPrimitive)?.content ?: "string"
        val enums = (obj?.get("enum") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            .orEmpty()
        val title = (obj?.get("title") as? JsonPrimitive)?.content
        McpSchemaField(
            name = key,
            type = type,
            required = key in required,
            enumValues = enums,
            title = title,
        )
    }
}

internal fun mcpSchemaFormSupported(schema: JsonObject): Boolean {
    val fields = mcpSchemaFields(schema)
    if (fields.isEmpty()) return false
    return fields.all { field ->
        field.enumValues.isNotEmpty() || field.type in setOf("string", "number", "integer", "boolean")
    }
}

internal fun mcpMissingRequiredArgs(schema: JsonObject, argsJson: String): List<String> {
    val obj = runCatching { mcpPrettyJson.parseToJsonElement(argsJson) }.getOrNull() as? JsonObject
        ?: JsonObject(emptyMap())
    return mcpSchemaFields(schema).filter { it.required }.mapNotNull { field ->
        val value = obj[field.name]
        val missing = when {
            value == null -> true
            value is JsonPrimitive && value.isString && value.content.isBlank() -> true
            else -> false
        }
        field.name.takeIf { missing }
    }
}

internal fun mcpArgsGet(argsJson: String, key: String): JsonElement? {
    val obj = runCatching { mcpPrettyJson.parseToJsonElement(argsJson) }.getOrNull() as? JsonObject
    return obj?.get(key)
}

internal fun mcpArgsPut(argsJson: String, key: String, value: JsonElement): String {
    val obj = runCatching { mcpPrettyJson.parseToJsonElement(argsJson) }.getOrNull() as? JsonObject
        ?: JsonObject(emptyMap())
    val next = buildJsonObject {
        obj.forEach { (k, v) -> if (k != key) put(k, v) }
        put(key, value)
    }
    return mcpPrettyJson.encodeToString(JsonObject.serializer(), next)
}

internal fun mcpToolHintChips(annotations: JsonObject?): List<String> {
    if (annotations == null) return emptyList()
    fun flag(name: String): Boolean {
        val prim = annotations[name] as? JsonPrimitive ?: return false
        return prim.booleanOrNull == true || prim.content.equals("true", ignoreCase = true)
    }
    return buildList {
        if (flag("readOnlyHint")) add("readOnly")
        if (flag("destructiveHint")) add("destructive")
    }
}

internal fun mcpPromptSchema(prompt: McpPrompt): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonArray("required") {
        prompt.arguments.filter { it.required == true }.forEach { add(JsonPrimitive(it.name)) }
    }
    put("properties", buildJsonObject {
        prompt.arguments.forEach { arg ->
            put(arg.name, buildJsonObject {
                put("type", "string")
                arg.description?.let { put("description", it) }
            })
        }
    })
}

internal fun connectionFingerprint(config: McpConnectionConfig): String =
    listOf(
        config.transport.name,
        config.httpMode.name,
        config.url,
        config.command,
        config.auth.type.name,
        config.auth.params.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" },
        config.headers.filter { it.enabled && it.key.isNotBlank() }.joinToString { "${it.key}=${it.value}" },
    ).joinToString("|")

internal fun mcpParseScalar(raw: String, type: String): JsonElement {
    if (raw.contains("{{")) return JsonPrimitive(raw)
    return when (type) {
        "integer" -> raw.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
        "number" -> raw.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
        "boolean" -> when (raw.lowercase()) {
            "true" -> JsonPrimitive(true)
            "false" -> JsonPrimitive(false)
            else -> JsonPrimitive(raw)
        }
        else -> JsonPrimitive(raw)
    }
}
