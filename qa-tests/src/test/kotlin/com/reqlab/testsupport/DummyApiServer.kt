package com.reqlab.testsupport

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.content.forEachPart
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.websocket.CloseReason
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class DummyApiServer(
    private val port: Int = 0,
    private val validBearerToken: String = "dummy-token",
    private val validBasicToken: String = "dXNlcjpwYXNz",
    private val validApiKey: String = "test-api-key"
) {
    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val idSequence = AtomicInteger(3)
    private val users = ConcurrentHashMap<Int, UserRecord>()
    private val _requestsLog = MutableStateFlow<List<LoggedRequest>>(emptyList())

    val requestsLog = _requestsLog.asStateFlow()

    init {
        users[1] = UserRecord(1, "Alice", "alice@example.com")
        users[2] = UserRecord(2, "Bob", "bob@example.com")
        users[3] = UserRecord(3, "Charlie", "charlie@example.com")
    }

    fun start(): Int {
        if (engine != null) return resolvedPort()
        val started = embeddedServer(Netty, port = port) {
            dummyApiModule(
                users = users,
                idSequence = idSequence,
                requestLogger = { request -> _requestsLog.value = _requestsLog.value + request },
                validBearerToken = validBearerToken,
                validBasicToken = validBasicToken,
                validApiKey = validApiKey
            )
        }.start(wait = false)
        engine = started
        return resolvedPort()
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        engine = null
    }

    fun baseUrl(): String = "http://127.0.0.1:${resolvedPort()}"

    private fun resolvedPort(): Int {
        val appEngine = requireNotNull(engine) { "Server not started" }
        return runBlocking { appEngine.engine.resolvedConnectors().first().port }
    }
}

