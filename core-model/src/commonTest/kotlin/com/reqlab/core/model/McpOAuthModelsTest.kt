package com.reqlab.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpOAuthModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun config_round_trip_and_secret_fields() {
        val config = McpOAuthConfig(
            authServerUrl = "http://localhost:8080",
            clientId = "abc",
            scopes = listOf("mcp"),
            useDcr = true,
            accessToken = "tok",
            refreshToken = "ref",
        )
        val decoded = json.decodeFromString(McpOAuthConfig.serializer(), json.encodeToString(McpOAuthConfig.serializer(), config))
        assertEquals("abc", decoded.clientId)
        assertEquals("tok", decoded.accessToken)
        assertEquals(McpOAuthGrantType.AUTHORIZATION_CODE, decoded.grantType)
    }

    @Test
    fun metadata_snake_case_round_trip() {
        val raw = """
            {"issuer":"http://localhost:8080",
             "authorization_endpoint":"http://localhost:8080/oauth/authorize",
             "token_endpoint":"http://localhost:8080/oauth/token",
             "registration_endpoint":"http://localhost:8080/oauth/register",
             "code_challenge_methods_supported":["S256"],
             "extra":true}
        """.trimIndent()
        val decoded = json.decodeFromString(OAuthAuthorizationServerMetadata.serializer(), raw)
        assertEquals("http://localhost:8080/oauth/token", decoded.tokenEndpoint)
        assertEquals(listOf("S256"), decoded.codeChallengeMethodsSupported)
    }

    @Test
    fun token_response_and_error() {
        val token = json.decodeFromString(
            OAuthTokenResponse.serializer(),
            """{"access_token":"a","token_type":"Bearer","expires_in":3600,"refresh_token":"r"}""",
        )
        assertEquals("a", token.accessToken)
        assertEquals(3600, token.expiresIn)
        val error = json.decodeFromString(OAuthError.serializer(), """{"error":"invalid_grant","error_description":"bad"}""")
        assertEquals("invalid_grant", error.error)
        assertEquals("bad", error.errorDescription)
    }

    @Test
    fun dcr_defaults() {
        val req = OAuthDynamicClientRegistrationRequest(redirectUris = listOf("http://127.0.0.1:8099/callback"))
        val encoded = json.encodeToString(OAuthDynamicClientRegistrationRequest.serializer(), req)
        assertTrue(encoded.contains("client_name"))
        assertTrue(encoded.contains("authorization_code"))
        val resp = json.decodeFromString(
            OAuthDynamicClientRegistrationResponse.serializer(),
            """{"client_id":"cid","client_secret":null}""",
        )
        assertEquals("cid", resp.clientId)
        assertNull(resp.clientSecret)
    }
}
