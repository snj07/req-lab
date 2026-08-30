package com.reqlab.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val eventIds = AtomicInteger(1)
private val legacyStreams = ConcurrentHashMap<String, Channel<String>>()

fun Route.mcpAndOAuthRoutes() {
    options("/mcp") { call.applyMcpCors(); call.respond(HttpStatusCode.NoContent) }
    options("/mcp/authed") { call.applyMcpCors(); call.respond(HttpStatusCode.NoContent) }
    options("/mcp/auth/bearer") { call.applyMcpCors(); call.respond(HttpStatusCode.NoContent) }
    options("/mcp/auth/basic") { call.applyMcpCors(); call.respond(HttpStatusCode.NoContent) }
    options("/mcp/auth/apikey") { call.applyMcpCors(); call.respond(HttpStatusCode.NoContent) }
    options("/mcp/auth/jwt") { call.applyMcpCors(); call.respond(HttpStatusCode.NoContent) }
    options("/mcp/secure") { call.applyMcpCors(); call.respond(HttpStatusCode.NoContent) }
    options("/mcp/sse") { call.applyMcpCors(); call.respond(HttpStatusCode.NoContent) }
    options("/mcp/messages") { call.applyMcpCors(); call.respond(HttpStatusCode.NoContent) }

    post("/mcp") { call.handleMcpPost(McpAuthGate.NONE) }
    post("/mcp/authed") { call.handleMcpPost(McpAuthGate.BEARER_AND_API_KEY) }
    post("/mcp/auth/bearer") { call.handleMcpPost(McpAuthGate.BEARER) }
    post("/mcp/auth/basic") { call.handleMcpPost(McpAuthGate.BASIC) }
    post("/mcp/auth/apikey") { call.handleMcpPost(McpAuthGate.API_KEY) }
    post("/mcp/auth/jwt") { call.handleMcpPost(McpAuthGate.JWT) }
    get("/mcp") { call.handleMcpGet() }
    delete("/mcp") {
        call.applyMcpCors()
        val sid = call.request.header("Mcp-Session-Id")
        if (sid != null) {
            McpMockProtocol.sessions.remove(sid)?.serverPushes?.close()
        }
        call.respond(HttpStatusCode.NoContent)
    }

    post("/mcp/secure") { call.handleMcpPost(McpAuthGate.OAUTH) }

    get("/mcp/sse") { call.handleLegacySse() }
    post("/mcp/messages") { call.handleLegacyMessage() }

    get("/.well-known/oauth-protected-resource") {
        call.applyMcpCors()
        val origin = call.originBase()
        call.respondText(
            buildJsonObject {
                put("resource", "$origin/mcp/secure")
                put("authorization_servers", buildJsonArray { add(JsonPrimitive(origin)) })
            }.toString(),
            ContentType.Application.Json,
        )
    }
    get("/.well-known/oauth-authorization-server") {
        call.applyMcpCors()
        val origin = call.originBase()
        call.respondText(
            buildJsonObject {
                put("issuer", origin)
                put("authorization_endpoint", "$origin/oauth/authorize")
                put("token_endpoint", "$origin/oauth/token")
                put("registration_endpoint", "$origin/oauth/register")
                put("code_challenge_methods_supported", buildJsonArray { add(JsonPrimitive("S256")) })
                put("grant_types_supported", buildJsonArray {
                    add(JsonPrimitive("authorization_code"))
                    add(JsonPrimitive("refresh_token"))
                    add(JsonPrimitive("client_credentials"))
                })
            }.toString(),
            ContentType.Application.Json,
        )
    }
    post("/oauth/register") {
        call.applyMcpCors()
        val body = call.receiveText()
        val obj = runCatching { mcpMockJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val redirect = (obj?.get("redirect_uris") as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?: listOf("http://127.0.0.1:8099/callback")
        val clientId = "dcr-" + UUID.randomUUID().toString().take(8)
        McpMockProtocol.oauthClients[clientId] = OAuthClientRecord(clientId, redirect)
        call.respondText(
            buildJsonObject { put("client_id", clientId) }.toString(),
            ContentType.Application.Json,
            HttpStatusCode.Created,
        )
    }
    get("/oauth/authorize") {
        call.applyMcpCors()
        val clientId = call.request.queryParameters["client_id"].orEmpty()
        val redirect = call.request.queryParameters["redirect_uri"].orEmpty()
        val challenge = call.request.queryParameters["code_challenge"].orEmpty()
        val state = call.request.queryParameters["state"]
        val code = "code-" + UUID.randomUUID().toString().take(8)
        McpMockProtocol.oauthCodes[code] = OAuthCodeRecord(code, clientId, challenge, redirect)
        val sep = if (redirect.contains('?')) "&" else "?"
        val location = buildString {
            append(redirect).append(sep).append("code=").append(code)
            if (!state.isNullOrBlank()) append("&state=").append(state)
        }
        call.respondRedirect(location)
    }
    post("/oauth/token") {
        call.applyMcpCors()
        val params = call.receiveText()
        val form = params.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], Charsets.UTF_8) else null
        }.toMap()
        val grant = form["grant_type"]
        when (grant) {
            "authorization_code" -> {
                val code = form["code"].orEmpty()
                val verifier = form["code_verifier"].orEmpty()
                val record = McpMockProtocol.oauthCodes.remove(code)
                if (record == null || sha256Base64Url(verifier) != record.codeChallenge) {
                    call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_grant") })
                    return@post
                }
                McpMockProtocol.oauthTokens[McpMockProtocol.ISSUED_ACCESS_TOKEN] = record.clientId
                call.respondText(tokenJson(), ContentType.Application.Json)
            }
            "refresh_token" -> {
                if (form["refresh_token"] != McpMockProtocol.ISSUED_REFRESH_TOKEN) {
                    call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "invalid_grant") })
                    return@post
                }
                call.respondText(tokenJson(), ContentType.Application.Json)
            }
            "client_credentials" -> {
                McpMockProtocol.oauthTokens[McpMockProtocol.ISSUED_ACCESS_TOKEN] = form["client_id"].orEmpty()
                call.respondText(tokenJson(), ContentType.Application.Json)
            }
            else -> call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "unsupported_grant_type") })
        }
    }
}

