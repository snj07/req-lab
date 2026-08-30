package com.reqlab.core.network.mcp

import com.reqlab.core.model.McpOAuthConfig
import com.reqlab.core.model.McpOAuthGrantType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OAuthTokenFlowTest {
    @Test
    fun discovery_dcr_and_token() = runTest {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/.well-known/oauth-protected-resource") -> respond(
                    """{"resource":"https://example/mcp","authorization_servers":["https://auth.example"]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path.endsWith("/.well-known/oauth-authorization-server") -> respond(
                    """{"issuer":"https://auth.example","authorization_endpoint":"https://auth.example/authorize","token_endpoint":"https://auth.example/token","registration_endpoint":"https://auth.example/register","code_challenge_methods_supported":["S256"]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path.endsWith("/register") -> respond(
                    """{"client_id":"cid-1"}""",
                    HttpStatusCode.Created,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path.endsWith("/token") -> respond(
                    """{"access_token":"atk","refresh_token":"rtk","token_type":"Bearer","expires_in":3600}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond("no $path", HttpStatusCode.NotFound)
            }
        }
        val oauth = McpOAuthClient(
            HttpClient(engine) { expectSuccess = false },
            randomBytes = { ByteArray(it) { 1 } },
            openAuthorize = { _, _ -> "splendid-code" },
        )
        val result = oauth.authorize(
            resourceUrl = "https://example/mcp",
            config = McpOAuthConfig(useDcr = true, scopes = listOf("mcp")),
            wwwAuthenticate = """Bearer realm="mcp", resource_metadata="https://example/.well-known/oauth-protected-resource"""",
        )
        assertEquals("atk", result.accessToken)
        assertEquals("rtk", result.refreshToken)
        assertEquals("cid-1", result.clientId)
        assertTrue(oauth.debugLog.any { it.phase.name == "DCR" })
        assertTrue(oauth.debugLog.any { it.phase.name == "TOKEN" })
    }

    @Test
    fun client_credentials_skips_browser() = runTest {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.contains("oauth-protected-resource") -> respond(
                    """{"authorization_servers":["https://auth.example"]}""",
                )
                path.contains("oauth-authorization-server") -> respond(
                    """{"token_endpoint":"https://auth.example/token","registration_endpoint":"https://auth.example/register"}""",
                )
                path.endsWith("/register") -> respond("""{"client_id":"cid"}""")
                path.endsWith("/token") -> respond("""{"access_token":"cc-token","token_type":"Bearer"}""")
                else -> respond("no", HttpStatusCode.NotFound)
            }
        }
        val oauth = McpOAuthClient(HttpClient(engine) { expectSuccess = false })
        val result = oauth.authorize(
            "https://example/mcp",
            McpOAuthConfig(grantType = McpOAuthGrantType.CLIENT_CREDENTIALS, useDcr = true),
        )
        assertEquals("cc-token", result.accessToken)
    }

    @Test
    fun parse_www_authenticate_resource_metadata() {
        val header =
            "Bearer realm=\"mcp\", resource_metadata=\"https://mcp.example/.well-known/oauth-protected-resource\", error=\"invalid_token\""
        assertEquals(
            "https://mcp.example/.well-known/oauth-protected-resource",
            McpOAuthClient.parseResourceMetadataUrl(header),
        )
        val uriForm = "Bearer resource_metadata_uri=\"https://mcp.example/.well-known/oauth-protected-resource\""
        assertEquals(
            "https://mcp.example/.well-known/oauth-protected-resource",
            McpOAuthClient.parseResourceMetadataUrl(uriForm),
        )
    }
}
