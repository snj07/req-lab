package com.reqlab.core.network.mcp

import com.reqlab.core.model.JsonRpcEnvelope
import com.reqlab.core.model.jsonRpcId
import com.reqlab.core.model.jsonRpcIdKey
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonRpcFramingTest {
    @Test
    fun classifies_request_response_and_notification() {
        val request = JsonRpcEnvelope(id = jsonRpcId(1), method = "tools/list")
        val notification = JsonRpcEnvelope(method = "notifications/initialized")
        val response = JsonRpcEnvelope(id = jsonRpcId(1), result = buildJsonObject { put("ok", true) })
        assertTrue(request.isRequest())
        assertTrue(notification.isNotification())
        assertTrue(response.isResponse())
        assertFalse(notification.isRequest())
        assertNull(notification.idKey())
        assertEquals("1", jsonRpcIdKey(JsonPrimitive(1)))
        assertEquals("1", jsonRpcIdKey(JsonPrimitive("1")))
    }

    @Test
    fun compact_json_has_no_newlines() {
        val encoded = mcpJson.encodeToString(
            JsonRpcEnvelope.serializer(),
            JsonRpcEnvelope(id = jsonRpcId(1), method = "ping"),
        )
        assertFalse('\n' in encoded)
        assertTrue(encoded.contains("\"method\":\"ping\""))
    }
}