internal const val MCP_TEST_BEARER_TOKEN = "reqlab-mcp-token"
internal const val MCP_TEST_API_KEY = "reqlab-key"
internal const val MCP_TEST_JWT = "reqlab-mcp-jwt"
internal const val MCP_TEST_BASIC_USER = "admin"
internal const val MCP_TEST_BASIC_PASSWORD = "password"

private enum class McpAuthGate { NONE, BEARER, BASIC, API_KEY, JWT, BEARER_AND_API_KEY, OAUTH }

private suspend fun ApplicationCall.handleMcpPost(gate: McpAuthGate) {
    applyMcpCors()
    if (!enforceMcpAuth(gate)) return
    if (request.queryParameters["requireTenant"] == "true" && request.queryParameters["tenant"].isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", "missing_tenant") })
        return
    }
    val stateless = request.queryParameters["stateless"] == "true"
    val sessionHeader = request.header("Mcp-Session-Id")
    if (!sessionHeader.isNullOrBlank() && McpMockProtocol.session(sessionHeader) == null) {
        respond(HttpStatusCode.NotFound, "Unknown MCP session")
        return
    }
    val body = receiveText()
    val extra = mutableListOf<McpOutbound>()
    val session = if (stateless) McpMockSession() else McpMockProtocol.requireOrCreate(sessionHeader)
    if (!stateless) McpMockProtocol.sessions[session.id] = session
    val handled = McpMockProtocol.handle(body, session, extra)
    extra.forEach { outbound -> session.serverPushes.trySend(outbound) }
    if (handled != null && handled["result"] != null) {
        val method = runCatching {
            mcpMockJson.parseToJsonElement(body) as JsonObject
        }.getOrNull()?.get("method")?.let { (it as? JsonPrimitive)?.content }
        if (method == "initialize" && !stateless) {
            response.header("Mcp-Session-Id", session.id)
        }
    }
    val accept = request.header(HttpHeaders.Accept).orEmpty()
    val wantsSse = accept.contains("text/event-stream")
    if (wantsSse && extra.isNotEmpty()) {
        response.header(HttpHeaders.CacheControl, "no-cache")
        respond(object : OutgoingContent.WriteChannelContent() {
            override val contentType: ContentType = ContentType.parse("text/event-stream")
            override suspend fun writeTo(channel: ByteWriteChannel) {
                extra.forEach { outbound ->
                    channel.writeStringUtf8(McpMockProtocol.sseFrame(outbound, eventIds.getAndIncrement()))
                    channel.flush()
                }
                val result = McpMockProtocol.resolveWaitResult(session, handled)
                if (result != null) {
                    channel.writeStringUtf8(McpMockProtocol.sseFrame(McpOutbound(result), eventIds.getAndIncrement()))
                    channel.flush()
                }
            }
        })
        return
    }
    val result = McpMockProtocol.resolveWaitResult(session, handled)
    if (result == null) {
        respond(HttpStatusCode.Accepted)
        return
    }
    respondText(result.toString(), ContentType.Application.Json)
}

private suspend fun ApplicationCall.handleMcpGet() {
    applyMcpCors()
    val sessionHeader = request.header("Mcp-Session-Id")
    val session = McpMockProtocol.session(sessionHeader)
    if (session == null) {
        respond(HttpStatusCode.MethodNotAllowed, "GET SSE optional; session required")
        return
    }
    response.header(HttpHeaders.CacheControl, "no-cache")
    respondTextWriter(contentType = ContentType.parse("text/event-stream")) {
        for (outbound in session.serverPushes) {
            write(McpMockProtocol.sseFrame(outbound, eventIds.getAndIncrement()))
            flush()
        }
    }
}

private suspend fun ApplicationCall.handleLegacySse() {
    applyMcpCors()
    val session = McpMockProtocol.requireOrCreate(null)
    val channel = Channel<String>(Channel.UNLIMITED)
    legacyStreams[session.id] = channel
    respondTextWriter(contentType = ContentType.parse("text/event-stream")) {
        write(McpMockProtocol.sseEndpointFrame("/mcp/messages?sessionId=${session.id}"))
        flush()
        for (frame in channel) {
            write(frame)
            flush()
        }
    }
}

