package com.reqlab.core.network.mcp

import com.reqlab.core.model.AuthConfig
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.JsonRpcEnvelope
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.McpOAuthConfig
import com.reqlab.core.network.VariableResolver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal val mcpJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}

internal fun resolveMcpConfig(
    config: McpConnectionConfig,
    variableLayers: List<Map<String, String>>,
): McpConnectionConfig {
    fun r(value: String) = VariableResolver.resolve(value, variableLayers)
    val oauth = config.oauth
    return config.copy(
        url = r(config.url),
        headers = config.headers.map { it.copy(key = r(it.key), value = r(it.value)) },
        auth = config.auth.copy(params = config.auth.params.mapValues { r(it.value) }),
        oauth = oauth?.copy(
            authServerUrl = oauth.authServerUrl?.let(::r),
            clientId = oauth.clientId?.let(::r),
            clientSecret = oauth.clientSecret?.let(::r),
            accessToken = oauth.accessToken?.let(::r),
            refreshToken = oauth.refreshToken?.let(::r),
            resource = oauth.resource?.let(::r),
        ),
        command = r(config.command),
        args = config.args.map(::r),
        env = config.env.mapValues { r(it.value) },
        workingDir = config.workingDir?.let(::r),
        samplingForwardUrl = config.samplingForwardUrl?.let(::r),
        samplingForwardToken = config.samplingForwardToken?.let(::r),
    )
}

internal fun HttpRequestBuilder.applyMcpHeaders(
    headers: List<KeyValueEntry>,
    auth: AuthConfig,
    oauth: McpOAuthConfig?,
    sessionId: String?,
    protocolVersion: String?,
    lastEventId: String? = null,
) {
    header(HttpHeaders.Accept, "application/json, text/event-stream")
    header("Accept-Language", "en")
    headers.filter { it.enabled && it.key.isNotBlank() }.forEach { header(it.key, it.value) }
    applyAuth(auth, oauth)
    if (!sessionId.isNullOrBlank()) header("Mcp-Session-Id", sessionId)
    if (!protocolVersion.isNullOrBlank()) header("MCP-Protocol-Version", protocolVersion)
    if (!lastEventId.isNullOrBlank()) header("Last-Event-ID", lastEventId)
}

internal fun HttpRequestBuilder.applyAuth(auth: AuthConfig, oauth: McpOAuthConfig?) {
    val oauthToken = oauth?.accessToken
    if (!oauthToken.isNullOrBlank()) {
        header(HttpHeaders.Authorization, "Bearer $oauthToken")
        return
    }
    when (auth.type) {
        AuthType.NONE -> Unit
        AuthType.BASIC -> {
            val username = auth.params["username"].orEmpty()
            val password = auth.params["password"].orEmpty()
            header(HttpHeaders.Authorization, "Basic ${"$username:$password".encodeToByteArray().mcpBase64()}")
        }
        AuthType.BEARER, AuthType.JWT, AuthType.OAUTH2 -> {
            val token = auth.params["token"] ?: auth.params["accessToken"].orEmpty()
            if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
        }
        AuthType.API_KEY -> {
            val key = auth.params["key"].orEmpty()
            val value = auth.params["value"].orEmpty()
            if (key.isNotBlank()) header(key, value)
        }
    }
}

internal fun ByteArray.mcpBase64(urlSafe: Boolean = false, padding: Boolean = true): String {
    val alphabet = if (urlSafe) {
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    } else {
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    }
    val result = StringBuilder((size + 2) / 3 * 4)
    var i = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xFF
        val b1 = if (i + 1 < size) this[i + 1].toInt() and 0xFF else -1
        val b2 = if (i + 2 < size) this[i + 2].toInt() and 0xFF else -1
        result.append(alphabet[b0 ushr 2])
        result.append(alphabet[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])
        if (b1 >= 0) {
            result.append(alphabet[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)])
        } else if (padding) result.append('=') else { /* skip */ }
        if (b2 >= 0) {
            result.append(alphabet[b2 and 0x3F])
        } else if (padding) result.append('=') else { /* skip */ }
        i += 3
    }
    return result.toString()
}

internal fun parseJsonRpc(text: String): JsonRpcEnvelope? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed == "[DONE]") return null
    return runCatching { mcpJson.decodeFromString(JsonRpcEnvelope.serializer(), trimmed) }.getOrNull()
}

internal inline fun <reified T> decodeResult(element: JsonElement?): T {
    requireNotNull(element) { "Missing JSON-RPC result" }
    return mcpJson.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), element)
}

internal inline fun <reified T> encodeParams(value: T): JsonElement =
    mcpJson.encodeToJsonElement(kotlinx.serialization.serializer<T>(), value)
