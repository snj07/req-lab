package com.reqlab.core.network.mcp

import com.reqlab.core.model.AuthConfig
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.McpConnectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class McpConfigResolveTest {
    @Test
    fun interpolates_url_auth_and_headers() {
        val resolved = resolveMcpConfig(
            McpConnectionConfig(
                url = "{{base}}/mcp",
                auth = AuthConfig(AuthType.BEARER, mapOf("token" to "{{tok}}")),
                headers = listOf(KeyValueEntry("X-Api-Key", "{{key}}")),
            ),
            listOf(mapOf("base" to "https://example", "tok" to "secret-token", "key" to "k1")),
        )
        assertEquals("https://example/mcp", resolved.url)
        assertEquals("secret-token", resolved.auth.params["token"])
        assertEquals("k1", resolved.headers.single().value)
        assertEquals("X-Api-Key", resolved.headers.single().key)
    }

    @Test
    fun interpolates_query_params_on_mcp_url() {
        val resolved = resolveMcpConfig(
            McpConnectionConfig(url = "{{base}}/mcp?requireTenant=true&tenant={{tenant}}"),
            listOf(mapOf("base" to "https://example", "tenant" to "acme")),
        )
        assertEquals("https://example/mcp?requireTenant=true&tenant=acme", resolved.url)
    }
}