private suspend fun ApplicationCall.handleLegacyMessage() {
    applyMcpCors()
    val sid = request.queryParameters["sessionId"]
    val session = McpMockProtocol.session(sid) ?: McpMockProtocol.requireOrCreate(sid)
    val channel = legacyStreams[session.id]
    val extra = mutableListOf<McpOutbound>()
    val result = McpMockProtocol.handle(receiveText(), session, extra)
    extra.forEach { channel?.send(McpMockProtocol.sseFrame(it, eventIds.getAndIncrement())) }
    val resolved = McpMockProtocol.resolveWaitResult(session, result)
    if (resolved != null) channel?.send(McpMockProtocol.sseFrame(McpOutbound(resolved), eventIds.getAndIncrement()))
    respond(HttpStatusCode.Accepted)
}

private fun ApplicationCall.applyMcpCors() {
    response.header("Access-Control-Allow-Origin", "*")
    response.header("Access-Control-Allow-Headers", "*")
    response.header("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS")
    response.header("Access-Control-Expose-Headers", "Mcp-Session-Id, WWW-Authenticate")
}

private fun ApplicationCall.originBase(): String {
    val host = request.header("Host") ?: "localhost:8080"
    return "http://$host"
}

private suspend fun ApplicationCall.enforceMcpAuth(gate: McpAuthGate): Boolean {
    val authorization = request.header(HttpHeaders.Authorization).orEmpty()
    val ok = when (gate) {
        McpAuthGate.NONE -> true
        McpAuthGate.OAUTH -> hasMcpOAuth()
        McpAuthGate.BEARER -> authorization == "Bearer $MCP_TEST_BEARER_TOKEN"
        McpAuthGate.JWT -> authorization == "Bearer $MCP_TEST_JWT"
        McpAuthGate.API_KEY -> request.header("X-Api-Key") == MCP_TEST_API_KEY
        McpAuthGate.BASIC -> {
            val expected = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("$MCP_TEST_BASIC_USER:$MCP_TEST_BASIC_PASSWORD".toByteArray())
            authorization == expected
        }
        McpAuthGate.BEARER_AND_API_KEY ->
            authorization == "Bearer $MCP_TEST_BEARER_TOKEN" && request.header("X-Api-Key") == MCP_TEST_API_KEY
    }
    if (!ok) {
        if (gate == McpAuthGate.OAUTH) respondUnauthorized()
        else respond(HttpStatusCode.Unauthorized, buildJsonObject { put("error", "invalid_token") })
    }
    return ok
}

private fun ApplicationCall.hasMcpOAuth(): Boolean {
    val header = request.header(HttpHeaders.Authorization).orEmpty()
    val token = if (header.startsWith("Bearer ")) header.removePrefix("Bearer ").trim() else ""
    return token == McpMockProtocol.ISSUED_ACCESS_TOKEN || McpMockProtocol.oauthTokens.containsKey(token)
}

private suspend fun ApplicationCall.respondUnauthorized() {
    response.header(
        HttpHeaders.WWWAuthenticate,
        """Bearer realm="mcp", resource_metadata="${originBase()}/.well-known/oauth-protected-resource"""",
    )
    respond(HttpStatusCode.Unauthorized, buildJsonObject { put("error", "invalid_token") })
}

private fun tokenJson(): String = buildJsonObject {
    put("access_token", McpMockProtocol.ISSUED_ACCESS_TOKEN)
    put("token_type", "Bearer")
    put("expires_in", 3600)
    put("refresh_token", McpMockProtocol.ISSUED_REFRESH_TOKEN)
}.toString()

fun runMcpStdio() {
    val session = McpMockProtocol.requireOrCreate(null)
    val reader = System.`in`.bufferedReader()
    while (true) {
        val line = reader.readLine() ?: break
        if (line.isBlank()) continue
        val extra = mutableListOf<McpOutbound>()
        val handled = McpMockProtocol.handle(line, session, extra)
        extra.forEach { System.out.println(it.envelope.toString()) }
        System.out.flush()
        val waitFor = McpMockProtocol.waitForCallbackId(handled)
        val result = if (waitFor != null) {
            val replyLine = reader.readLine()
            if (!replyLine.isNullOrBlank()) {
                McpMockProtocol.handle(replyLine, session, mutableListOf())
            }
            val echoed = session.lastReplies[waitFor]
            val callId = handled?.get("id")
            if (callId == null) {
                null
            } else if (echoed == null) {
                McpMockProtocol.rpcResult(
                    callId,
                    McpMockProtocol.toolText("Timed out waiting for client reply to $waitFor", isError = true),
                )
            } else {
                McpMockProtocol.rpcResult(callId, McpMockProtocol.toolText(echoed.toString()))
            }
        } else {
            handled
        }
        if (result != null) System.out.println(result.toString())
        System.out.flush()
    }
}
