package com.reqlab.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val MCP_PROTOCOL_VERSION = "2025-06-18"
const val MCP_PROTOCOL_VERSION_LEGACY = "2024-11-05"

object JsonRpcErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

/**
 * Generic JSON-RPC 2.0 envelope. Frames are classified by field presence:
 * request = method + id, notification = method and no id, response = id + result/error.
 */
@Serializable
data class JsonRpcEnvelope(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String? = null,
    val params: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
) {
    fun isNotification(): Boolean = method != null && id == null
    fun isRequest(): Boolean = method != null && id != null && id !is JsonNull
    fun isResponse(): Boolean = method == null && id != null && id !is JsonNull
    fun idKey(): String? = jsonRpcIdKey(id)
}

fun jsonRpcIdKey(id: JsonElement?): String? {
    if (id == null || id is JsonNull) return null
    val primitive = id as? JsonPrimitive ?: return id.toString()
    return primitive.content
}

fun jsonRpcId(value: String): JsonPrimitive = JsonPrimitive(value)
fun jsonRpcId(value: Long): JsonPrimitive = JsonPrimitive(value)
fun jsonRpcId(value: Int): JsonPrimitive = JsonPrimitive(value)

@Serializable
enum class RequestKind { HTTP, MCP }

@Serializable
enum class McpTransportType { STREAMABLE_HTTP, STDIO }

@Serializable
enum class McpHttpMode { AUTO, STREAMABLE_2025_06_18, LEGACY_2024_11_05 }

@Serializable
enum class McpSamplingMode { MANUAL, MOCK, FORWARD_LLM }

@Serializable
enum class McpConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

@Serializable
data class McpImplementation(
    val name: String,
    val version: String,
    val title: String? = null,
)

@Serializable
data class McpToolsCapability(
    val listChanged: Boolean? = null,
)

@Serializable
data class McpResourcesCapability(
    val subscribe: Boolean? = null,
    val listChanged: Boolean? = null,
)

@Serializable
data class McpPromptsCapability(
    val listChanged: Boolean? = null,
)

@Serializable
class McpLoggingCapability

@Serializable
class McpCompletionsCapability

@Serializable
class McpSamplingCapability

@Serializable
data class McpRootsCapability(
    val listChanged: Boolean? = null,
)

@Serializable
class McpElicitationCapability

@Serializable
data class McpClientCapabilities(
    val sampling: McpSamplingCapability? = McpSamplingCapability(),
    val roots: McpRootsCapability? = McpRootsCapability(listChanged = true),
    val elicitation: McpElicitationCapability? = McpElicitationCapability(),
)

@Serializable
data class McpServerCapabilities(
    val tools: McpToolsCapability? = null,
    val resources: McpResourcesCapability? = null,
    val prompts: McpPromptsCapability? = null,
    val logging: McpLoggingCapability? = null,
    val completions: McpCompletionsCapability? = null,
)

@Serializable
data class McpInitializeParams(
    val protocolVersion: String = MCP_PROTOCOL_VERSION,
    val capabilities: McpClientCapabilities = McpClientCapabilities(),
    val clientInfo: McpImplementation = McpImplementation(name = "ReqLab", version = "1.18.0"),
)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String = MCP_PROTOCOL_VERSION,
    val capabilities: McpServerCapabilities = McpServerCapabilities(),
    val serverInfo: McpImplementation = McpImplementation(name = "unknown", version = "0"),
    val instructions: String? = null,
)

@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject = JsonObject(emptyMap()),
    val title: String? = null,
    val annotations: JsonObject? = null,
)

@Serializable
data class McpListToolsResult(
    val tools: List<McpTool> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
    val title: String? = null,
    val size: Long? = null,
)

@Serializable
data class McpResourceTemplate(
    val uriTemplate: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
    val title: String? = null,
)

