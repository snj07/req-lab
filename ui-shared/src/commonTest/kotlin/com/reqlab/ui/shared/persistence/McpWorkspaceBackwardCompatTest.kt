package com.reqlab.ui.shared.persistence

import com.reqlab.core.model.RequestKind
import com.reqlab.ui.shared.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpWorkspaceBackwardCompatTest {
    @Test
    fun pre_mcp_workspace_loads_as_http() {
        val json = """
            {"type":"reqLabWorkspace","version":"1.0",
             "collections":[{"type":"reqLabCollection","version":"1.0","name":"Old",
               "folders":[],"requests":[{"name":"Ping","method":"GET","url":"http://localhost/ping"}]}],
             "environments":[]}
        """.trimIndent()
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importWorkspaceFromString(state, json)
        val req = state.collections.first().children.first { !it.isFolder }
        assertEquals(RequestKind.HTTP, req.kind)
        assertEquals("Ping", req.name)
    }

    @Test
    fun mcp_request_imports_auth_and_headers_into_mcp_config() {
        val json = """
            {"type":"reqLabWorkspace","version":"1.0",
             "collections":[{"type":"reqLabCollection","version":"1.0","name":"Mcp",
               "folders":[],"requests":[{
                 "name":"Authed MCP",
                 "kind":"MCP",
                 "method":"POST",
                 "url":"http://localhost/mcp/authed",
                 "mcpTransport":"STREAMABLE_HTTP",
                 "auth":{"type":"BEARER","token":"reqlab-mcp-token"},
                 "headers":[{"key":"X-Api-Key","value":"reqlab-key"}]
               }]}],
             "environments":[]}
        """.trimIndent()
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importWorkspaceFromString(state, json)
        val req = state.collections.first().children.first { !it.isFolder }
        assertEquals(RequestKind.MCP, req.kind)
        assertEquals("BEARER", req.authType?.name)
        assertEquals("reqlab-mcp-token", req.authToken)
        val mcp = req.mcpConfig
        assertTrue(mcp != null)
        assertEquals("reqlab-mcp-token", mcp!!.auth.params["token"])
        assertEquals("reqlab-key", mcp.headers.single { it.key == "X-Api-Key" }.value)
    }
}