fun Application.dummyApiModule(
    users: ConcurrentHashMap<Int, UserRecord>,
    idSequence: AtomicInteger,
    requestLogger: (LoggedRequest) -> Unit,
    validBearerToken: String,
    validBasicToken: String,
    validApiKey: String
) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }
    install(WebSockets)

    routing {
        route("/users") {
            get {
                requestLogger(call.logRequest())
                if (call.simulateDelayOrError()) return@get
                if (call.request.queryParameters["large"] == "true") {
                    val largePayload = buildJsonArray {
                        repeat(5_000) { index ->
                            add(
                                buildJsonObject {
                                    put("id", index + 1)
                                    put("name", "User-$index")
                                    put("email", "user$index@example.com")
                                }
                            )
                        }
                    }
                    call.respond(largePayload)
                    return@get
                }

                call.respond(users.values.sortedBy { it.id })
            }

            post {
                requestLogger(call.logRequest())
                if (call.simulateDelayOrError()) return@post
                val (name, email) = if (call.request.contentType().match(ContentType.Application.FormUrlEncoded)) {
                    val params = call.receiveParameters()
                    Pair(
                        params["name"] ?: "Unnamed",
                        params["email"] ?: "unknown@example.com"
                    )
                } else {
                    val payloadText = call.receiveText()
                    val payload = Json.parseToJsonElement(payloadText).jsonObject
                    Pair(
                        payload["name"]?.jsonPrimitive?.content ?: "Unnamed",
                        payload["email"]?.jsonPrimitive?.content ?: "unknown@example.com"
                    )
                }
                val id = idSequence.incrementAndGet()
                val created = UserRecord(id, name, email)
                users[id] = created
                call.respond(HttpStatusCode.Created, created)
            }

            options {
                requestLogger(call.logRequest())
                call.response.headers.append("Allow", "GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD")
                call.respond(HttpStatusCode.OK)
            }

            head {
                requestLogger(call.logRequest())
                call.response.headers.append("X-ReqLab-Head", "ok")
                call.respond(HttpStatusCode.OK)
            }
        }

        route("/users/{id}") {
            put {
                requestLogger(call.logRequest())
                if (call.simulateDelayOrError()) return@put
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null || users[id] == null) {
                    call.respond(HttpStatusCode.NotFound, jsonError("User not found"))
                    return@put
                }
                val payloadText = call.receiveText()
                val payload = Json.parseToJsonElement(payloadText).jsonObject
                val replaced = UserRecord(
                    id = id,
                    name = payload["name"]?.jsonPrimitive?.content ?: "Unnamed",
                    email = payload["email"]?.jsonPrimitive?.content ?: "unknown@example.com"
                )
                users[id] = replaced
                call.respond(replaced)
            }

            patch {
                requestLogger(call.logRequest())
                if (call.simulateDelayOrError()) return@patch
                val id = call.parameters["id"]?.toIntOrNull()
                val current = id?.let { users[it] }
                if (id == null || current == null) {
                    call.respond(HttpStatusCode.NotFound, jsonError("User not found"))
                    return@patch
                }
                val payloadText = call.receiveText()
                val payload = Json.parseToJsonElement(payloadText).jsonObject
                val updated = current.copy(
                    name = payload["name"]?.jsonPrimitive?.content ?: current.name,
                    email = payload["email"]?.jsonPrimitive?.content ?: current.email
                )
                users[id] = updated
                call.respond(updated)
            }

            delete {
                requestLogger(call.logRequest())
                if (call.simulateDelayOrError()) return@delete
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null || users.remove(id) == null) {
                    call.respond(HttpStatusCode.NotFound, jsonError("User not found"))
                    return@delete
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }

        post("/graphql") {
            requestLogger(call.logRequest())
            val payloadText = call.receiveText()
            val query = runCatching {
                Json.parseToJsonElement(payloadText).jsonObject["query"]?.jsonPrimitive?.content.orEmpty()
            }.getOrDefault("")
            call.respond(
                buildJsonObject {
                    putJsonObject("data") {
                        put("echo", query)
                        putJsonArray("users") {
                            users.values.sortedBy { it.id }.forEach { user ->
                                add(
                                    buildJsonObject {
                                        put("id", user.id)
                                        put("name", user.name)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        post("/upload") {
            requestLogger(call.logRequest())
            var fileCount = 0
            call.receiveMultipart().forEachPart { part ->
                if (part is io.ktor.http.content.PartData.FileItem) {
                    fileCount++
                }
            }
            call.respond(
                buildJsonObject {
                    put("uploadedFiles", fileCount)
                    put("totalBytes", 0L)
                }
            )
        }

        post("/auth/login") {
            requestLogger(call.logRequest())
            val body = call.receiveText()
            val jsonBody = Json.parseToJsonElement(body).jsonObject
            val username = jsonBody["username"]?.jsonPrimitive?.content
            val password = jsonBody["password"]?.jsonPrimitive?.content

            if (username == "user" && password == "pass") {
                call.respond(
                    buildJsonObject {
                        put("accessToken", validBearerToken)
                        put("tokenType", "Bearer")
                    }
                )
            } else {
                call.respond(HttpStatusCode.Unauthorized, jsonError("Invalid credentials"))
            }
        }

        post("/oauth/token") {
            requestLogger(call.logRequest())
            call.respond(
                buildJsonObject {
                    put("access_token", validBearerToken)
                    put("token_type", "Bearer")
                    put("expires_in", 3600)
                }
            )
        }

        get("/auth/protected") {
            requestLogger(call.logRequest())
            val authHeader = call.request.headers["Authorization"]
            val apiKeyHeader = call.request.headers["X-API-Key"]

            val isBearerOk = authHeader == "Bearer $validBearerToken"
            val isBasicOk = authHeader == "Basic $validBasicToken"
            val isApiKeyOk = apiKeyHeader == validApiKey || call.request.queryParameters["apiKey"] == validApiKey

            if (!isBearerOk && !isBasicOk && !isApiKeyOk) {
                call.respond(HttpStatusCode.Unauthorized, jsonError("Unauthorized"))
                return@get
            }

            call.respond(buildJsonObject { put("authorized", true) })
        }

        get("/stream") {
            requestLogger(call.logRequest())
            call.respondTextWriter(contentType = ContentType.Text.Plain) {
                repeat(5) { idx ->
                    write("event-$idx\n")
                    flush()
                    delay(120)
                }
            }
        }

        webSocket("/ws") {
            requestLogger(call.logRequest())
            send(Frame.Text("connected"))
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (text == "close") {
                        send(Frame.Text("bye"))
                        return@webSocket
                    }
                    send(Frame.Text("echo:$text"))
                }
            }
        }

        // ── Body type echo endpoints ────────────────────────────

        post("/echo-xml") {
            requestLogger(call.logRequest())
            val body = call.receiveText()
            val ct = call.request.contentType().toString()
            call.respond(buildJsonObject {
                put("contentType", ct)
                put("bodyLength", body.length)
                put("body", body)
            })
        }

        post("/echo-html") {
            requestLogger(call.logRequest())
            val body = call.receiveText()
            val ct = call.request.contentType().toString()
            call.respond(buildJsonObject {
                put("contentType", ct)
                put("bodyLength", body.length)
                put("body", body)
            })
        }

        post("/echo-js") {
            requestLogger(call.logRequest())
            val body = call.receiveText()
            val ct = call.request.contentType().toString()
            call.respond(buildJsonObject {
                put("contentType", ct)
                put("bodyLength", body.length)
                put("body", body)
            })
        }

        post("/echo-form-data") {
            requestLogger(call.logRequest())
            val fields = mutableMapOf<String, String>()
            call.receiveMultipart().forEachPart { part ->
                when (part) {
                    is io.ktor.http.content.PartData.FormItem -> {
                        fields[part.name ?: "unknown"] = part.value
                    }
                    is io.ktor.http.content.PartData.FileItem -> {
                        fields[part.name ?: "file"] = "<file:${part.originalFileName}>"
                    }
                    else -> { /* ignore */ }
                }
            }
            call.respond(buildJsonObject {
                put("fieldCount", fields.size)
                putJsonObject("fields") {
                    fields.forEach { (k, v) -> put(k, v) }
                }
            })
        }

        post("/echo-urlencoded") {
            requestLogger(call.logRequest())
            val params = call.receiveParameters()
            call.respond(buildJsonObject {
                put("paramCount", params.entries().size)
                putJsonObject("params") {
                    params.entries().forEach { (k, values) -> put(k, values.firstOrNull() ?: "") }
                }
            })
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.simulateDelayOrError(): Boolean {
    when (request.queryParameters["mode"]) {
        "slow" -> delay(900)
        "error" -> {
            respond(HttpStatusCode.InternalServerError, jsonError("Simulated server error"))
            return true
        }
        else -> Unit
    }
    return false
}

private fun io.ktor.server.application.ApplicationCall.logRequest(): LoggedRequest = LoggedRequest(
    method = request.httpMethod.value,
    path = request.uri,
    headers = request.headers.entries().associate { it.key to it.value.joinToString(",") },
    epochMs = Clock.System.now().toEpochMilliseconds()
)

private fun jsonError(message: String): JsonObject = buildJsonObject {
    put("error", JsonPrimitive(message))
}

@Serializable
data class UserRecord(
    val id: Int,
    val name: String,
    val email: String
)

data class LoggedRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val epochMs: Long
)