@Serializable
data class McpListResourcesResult(
    val resources: List<McpResource> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class McpListResourceTemplatesResult(
    val resourceTemplates: List<McpResourceTemplate> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class McpPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean? = null,
)

@Serializable
data class McpPrompt(
    val name: String,
    val description: String? = null,
    val arguments: List<McpPromptArgument> = emptyList(),
    val title: String? = null,
)

@Serializable
data class McpListPromptsResult(
    val prompts: List<McpPrompt> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
enum class McpContentType {
    @SerialName("text") TEXT,
    @SerialName("image") IMAGE,
    @SerialName("audio") AUDIO,
    @SerialName("resource") RESOURCE,
    @SerialName("resource_link") RESOURCE_LINK,
}

@Serializable
data class McpContent(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
    val uri: String? = null,
    val name: String? = null,
    val description: String? = null,
    val resource: JsonObject? = null,
    val annotations: JsonObject? = null,
)

@Serializable
data class McpToolResult(
    val content: List<McpContent> = emptyList(),
    val isError: Boolean = false,
    val structuredContent: JsonElement? = null,
)

@Serializable
data class McpReadResourceResult(
    val contents: List<McpResourceContents> = emptyList(),
)

@Serializable
data class McpResourceContents(
    val uri: String,
    val mimeType: String? = null,
    val text: String? = null,
    val blob: String? = null,
)

@Serializable
data class McpGetPromptResult(
    val description: String? = null,
    val messages: List<McpPromptMessage> = emptyList(),
)

@Serializable
data class McpPromptMessage(
    val role: String,
    val content: McpContent,
)

@Serializable
data class McpRoot(
    val uri: String,
    val name: String? = null,
)

@Serializable
data class McpListRootsResult(
    val roots: List<McpRoot> = emptyList(),
)

@Serializable
data class McpSamplingMessage(
    val role: String,
    val content: McpContent,
)

@Serializable
data class McpModelPreferences(
    val hints: List<McpModelHint> = emptyList(),
    val costPriority: Double? = null,
    val speedPriority: Double? = null,
    val intelligencePriority: Double? = null,
)

@Serializable
data class McpModelHint(
    val name: String? = null,
)

@Serializable
data class McpCreateMessageRequest(
    val messages: List<McpSamplingMessage> = emptyList(),
    val modelPreferences: McpModelPreferences? = null,
    val systemPrompt: String? = null,
    val includeContext: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int = 256,
    val stopSequences: List<String>? = null,
    val metadata: JsonObject? = null,
)

@Serializable
data class McpCreateMessageResult(
    val role: String = "assistant",
    val content: McpContent = McpContent(type = "text", text = ""),
    val model: String = "mock",
    val stopReason: String? = "endTurn",
)

@Serializable
data class McpElicitRequest(
    val message: String,
    val requestedSchema: JsonObject = JsonObject(emptyMap()),
)

@Serializable
enum class McpElicitAction {
    @SerialName("accept") ACCEPT,
    @SerialName("decline") DECLINE,
    @SerialName("cancel") CANCEL,
}

@Serializable
data class McpElicitResult(
    val action: McpElicitAction = McpElicitAction.DECLINE,
    val content: JsonObject? = null,
)

@Serializable
data class McpProgressNotification(
    val progressToken: JsonElement,
    val progress: Double,
    val total: Double? = null,
    val message: String? = null,
)

@Serializable
enum class McpLogLevel {
    @SerialName("debug") DEBUG,
    @SerialName("info") INFO,
    @SerialName("notice") NOTICE,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR,
    @SerialName("critical") CRITICAL,
    @SerialName("alert") ALERT,
    @SerialName("emergency") EMERGENCY,
}

@Serializable
data class McpLoggingMessageNotification(
    val level: McpLogLevel = McpLogLevel.INFO,
    val logger: String? = null,
    val data: JsonElement? = null,
)

@Serializable
data class McpCompleteRequest(
    val ref: JsonObject,
    val argument: McpCompleteArgument,
)

@Serializable
data class McpCompleteArgument(
    val name: String,
    val value: String,
)

@Serializable
data class McpCompleteResult(
    val completion: McpCompletion,
)

@Serializable
data class McpCompletion(
    val values: List<String> = emptyList(),
    val total: Int? = null,
    val hasMore: Boolean? = null,
)

@Serializable
enum class McpLogEntryKind { SENT, RECEIVED, NOTIFICATION, STATE, ERROR, OAUTH }

@Serializable
data class McpLogEntry(
    val timestampEpochMillis: Long,
    val kind: McpLogEntryKind,
    val summary: String,
    val payload: String? = null,
    val method: String? = null,
    val id: String? = null,
)

@Serializable
data class McpConnectionConfig(
    val transport: McpTransportType = McpTransportType.STREAMABLE_HTTP,
    val httpMode: McpHttpMode = McpHttpMode.AUTO,
    val url: String = "",
    val headers: List<KeyValueEntry> = emptyList(),
    val auth: AuthConfig = AuthConfig(),
    val oauth: McpOAuthConfig? = null,
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val workingDir: String? = null,
    val roots: List<McpRoot> = emptyList(),
    val samplingMode: McpSamplingMode = McpSamplingMode.MOCK,
    val samplingForwardUrl: String? = null,
    val samplingForwardToken: String? = null,
    val samplingMaxTokens: Int? = null,
    val autoRespondElicitation: Boolean = true,
)
