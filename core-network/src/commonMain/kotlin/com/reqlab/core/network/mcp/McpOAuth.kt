package com.reqlab.core.network.mcp

import com.reqlab.core.model.McpOAuthConfig
import com.reqlab.core.model.McpOAuthDebugEntry
import com.reqlab.core.model.McpOAuthGrantType
import com.reqlab.core.model.McpOAuthPhase
import com.reqlab.core.model.OAuthAuthorizationServerMetadata
import com.reqlab.core.model.OAuthDynamicClientRegistrationRequest
import com.reqlab.core.model.OAuthDynamicClientRegistrationResponse
import com.reqlab.core.model.OAuthProtectedResourceMetadata
import com.reqlab.core.model.OAuthTokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.datetime.Clock

class McpOAuthClient(
    private val httpClient: HttpClient,
    private val randomBytes: (Int) -> ByteArray = { mcpSecureRandomBytes(it) },
    private val openAuthorize: suspend (url: String, redirectPort: Int) -> String =
        { url, port -> mcpOpenAuthorizeUrlAndAwaitCode(url, port) },
) {
    val debugLog = mutableListOf<McpOAuthDebugEntry>()

    fun pkceVerifier(): String = randomBytes(32).mcpBase64(urlSafe = true, padding = false)

    fun pkceChallengeS256(verifier: String): String =
        sha256(verifier.encodeToByteArray()).mcpBase64(urlSafe = true, padding = false)

    suspend fun authorize(
        resourceUrl: String,
        config: McpOAuthConfig,
        wwwAuthenticate: String? = null,
        preissuedCode: String? = null,
        preissuedVerifier: String? = null,
    ): McpOAuthConfig {
        debugLog.clear()
        var working = config
        val now = { Clock.System.now().toEpochMilliseconds() }

        if (working.grantType == McpOAuthGrantType.PASTE_TOKEN && !working.accessToken.isNullOrBlank()) {
            return working
        }

        val metadataUrl = parseResourceMetadataUrl(wwwAuthenticate)
            ?: defaultProtectedResourceUrl(working.authServerUrl ?: resourceUrl)
        val resourceMeta = fetchProtectedResource(metadataUrl)
        val authServer = working.authServerUrl
            ?: resourceMeta.authorizationServers.firstOrNull()
            ?: originOf(resourceUrl)
        val asMeta = fetchAuthorizationServer(authServer)
        val redirectUri = working.redirectUri ?: "http://127.0.0.1:${working.redirectPort}/callback"

        if (working.useDcr && working.clientId.isNullOrBlank()) {
            val registrationEndpoint = asMeta.registrationEndpoint
                ?: throw McpProtocolException("Authorization server has no registration_endpoint")
            val dcr = registerClient(registrationEndpoint, redirectUri, working.scopes)
            working = working.copy(clientId = dcr.clientId, clientSecret = dcr.clientSecret ?: working.clientSecret)
            record(McpOAuthPhase.DCR, now(), "POST $registrationEndpoint", "client_id=${dcr.clientId}", 201)
        }

        val clientId = working.clientId ?: throw McpProtocolException("OAuth client_id is required")
        val tokenEndpoint = asMeta.tokenEndpoint
            ?: throw McpProtocolException("Authorization server has no token_endpoint")

        if (working.grantType == McpOAuthGrantType.CLIENT_CREDENTIALS) {
            val token = exchangeToken(
                tokenEndpoint,
                clientId,
                working.clientSecret,
                Parameters.build {
                    append("grant_type", "client_credentials")
                    if (working.scopes.isNotEmpty()) append("scope", working.scopes.joinToString(" "))
                    append("resource", working.resource ?: resourceUrl)
                },
                McpOAuthPhase.TOKEN,
            )
            return applyToken(working, token, now())
        }

        if (!working.refreshToken.isNullOrBlank() && working.grantType == McpOAuthGrantType.REFRESH_TOKEN) {
            return refresh(working, tokenEndpoint, resourceUrl)
        }

        val verifier = preissuedVerifier ?: pkceVerifier()
        val challenge = pkceChallengeS256(verifier)
        val code = preissuedCode ?: run {
            val authorizeEndpoint = asMeta.authorizationEndpoint
                ?: throw McpProtocolException("Authorization server has no authorization_endpoint")
            val url = buildAuthorizeUrl(
                authorizeEndpoint, clientId, redirectUri, working.scopes, challenge, working.resource ?: resourceUrl,
            )
            record(McpOAuthPhase.AUTHORIZE, now(), url)
            val captured = openAuthorize(url, working.redirectPort)
            record(McpOAuthPhase.AUTHORIZE, now(), url, "code captured")
            captured
        }

        val token = exchangeToken(
            tokenEndpoint,
            clientId,
            working.clientSecret,
            Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("code_verifier", verifier)
                append("resource", working.resource ?: resourceUrl)
            },
            McpOAuthPhase.TOKEN,
        )
        return applyToken(working, token, now())
    }

    suspend fun refresh(config: McpOAuthConfig, tokenEndpoint: String, resourceUrl: String): McpOAuthConfig {
        val refreshToken = config.refreshToken ?: throw McpProtocolException("No refresh_token")
        val clientId = config.clientId ?: throw McpProtocolException("OAuth client_id is required")
        val token = exchangeToken(
            tokenEndpoint,
            clientId,
            config.clientSecret,
            Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("resource", config.resource ?: resourceUrl)
            },
            McpOAuthPhase.REFRESH,
        )
        return applyToken(config, token, Clock.System.now().toEpochMilliseconds())
    }

    private suspend fun fetchProtectedResource(url: String): OAuthProtectedResourceMetadata {
        record(McpOAuthPhase.DISCOVERY, Clock.System.now().toEpochMilliseconds(), "GET $url")
        val text = httpClient.get(url).bodyAsText()
        val decoded = mcpJson.decodeFromString(OAuthProtectedResourceMetadata.serializer(), text)
        record(McpOAuthPhase.DISCOVERY, Clock.System.now().toEpochMilliseconds(), "GET $url", text.take(400), 200)
        return decoded
    }

    private suspend fun fetchAuthorizationServer(authServer: String): OAuthAuthorizationServerMetadata {
        val candidates = listOf(
            "${authServer.trimEnd('/')}/.well-known/oauth-authorization-server",
            "${authServer.trimEnd('/')}/.well-known/openid-configuration",
        )
        var lastError: Exception? = null
        for (url in candidates) {
            try {
                record(McpOAuthPhase.DISCOVERY, Clock.System.now().toEpochMilliseconds(), "GET $url")
                val text = httpClient.get(url).bodyAsText()
                val decoded = mcpJson.decodeFromString(OAuthAuthorizationServerMetadata.serializer(), text)
                record(McpOAuthPhase.DISCOVERY, Clock.System.now().toEpochMilliseconds(), "GET $url", text.take(400), 200)
                return decoded
            } catch (e: Exception) {
                lastError = e
                record(McpOAuthPhase.DISCOVERY, Clock.System.now().toEpochMilliseconds(), "GET $url", error = e.message)
            }
        }
        throw lastError ?: McpProtocolException("Unable to fetch authorization-server metadata")
    }

    private suspend fun registerClient(
        endpoint: String,
        redirectUri: String,
        scopes: List<String>,
    ): OAuthDynamicClientRegistrationResponse {
        val request = OAuthDynamicClientRegistrationRequest(
            redirectUris = listOf(redirectUri),
            scope = scopes.joinToString(" ").ifBlank { null },
        )
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(mcpJson.encodeToString(OAuthDynamicClientRegistrationRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        return mcpJson.decodeFromString(OAuthDynamicClientRegistrationResponse.serializer(), text)
    }

    private suspend fun exchangeToken(
        tokenEndpoint: String,
        clientId: String,
        clientSecret: String?,
        params: Parameters,
        phase: McpOAuthPhase,
    ): OAuthTokenResponse {
        record(phase, Clock.System.now().toEpochMilliseconds(), "POST $tokenEndpoint")
        val response = httpClient.submitForm(tokenEndpoint, formParameters = Parameters.build {
            appendAll(params)
            append("client_id", clientId)
            if (!clientSecret.isNullOrBlank()) append("client_secret", clientSecret)
        }) {
            header(HttpHeaders.Accept, "application/json")
        }
        val text = response.bodyAsText()
        if (response.status.value >= 400) {
            record(phase, Clock.System.now().toEpochMilliseconds(), "POST $tokenEndpoint", text.take(400), response.status.value, text)
            throw McpProtocolException("Token endpoint failed: ${response.status} $text")
        }
        record(phase, Clock.System.now().toEpochMilliseconds(), "POST $tokenEndpoint", "token issued", response.status.value)
        return mcpJson.decodeFromString(OAuthTokenResponse.serializer(), text)
    }

    private fun applyToken(config: McpOAuthConfig, token: OAuthTokenResponse, now: Long): McpOAuthConfig {
        val expiresAt = token.expiresIn?.let { now + it * 1000 }
        return config.copy(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken ?: config.refreshToken,
            tokenType = token.tokenType,
            expiresAtEpochMillis = expiresAt,
        )
    }

    private fun record(
        phase: McpOAuthPhase,
        at: Long,
        request: String,
        response: String? = null,
        status: Int? = null,
        error: String? = null,
    ) {
        debugLog += McpOAuthDebugEntry(phase, at, request, response, status, error)
    }

    companion object {
        fun parseResourceMetadataUrl(wwwAuthenticate: String?): String? {
            if (wwwAuthenticate.isNullOrBlank()) return null
            val marker = "resource_metadata"
            val idx = wwwAuthenticate.indexOf(marker, ignoreCase = true)
            if (idx < 0) return null
            var i = idx + marker.length
            if (wwwAuthenticate.startsWith("_uri", i, ignoreCase = true)) i += 4
            while (i < wwwAuthenticate.length && wwwAuthenticate[i].isWhitespace()) i++
            if (i >= wwwAuthenticate.length || wwwAuthenticate[i] != '=') return null
            i++
            while (i < wwwAuthenticate.length && wwwAuthenticate[i].isWhitespace()) i++
            if (i >= wwwAuthenticate.length) return null
            return if (wwwAuthenticate[i] == '"') {
                val end = wwwAuthenticate.indexOf('"', i + 1)
                if (end < 0) null else wwwAuthenticate.substring(i + 1, end)
            } else {
                val end = wwwAuthenticate.indexOfAny(charArrayOf(',', ' '), i).let {
                    if (it < 0) wwwAuthenticate.length else it
                }
                wwwAuthenticate.substring(i, end).trim().ifBlank { null }
            }
        }

        fun defaultProtectedResourceUrl(base: String): String {
            val origin = originOf(base)
            return "$origin/.well-known/oauth-protected-resource"
        }

        fun originOf(url: String): String {
            val slash = url.indexOf('/', url.indexOf("://").let { if (it < 0) 0 else it + 3 })
            return if (slash < 0) url.trimEnd('/') else url.substring(0, slash)
        }

        fun buildAuthorizeUrl(
            endpoint: String,
            clientId: String,
            redirectUri: String,
            scopes: List<String>,
            challenge: String,
            resource: String,
        ): String {
            val params = buildString {
                append("response_type=code")
                append("&client_id=").append(encodeQuery(clientId))
                append("&redirect_uri=").append(encodeQuery(redirectUri))
                append("&code_challenge=").append(encodeQuery(challenge))
                append("&code_challenge_method=S256")
                if (scopes.isNotEmpty()) append("&scope=").append(encodeQuery(scopes.joinToString(" ")))
                append("&resource=").append(encodeQuery(resource))
            }
            return if (endpoint.contains('?')) "$endpoint&$params" else "$endpoint?$params"
        }

        private fun encodeQuery(value: String): String = buildString {
            value.forEach { c ->
                when {
                    c.isLetterOrDigit() || c in "-._~" -> append(c)
                    c == ' ' -> append("%20")
                    else -> append('%').append(c.code.toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}
