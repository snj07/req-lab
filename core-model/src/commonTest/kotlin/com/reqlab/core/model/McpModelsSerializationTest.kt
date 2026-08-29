package com.reqlab.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpModelsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun json_rpc_request_round_trip_string_id() {
        val envelope = JsonRpcEnvelope(
            id = jsonRpcId("abc-1"),
            method = "tools/call",
            params = buildJsonObject { put("name", "echo") },
        )
        val encoded = json.encodeToString(JsonRpcEnvelope.serializer(), envelope)
        val decoded = json.decodeFromString(JsonRpcEnvelope.serializer(), encoded)
        assertTrue(decoded.isRequest())
        assertFalse(decoded.isNotification())
        assertEquals("abc-1", decoded.idKey())
        assertEquals("tools/call", decoded.method)
    }

    @Test
    fun json_rpc_response_number_id_correlates_as_string() {
        val envelope = JsonRpcEnvelope(
            id = jsonRpcId(42),
            result = buildJsonObject { put("ok", true) },
        )
        val encoded = json.encodeToString(JsonRpcEnvelope.serializer(), envelope)
        val decoded = json.decodeFromString(JsonRpcEnvelope.serializer(), encoded)
        assertTrue(decoded.isResponse())
        assertEquals("42", decoded.idKey())
        assertEquals("42", jsonRpcIdKey(JsonPrimitive(42)))
        assertEquals("42", jsonRpcIdKey(JsonPrimitive("42")))
    }

    @Test
    fun notification_has_no_id() {
        val envelope = JsonRpcEnvelope(method = "notifications/initialized")
        assertTrue(envelope.isNotification())
        assertNull(envelope.idKey())
    }

    @Test
    fun error_codes_and_isError_are_distinct() {
        val rpcError = JsonRpcError(JsonRpcErrorCodes.METHOD_NOT_FOUND, "Method not found")
        val toolResult = McpToolResult(
            content = listOf(McpContent(type = "text", text = "boom")),
            isError = true,
        )
        val encoded = json.encodeToString(McpToolResult.serializer(), toolResult)
        val decoded = json.decodeFromString(McpToolResult.serializer(), encoded)
        assertTrue(decoded.isError)
        assertEquals(-32601, rpcError.code)
        assertEquals("boom", decoded.content.single().text)
    }

    @Test
    fun initialize_result_unknown_keys_are_ignored() {
        val raw = """
            {"protocolVersion":"2025-06-18",
             "capabilities":{"tools":{"listChanged":true},"resources":{"subscribe":true},"unknownCap":true},
             "serverInfo":{"name":"demo","version":"1","extra":"x"},
             "surprise":1}
        """.trimIndent()
        val decoded = json.decodeFromString(McpInitializeResult.serializer(), raw)
        assertEquals(MCP_PROTOCOL_VERSION, decoded.protocolVersion)
        assertEquals(true, decoded.capabilities.tools?.listChanged)
        assertEquals(true, decoded.capabilities.resources?.subscribe)
        assertEquals("demo", decoded.serverInfo.name)
    }

    @Test
    fun connection_config_defaults_round_trip() {
        val config = McpConnectionConfig(
            url = "http://localhost:8080/mcp",
            roots = listOf(McpRoot("file:///tmp", "tmp")),
        )
        val encoded = json.encodeToString(McpConnectionConfig.serializer(), config)
        val decoded = json.decodeFromString(McpConnectionConfig.serializer(), encoded)
        assertEquals(McpTransportType.STREAMABLE_HTTP, decoded.transport)
        assertEquals(McpHttpMode.AUTO, decoded.httpMode)
        assertEquals(McpSamplingMode.MOCK, decoded.samplingMode)
        assertEquals("file:///tmp", decoded.roots.single().uri)
    }

    @Test
    fun content_variants_round_trip() {
        val contents = listOf(
            McpContent(type = "text", text = "hello"),
            McpContent(type = "image", data = "AAA", mimeType = "image/png"),
            McpContent(type = "audio", data = "BBB", mimeType = "audio/wav"),
            McpContent(type = "resource_link", uri = "file:///a", name = "a"),
            McpContent(type = "resource", resource = JsonObject(mapOf("uri" to JsonPrimitive("file:///b")))),
        )
        contents.forEach { original ->
            val decoded = json.decodeFromString(McpContent.serializer(), json.encodeToString(McpContent.serializer(), original))
            assertEquals(original.type, decoded.type)
        }
    }
}
