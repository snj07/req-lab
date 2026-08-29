package com.reqlab.server

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.method
import io.ktor.server.routing.options
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.http.HttpMethod
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.Base64

fun main(args: Array<String> = emptyArray()) {
    if (args.contains("--stdio")) {
        System.err.println("ReqLab MCP stdio mock ready")
        runMcpStdio()
        return
    }
    println("==========================================================")
    println("  ReqLab Sample API Server")
    println("  Listening on  http://localhost:8080")
    println("  WebSocket     ws://localhost:8080/ws")
    println("  LLM mock      POST /v1/chat/completions  (?demo=true for a visible token stream)")
    println("  MCP           POST /mcp  (OAuth-protected: POST /mcp/secure)")
    println("  MCP legacy    GET  /mcp/sse")
    println("  Press Ctrl+C to stop")
    println("==========================================================")
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { prettyPrint = true; ignoreUnknownKeys = true })
    }
    install(WebSockets)

    routing {

        // ── Health check ───────────────────────────────────────────────────
        get("/") {
            call.respondText(
                "ReqLab Sample API Server is running! Visit http://localhost:8080",
                ContentType.Text.Plain
            )
        }

        // ── Deterministic scripting test endpoints ───────────────────────
        get("/status/200") {
            call.respond(HttpStatusCode.OK, buildJsonObject {
                put("status", 200)
                put("ok", true)
                put("message", "Deterministic 200 response")
            })
        }

        get("/status/201") {
            call.respond(HttpStatusCode.Created, buildJsonObject {
                put("status", 201)
                put("ok", true)
                put("message", "Deterministic 201 response")
            })
        }

        get("/json/user") {
            call.respond(buildJsonObject {
                put("id", "1")
                put("name", "Jane")
                put("age", 23)
                put("type", "Subscriber")
                put("active", true)
            })
        }

        get("/json/array") {
            call.respond(buildJsonObject {
                put("items", buildJsonArray {
                    add(buildJsonObject {
                        put("id", 1)
                        put("label", "alpha")
                        put("tags", buildJsonArray { add("core"); add("smoke") })
                    })
                    add(buildJsonObject {
                        put("id", 2)
                        put("label", "beta")
                        put("tags", buildJsonArray { add("extended") })
                    })
                })
            })
        }

        get("/json/object") {
            call.respond(buildJsonObject {
                put("meta", buildJsonObject {
                    put("version", "1.0")
                    put("env", "test")
                })
                put("payload", buildJsonObject {
                    put("title", "ReqLab")
                    put("count", 3)
                    put("enabled", true)
                    put("nullable", JsonNull)
                    put("nested", buildJsonObject {
                        put("code", "OBJ-1")
                    })
                    put("list", buildJsonArray { add(10); add(20); add(30) })
                })
            })
        }

        get("/headers") {
            val trace = call.request.header("X-Trace-Id").orEmpty()
            val run = call.request.header("X-Run-Id").orEmpty()
            call.response.header("X-Sample-Server", "ReqLab-Sample")
            call.respond(buildJsonObject {
                put("traceId", trace)
                put("runId", run)
                put("hasTrace", trace.isNotBlank())
                put("hasRunId", run.isNotBlank())
            })
        }

        get("/cookies") {
            call.response.cookies.append(Cookie("script_session", "sess-123", path = "/"))
            call.response.cookies.append(Cookie("script_user", "qa", path = "/"))
            call.respond(buildJsonObject {
                put("message", "Cookies set")
                put("receivedCookieHeader", call.request.header("Cookie") ?: "")
            })
        }

        get("/response-time") {
            val delayMs = call.request.queryParameters["ms"]?.toLongOrNull() ?: 120L
            delay(delayMs)
            call.respond(buildJsonObject {
                put("delayMs", delayMs)
                put("ok", true)
            })
        }

        get("/string-body") {
            call.respondText(
                "ReqLab plain string body for scripting assertions",
                ContentType.Text.Plain,
                HttpStatusCode.OK,
            )
        }

        post("/echo-body") {
            val body = call.receiveText()
            call.respond(buildJsonObject {
                put("method", call.request.httpMethod.value)
                put("body", body)
                put("length", body.length)
            })
        }

        // ── HTTP Methods ───────────────────────────────────────────────────
        route("/api/users") {
            get {
                call.respond(buildJsonObject {
                    put("method", "GET")
                    put("users", buildJsonArray {
                        add(buildJsonObject { put("id", 1); put("name", "Alice"); put("email", "alice@example.com") })
                        add(buildJsonObject { put("id", 2); put("name", "Bob");   put("email", "bob@example.com") })
                        add(buildJsonObject { put("id", 3); put("name", "Carol"); put("email", "carol@example.com") })
                    })
                })
            }

            post {
                val body = call.receiveText()
                call.respond(HttpStatusCode.Created, buildJsonObject {
                    put("method", "POST")
                    put("message", "User created successfully")
                    put("receivedBody", body)
                })
            }

            put("/{id}") {
                val id = call.parameters["id"]
                val body = call.receiveText()
                call.respond(buildJsonObject {
                    put("method", "PUT")
                    put("message", "User $id fully replaced")
                    put("receivedBody", body)
                })
            }

            patch("/{id}") {
                val id = call.parameters["id"]
                val body = call.receiveText()
                call.respond(buildJsonObject {
                    put("method", "PATCH")
                    put("message", "User $id partially updated")
                    put("receivedBody", body)
                })
            }

            delete("/{id}") {
                val id = call.parameters["id"]
                call.respond(buildJsonObject {
                    put("method", "DELETE")
                    put("message", "User $id deleted")
                })
            }

            options {
                call.response.header("Allow", "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD")
                call.respond(buildJsonObject {
                    put("method", "OPTIONS")
                    put("allow", "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD")
                })
            }

            head {
                call.response.header("X-Total-Count", "3")
                call.response.header("X-Api-Version", "1.0")
                call.respond(HttpStatusCode.OK)
            }

            // TRACE — echo request method + headers back (like a real TRACE response)
            method(HttpMethod("TRACE")) {
                handle {
                    call.response.header("Content-Type", "message/http")
                    call.respond(buildJsonObject {
                        put("method", "TRACE")
                        put("url", call.request.local.uri)
                        put("echoedHeaders", buildJsonObject {
                            call.request.headers.entries().forEach { (k, v) ->
                                put(k, v.firstOrNull() ?: "")
                            }
                        })
                    })
                }
            }

            // CONNECT — returns 200 with a tunnel-established message (can't do real tunnelling)
            method(HttpMethod("CONNECT")) {
                handle {
                    call.respond(buildJsonObject {
                        put("method", "CONNECT")
                        put("message", "Tunnel established (simulation)")
                        put("target", call.request.local.uri)
                    })
                }
            }
        }

        // ── Standalone TRACE / CONNECT endpoints ───────────────────────────
        /**
         * TRACE /api/trace
         * Echoes back method, URL, and request headers as a diagnostic tool.
         * Useful for verifying pre-request header injection in scripts.
         */
        route("/api/trace") {
            method(HttpMethod("TRACE")) {
                handle {
                    call.response.header("Content-Type", "message/http")
                    call.respond(buildJsonObject {
                        put("method", "TRACE")
                        put("url", call.request.local.uri)
                        put("echoedHeaders", buildJsonObject {
                            call.request.headers.entries().forEach { (k, v) ->
                                put(k, v.firstOrNull() ?: "")
                            }
                        })
                        put("message", "TRACE echo — inspect echoedHeaders to verify your request")
                    })
                }
            }
        }

        /**
         * CONNECT /api/connect
         * Returns a 200 "tunnel established" simulation response.
         * Real CONNECT tunnelling is not applicable in a REST test server.
         */
        route("/api/connect") {
            method(HttpMethod("CONNECT")) {
                handle {
                    call.respond(buildJsonObject {
                        put("method", "CONNECT")
                        put("message", "Tunnel established (simulation)")
                        put("target", call.request.local.uri)
                    })
                }
            }
        }

        // ── Query Params ───────────────────────────────────────────────────

        // Returns raw query string info including multi-value detection.
        // Used by integration tests to verify params are not duplicated.
        get("/api/echo-query") {
            val params = call.request.queryParameters
            call.respond(buildJsonObject {
                put("paramCounts", buildJsonObject {
                    params.names().forEach { name ->
                        put(name, params.getAll(name)?.size ?: 0)
                    }
                })
                put("params", buildJsonObject {
                    params.names().forEach { name ->
                        put(name, params[name] ?: "")
                    }
                })
            })
        }

        get("/api/search") {
            val params = call.request.queryParameters
            val q     = params["q"]     ?: ""
            val page  = params["page"]  ?: "1"
            val limit = params["limit"] ?: "10"
            call.respond(buildJsonObject {
                put("query", q)
                put("page", page.toIntOrNull() ?: 1)
                put("limit", limit.toIntOrNull() ?: 10)
                put("results", buildJsonArray {
                    add(buildJsonObject { put("id", 1); put("title", "Result for '$q' #1") })
                    add(buildJsonObject { put("id", 2); put("title", "Result for '$q' #2") })
                })
                put("allParams", buildJsonObject {
                    params.entries().forEach { (key, values) ->
                        put(key, values.firstOrNull() ?: "")
                    }
                })
            })
        }

        // ── Headers Echo ───────────────────────────────────────────────────
        get("/api/echo-headers") {
            val headers = buildJsonObject {
                call.request.headers.entries().forEach { (key, values) ->
                    put(key, values.firstOrNull() ?: "")
                }
            }
            call.response.header("X-Echo-Server", "ReqLab/1.0")
            call.respond(buildJsonObject {
                put("message", "Received headers echoed below")
                put("receivedHeaders", headers)
            })
        }

        // ── Body Types ─────────────────────────────────────────────────────

        post("/api/json") {
            val body = call.receiveText()
            call.respond(buildJsonObject {
                put("message", "JSON body received")
                put("contentType", call.request.header("Content-Type") ?: "")
                put("body", body)
            })
        }

        post("/api/graphql") {
            val body = call.receiveText()
            val userId = Regex(""""id"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "1"
            val operationName = Regex(""""operationName"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                ?: if (body.contains("user", ignoreCase = true)) "user" else "query"
            call.respond(buildJsonObject {
                put("message", "GraphQL request received")
                put("contentType", call.request.header("Content-Type") ?: "")
                put("data", buildJsonObject {
                    put("user", buildJsonObject {
                        put("id", userId)
                        put("name", "GraphQL User")
                        put("type", "Subscriber")
                    })
                })
                put("extensions", buildJsonObject {
                    put("receivedOperation", operationName)
                })
            })
        }

        post("/api/raw") {
            val text = call.receiveText()
            call.respond(buildJsonObject {
                put("message", "Raw text received")
                put("contentType", call.request.header("Content-Type") ?: "")
                put("body", text)
            })
        }

        post("/api/form-data") {
            val fields = mutableMapOf<String, String>()
            val files  = mutableListOf<String>()
            call.receiveMultipart().forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> fields[part.name ?: "unknown"] = part.value
                    is PartData.FileItem -> {
                        val name = part.originalFileName ?: "unnamed"
                        @Suppress("DEPRECATION")
                        val size = part.streamProvider().readBytes().size
                        files += "$name (${size} bytes)"
                    }
                    else -> Unit
                }
                part.dispose()
            }
            call.respond(buildJsonObject {
                put("message", "Multipart form-data received")
                put("fields", buildJsonObject { fields.forEach { (k, v) -> put(k, v) } })
                put("files", buildJsonArray { files.forEach { add(it) } })
            })
        }

        post("/api/urlencoded") {
            val params = call.receiveParameters()
            call.respond(buildJsonObject {
                put("message", "URL-encoded form received")
                put("fields", buildJsonObject {
                    params.entries().forEach { (key, values) ->
                        put(key, values.firstOrNull() ?: "")
                    }
                })
            })
        }

        post("/api/upload") {
            val files = mutableListOf<JsonElement>()
            val contentType = call.request.header("Content-Type").orEmpty()

            if (contentType.startsWith("multipart/form-data", ignoreCase = true)) {
                call.receiveMultipart().forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            @Suppress("DEPRECATION")
                            val bytes = part.streamProvider().readBytes()
                            val filename = part.originalFileName ?: "unnamed"
                            val textPreview = bytes.takeIf { looksLikeTextContent(it, part.contentType?.toString()) }
                                ?.let { firstWordsPreview(it.decodeToString(), limit = 20) }
                            files += buildJsonObject {
                                put("filename", filename)
                                put("fieldName", part.name ?: "file")
                                put("sizeBytes", bytes.size)
                                if (textPreview != null) {
                                    put("summary", textPreview)
                                    put("summaryType", "textPreview")
                                } else {
                                    put("summary", filename)
                                    put("summaryType", "filename")
                                }
                            }
                        }
                        else -> Unit
                    }
                    part.dispose()
                }
            }

            if (files.isNotEmpty()) {
                call.respond(HttpStatusCode.OK, buildJsonObject {
                    put("message", "File(s) uploaded successfully")
                    put("mode", "multipart")
                    put("files", buildJsonArray { files.forEach { add(it) } })
                })
                return@post
            }

            val rawBytes = runCatching { call.receiveText().encodeToByteArray() }.getOrDefault(ByteArray(0))
            val filenameHint = call.request.header("X-ReqLab-Filename").orEmpty().ifBlank { "upload.bin" }
            val rawSummary = if (looksLikeTextContent(rawBytes, contentType)) {
                firstWordsPreview(rawBytes.decodeToString(), limit = 20)
            } else {
                filenameHint
            }
            call.respond(HttpStatusCode.OK, buildJsonObject {
                put("message", "Upload request accepted")
                put("mode", if (rawBytes.isNotEmpty()) "raw" else "empty")
                put("sizeBytes", rawBytes.size)
                put("contentType", contentType)
                put("summary", rawSummary)
                put("summaryType", if (looksLikeTextContent(rawBytes, contentType)) "textPreview" else "filename")
            })
        }

        // ── Authentication ─────────────────────────────────────────────────

        get("/api/auth/basic") {
            val authHeader = call.request.header("Authorization") ?: ""
            if (!authHeader.startsWith("Basic ")) {
                call.response.header("WWW-Authenticate", "Basic realm=\"ReqLab Sample Server\"")
                call.respond(HttpStatusCode.Unauthorized, buildJsonObject {
                    put("error", "Basic auth required")
                    put("hint", "Authorization: Basic base64(admin:password)")
                })
                return@get
            }
            val decoded = runCatching {
                String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ").trim()))
            }.getOrDefault("")
            val colon   = decoded.indexOf(':')
            val user    = if (colon >= 0) decoded.substring(0, colon) else decoded
            val pass    = if (colon >= 0) decoded.substring(colon + 1) else ""
            if (user == "admin" && pass == "password") {
                call.respond(buildJsonObject {
                    put("message", "Basic auth OK")
                    put("user", user)
                })
            } else {
                call.respond(HttpStatusCode.Unauthorized, buildJsonObject {
                    put("error", "Invalid credentials")
                    put("hint", "Use username=admin, password=password")
                })
            }
        }

        get("/api/auth/bearer") {
            val authHeader = call.request.header("Authorization") ?: ""
            val token = if (authHeader.startsWith("Bearer ")) authHeader.removePrefix("Bearer ").trim() else null
            if (token == "test-token") {
                call.respond(buildJsonObject {
                    put("message", "Bearer auth OK")
                    put("token", token)
                })
            } else {
                call.respond(HttpStatusCode.Unauthorized, buildJsonObject {
                    put("error", "Invalid or missing bearer token")
                    put("hint", "Authorization: Bearer test-token")
                    put("received", authHeader.ifEmpty { "<none>" })
                })
            }
        }

        get("/api/auth/apikey") {
            val key = call.request.header("X-API-Key") ?: ""
            if (key == "test-api-key") {
                call.respond(buildJsonObject {
                    put("message", "API key auth OK")
                    put("key", key)
                })
            } else {
                call.respond(HttpStatusCode.Unauthorized, buildJsonObject {
                    put("error", "Invalid or missing API key")
                    put("hint", "X-API-Key: test-api-key")
                    put("received", key.ifEmpty { "<none>" })
                })
            }
        }

        // ── Time (for pre-request script demo) ────────────────────────────
        get("/api/time") {
            val now = Instant.now()
            call.respond(buildJsonObject {
                put("epochMillis", now.toEpochMilli())
                put("iso8601", now.toString())
            })
        }

        // ── Cookies ────────────────────────────────────────────────────────
        get("/api/cookies") {
            call.response.cookies.append(Cookie("session", "abc123", path = "/"))
            call.response.cookies.append(Cookie("user", "reqlab-tester", path = "/"))
            call.respond(buildJsonObject {
                put("message", "Two cookies set in response (session, user)")
                put("receivedCookies", buildJsonObject {
                    call.request.cookies.rawCookies.forEach { (k, v) -> put(k, v) }
                })
            })
        }

        // ── Redirects ──────────────────────────────────────────────────────
        get("/api/redirect") {
            call.respondRedirect("/api/final", permanent = false)   // 302
        }

        get("/api/final") {
            call.respond(buildJsonObject {
                put("message", "You reached the redirect destination successfully")
                put("endpoint", "/api/final")
            })
        }

        // ── Error Responses ────────────────────────────────────────────────
        get("/api/error/{code}") {
            val code   = call.parameters["code"]?.toIntOrNull() ?: 400
            val status = HttpStatusCode.fromValue(code)
            call.respond(status, buildJsonObject {
                put("error", status.description)
                put("code", code)
                put("message", "Simulated ${status.value} response from ReqLab sample server")
            })
        }

        // ── Slow Request ───────────────────────────────────────────────────
        get("/api/slow") {
            val delayMs = call.request.queryParameters["ms"]?.toLongOrNull() ?: 3_000L
            delay(delayMs)
            call.respond(buildJsonObject {
                put("message", "Slow response delivered after ${delayMs}ms delay")
                put("delayMs", delayMs)
            })
        }

        // ── Large fixture payloads (from qa-tests/src/resources) ──────────
        get("/api/large-json") {
            val payload = loadQaResourceText("10mb-sample.json")
            if (payload == null) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    buildJsonObject {
                        put("error", "Fixture not found")
                        put("fixture", "qa-tests/src/resources/10mb-sample.json")
                    },
                )
                return@get
            }
            call.respondText(payload, ContentType.Application.Json, HttpStatusCode.OK)
        }

        get("/api/large-xml") {
            val payload = loadQaResourceText("5mb.xml")
            if (payload == null) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    buildJsonObject {
                        put("error", "Fixture not found")
                        put("fixture", "qa-tests/src/resources/5mb.xml")
                    },
                )
                return@get
            }
            call.respondText(payload, ContentType.Application.Xml, HttpStatusCode.OK)
        }

        // ── Scripting / chaining helpers ───────────────────────────────────

        /**
         * GET /api/timestamp
         * Returns the current server time in multiple formats.
         * Useful in pre-request scripts: env.set("ts", response.json().unix)
         */
        get("/api/timestamp") {
            val now = Instant.now()
            call.respond(buildJsonObject {
                put("unix", now.epochSecond)
                put("ms",   now.toEpochMilli())
                put("iso",  now.toString())
                put("tz",   "UTC")
            })
        }

        /**
         * POST /api/token
         * Body: { "user": "alice", "role": "admin" }
         * Returns a fake JWT-style token.  Pre-request scripts can fetch this
         * and store the token: env.set("token", response.json().token)
         */
        post("/api/token") {
            val body = runCatching { call.receiveText() }.getOrDefault("{}")
            val user = Regex(""""user"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "anonymous"
            val role = Regex(""""role"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "user"
            val token = "rl.${Base64.getEncoder().encodeToString("$user:$role:${System.currentTimeMillis()}".toByteArray())}"
            call.respond(buildJsonObject {
                put("token", token)
                put("user",  user)
                put("role",  role)
                put("expiresIn", 3600)
            })
        }

        /**
         * GET /api/protected
         * Requires header: X-Token  (set by a pre-request script)
         * Returns 401 if missing, 200 with user info if present.
         */
        get("/api/protected") {
            val token = call.request.header("X-Token")
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, buildJsonObject {
                    put("error", "Missing X-Token header")
                    put("hint",  "Add a pre-request script that sets the X-Token header via env.set()")
                })
            } else {
                val decoded = runCatching {
                    String(Base64.getDecoder().decode(token.removePrefix("rl.")))
                }.getOrDefault(token)
                call.respond(buildJsonObject {
                    put("message",  "Access granted")
                    put("token",    token)
                    put("decoded",  decoded)
                    put("resource", "protected data")
                })
            }
        }

        /**
         * POST /api/validate
         * Validates any JSON body and returns the field names found.
         * Post-request scripts can assert: expect(response.json().valid).to.equal(true)
         */
        post("/api/validate") {
            val body = runCatching { call.receiveText() }.getOrDefault("")
            val isValidJson = body.isNotBlank() && (body.trimStart().startsWith('{') || body.trimStart().startsWith('['))
            val fieldCount = Regex(""""(\w+)"\s*:""").findAll(body).count()
            call.respond(buildJsonObject {
                put("valid",      isValidJson)
                put("fieldCount", fieldCount)
                put("bodyLength", body.length)
                put("message",    if (isValidJson) "Valid JSON body received" else "Empty or non-JSON body")
            })
        }

        // ── sendRequest chain demo endpoints ──────────────────────────────

        /**
         * POST /api/chain/token
         * Issues a simple bearer token from `{"username":"..."}`.
         * Designed for `reqlab.sendRequest()` demos: a pre-request script can POST
         * here to obtain a token before the main request runs.
         *
         * Request body (JSON): {"username": "alice"}
         * Response: {"token": "...", "username": "alice", "expiresIn": 3600}
         */
        post("/api/chain/token") {
            val body = runCatching { call.receiveText() }.getOrDefault("{}")
            val username = Regex(""""username"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "guest"
            val token = "chain.${Base64.getEncoder().encodeToString("$username:${System.currentTimeMillis()}".toByteArray())}"
            call.respond(buildJsonObject {
                put("token",     token)
                put("username",  username)
                put("expiresIn", 3600)
            })
        }

        /**
         * GET /api/chain/data
         * Requires `Authorization: Bearer <token>` header (obtained from /api/chain/token).
         * Returns 401 if header is missing, 200 with decoded username if present.
         *
         * Demonstrates the classic sendRequest auth-then-fetch pattern.
         */
        get("/api/chain/data") {
            val authHeader = call.request.header("Authorization") ?: ""
            if (!authHeader.startsWith("Bearer chain.")) {
                call.respond(HttpStatusCode.Unauthorized, buildJsonObject {
                    put("error",   "Missing or invalid Authorization header")
                    put("hint",    "Use a pre-request script: reqlab.sendRequest to POST /api/chain/token, then set Authorization header")
                })
            } else {
                val encoded = authHeader.removePrefix("Bearer chain.")
                val decoded = runCatching {
                    String(Base64.getDecoder().decode(encoded))
                }.getOrDefault(encoded)
                val username = decoded.substringBefore(":")
                call.respond(buildJsonObject {
                    put("message",  "Chain access granted")
                    put("username", username)
                    put("data",     buildJsonObject {
                        put("items",    buildJsonArray { add("alpha"); add("beta"); add("gamma") })
                        put("total",    3)
                        put("resource", "chain-protected-data")
                    })
                })
            }
        }

        /**
         * GET /api/users/{id}
         * Returns a single user by numeric ID (1–3).
         * Useful for sendRequest chaining: a script can look up a user then inject
         * the user's role or email into the main request headers.
         *
         * Response: {"id": 1, "name": "Alice", "email": "alice@example.com", "role": "admin"}
         */
        get("/api/users/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            val users = mapOf(
                1 to Triple("Alice", "alice@example.com", "admin"),
                2 to Triple("Bob",   "bob@example.com",   "user"),
                3 to Triple("Carol", "carol@example.com", "moderator"),
            )
            val user = users[id]
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, buildJsonObject {
                    put("error", "User $id not found")
                    put("validIds", buildJsonArray { add(1); add(2); add(3) })
                })
            } else {
                call.respond(buildJsonObject {
                    put("id",    id)
                    put("name",  user.first)
                    put("email", user.second)
                    put("role",  user.third)
                })
            }
        }

        /**
         * GET /api/echo-full
         * Echoes method, URL, all headers, and query parameters back to the caller.
         * Useful for verifying that pre-request scripts injected the right values.
         */
        get("/api/echo-full") {
            val headers = buildJsonObject {
                call.request.headers.entries().forEach { (k, v) -> put(k, v.firstOrNull() ?: "") }
            }
            val params = buildJsonObject {
                call.request.queryParameters.entries().forEach { (k, v) -> put(k, v.firstOrNull() ?: "") }
            }
            call.respond(buildJsonObject {
                put("method",  "GET")
                put("path",    call.request.local.uri)
                put("headers", headers)
                put("params",  params)
                put("message", "Full echo – inspect headers/params injected by your pre-request script")
            })
        }
        // ── Raw body type endpoints (XML / HTML / JavaScript) ─────────────

        /**
         * GET /api/xml-response
         * Returns a well-formed XML document so the body editor can display it with
         * XML syntax highlighting and code folding.
         */
        get("/api/xml-response") {
            call.respondText(
                """<?xml version="1.0" encoding="UTF-8"?>
<catalog>
  <book id="1">
    <title>ReqLab Guide</title>
    <author>ReqLab Team</author>
    <year>2024</year>
    <tags>
      <tag>testing</tag>
      <tag>api</tag>
    </tags>
  </book>
  <book id="2">
    <title>Kotlin Multiplatform</title>
    <author>JetBrains</author>
    <year>2023</year>
    <tags>
      <tag>kotlin</tag>
      <tag>multiplatform</tag>
    </tags>
  </book>
</catalog>""",
                ContentType.Application.Xml,
                HttpStatusCode.OK,
            )
        }

        /**
         * POST /api/xml
         * Accepts an XML body. Echoes the received content-type and body length.
         */
        post("/api/xml") {
            val body = call.receiveText()
            call.respond(buildJsonObject {
                put("message", "XML body received")
                put("contentType", call.request.header("Content-Type") ?: "")
                put("bodyLength", body.length)
                put("body", body)
            })
        }

        /**
         * GET /api/html-response
         * Returns an HTML page so the body editor can render it with HTML highlighting.
         */
        get("/api/html-response") {
            call.respondText(
                """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>ReqLab Sample</title>
</head>
<body>
  <h1>ReqLab Sample Server</h1>
  <p>This HTML response is served from the <code>/api/html-response</code> endpoint.</p>
  <ul>
    <li>Syntax highlighting: ✓</li>
    <li>Code folding: ✓</li>
    <li>Preview mode: ✓</li>
  </ul>
</body>
</html>""",
                ContentType.Text.Html,
                HttpStatusCode.OK,
            )
        }

        /**
         * POST /api/html
         * Accepts an HTML body. Echoes content-type and length.
         */
        post("/api/html") {
            val body = call.receiveText()
            call.respond(buildJsonObject {
                put("message", "HTML body received")
                put("contentType", call.request.header("Content-Type") ?: "")
                put("bodyLength", body.length)
                put("body", body)
            })
        }

        /**
         * GET /api/js-response
         * Returns a JavaScript snippet so the body editor highlights it as JS.
         */
        get("/api/js-response") {
            call.respondText(
                """// ReqLab sample JavaScript response
const api = {
  baseUrl: "http://localhost:8080",
  version: "1.0",
  endpoints: [
    "/api/xml-response",
    "/api/html-response",
    "/api/js-response",
  ],
  ping: function() {
    return fetch(this.baseUrl + "/");
  },
};

module.exports = api;""",
                ContentType.parse("application/javascript"),
                HttpStatusCode.OK,
            )
        }

        /**
         * POST /api/javascript
         * Accepts a JavaScript body. Echoes content-type and length.
         */
        post("/api/javascript") {
            val body = call.receiveText()
            call.respond(buildJsonObject {
                put("message", "JavaScript body received")
                put("contentType", call.request.header("Content-Type") ?: "")
                put("bodyLength", body.length)
                put("body", body)
            })
        }

        /**
         * GET /api/json-valid
         * Returns a richly-nested JSON structure to exercise JSON highlighting and folding.
         */
        get("/api/json-valid") {
            call.respond(buildJsonObject {
                put("status", "ok")
                put("version", "1.0")
                put("metadata", buildJsonObject {
                    put("server", "ReqLab Sample")
                    put("generated", Instant.now().toString())
                    put("tags", buildJsonArray { add("json"); add("valid"); add("nested") })
                })
                put("users", buildJsonArray {
                    add(buildJsonObject {
                        put("id", 1); put("name", "Alice"); put("active", true)
                        put("roles", buildJsonArray { add("admin"); add("user") })
                    })
                    add(buildJsonObject {
                        put("id", 2); put("name", "Bob"); put("active", false)
                        put("roles", buildJsonArray { add("user") })
                    })
                })
                put("config", buildJsonObject {
                    put("maxConnections", 100)
                    put("timeoutMs", 30000)
                    put("features", buildJsonObject {
                        put("xmlSupport", true)
                        put("htmlSupport", true)
                        put("jsSupport", true)
                    })
                })
            })
        }

        /**
         * GET /api/json-malformed
         * Returns a response with Content-Type: application/json but an intentionally
         * broken body – used to test the JSON error banner in the body editor.
         */
        get("/api/json-malformed") {
            call.response.header("Content-Type", "application/json")
            call.respondText(
                """{ "status": "broken", "missing_close": [1, 2, 3""",
                ContentType.Application.Json,
                HttpStatusCode.OK,
            )
        }
        /**
         * GET /api/scope-info
         * Returns information about all scripting variable scopes.
         * Useful as a post-request endpoint when testing has/clear/replaceIn APIs.
         */
        get("/api/scope-info") {
            call.respond(buildJsonObject {
                put("message", "Use this endpoint to test variable scope scripting APIs")
                put("supportedScopes", buildJsonArray {
                    add("environment"); add("globals"); add("collectionVariables"); add("variables")
                })
                put("supportedMethods", buildJsonArray {
                    add("get"); add("set"); add("unset"); add("has"); add("clear"); add("toObject"); add("replaceIn")
                })
            })
        }

        /**
         * GET /api/info
         * Returns stub values that mirror reqlab.info fields.
         * Useful for testing pm.info migration: reqlab.info.requestName etc.
         */
        get("/api/info") {
            call.respond(buildJsonObject {
                put("requestName", "")
                put("requestId", "")
                put("iteration", 1)
                put("iterationCount", 1)
                put("eventName", "")
                put("message", "Mirrors reqlab.info stub fields")
            })
        }

        // ── OpenAI-compatible LLM mock ─────────────────────────────────────

        get("/v1/models") {
            if (!call.requireLlmBearer()) return@get
            call.respond(buildJsonObject {
                put("object", "list")
                put("data", buildJsonArray {
                    add(buildJsonObject {
                        put("id", "mock-gpt")
                        put("object", "model")
                        put("owned_by", "reqlab")
                    })
                    add(buildJsonObject {
                        put("id", "mock-embed")
                        put("object", "model")
                        put("owned_by", "reqlab")
                    })
                })
            })
        }

        get("/v1/chat/slow") {
            if (!call.requireLlmBearer()) return@get
            val delayMs = call.request.queryParameters["ms"]?.toLongOrNull() ?: 1_000L
            delay(delayMs)
            call.respond(openAiChatCompletionJson(content = LLM_SHORT_REPLY, finishReason = "stop"))
        }

        post("/v1/embeddings") {
            if (!call.requireLlmBearer()) return@post
            val body = runCatching { call.receiveText() }.getOrDefault("")
            val model = Regex(""""model"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "mock-embed"
            call.respond(buildJsonObject {
                put("object", "list")
                put("model", model)
                put("data", buildJsonArray {
                    add(buildJsonObject {
                        put("object", "embedding")
                        put("index", 0)
                        put("embedding", buildJsonArray {
                            add(0.1); add(0.2); add(0.3); add(0.4)
                            add(0.5); add(0.6); add(0.7); add(0.8)
                        })
                    })
                })
                put("usage", buildJsonObject {
                    put("prompt_tokens", 8)
                    put("total_tokens", 8)
                })
            })
        }

        post("/v1/chat/ndjson") {
            if (!call.requireLlmBearer()) return@post
            call.respondTextWriter(contentType = ContentType.parse("application/x-ndjson")) {
                write("""{"message":{"role":"assistant","content":"Hello"},"done":false}""" + "\n")
                flush()
                delay(15)
                write("""{"message":{"role":"assistant","content":" from"},"done":false}""" + "\n")
                flush()
                delay(15)
                write("""{"message":{"role":"assistant","content":" ReqLab"},"done":true}""" + "\n")
                flush()
            }
        }

        post("/v1/chat/completions") {
            if (!call.requireLlmBearer()) return@post
            when (call.request.queryParameters["fail"]) {
                "429" -> {
                    call.respond(HttpStatusCode.TooManyRequests, openAiError("rate_limit_exceeded", "Rate limit exceeded"))
                    return@post
                }
                "500" -> {
                    call.respond(HttpStatusCode.InternalServerError, openAiError("server_error", "Simulated upstream failure"))
                    return@post
                }
            }
            val delayMs = call.request.queryParameters["ms"]?.toLongOrNull() ?: 0L
            if (delayMs > 0) delay(delayMs)

            val body = runCatching { call.receiveText() }.getOrDefault("")
            val stream = Regex(""""stream"\s*:\s*true""").containsMatchIn(body)
            val hasTools = body.contains("\"tools\"")
            val jsonMode = body.contains("json_object")
            val earlyClose = call.request.queryParameters["earlyClose"] == "true"
            val demo = call.request.queryParameters["demo"] == "true"
            val reply = if (demo) LLM_DEMO_REPLY else LLM_SHORT_REPLY
            val defaultChunkMs = if (demo) 200L else 20L
            val chunkDelayMs = call.request.queryParameters["chunkMs"]?.toLongOrNull() ?: defaultChunkMs

            if (hasTools && !stream) {
                call.respond(openAiToolCallCompletionJson())
                return@post
            }

            if (jsonMode && !stream) {
                call.respond(openAiChatCompletionJson(content = """{"ok":true,"source":"reqlab"}""", finishReason = "stop"))
                return@post
            }

            if (stream) {
                call.response.header("Cache-Control", "no-cache")
                call.respondTextWriter(contentType = ContentType.parse("text/event-stream")) {
                    write("data: ${openAiChunkJson("", finishReason = null, role = "assistant")}\n\n")
                    flush()
                    llmWordTokens(reply).forEach { token ->
                        write("data: ${openAiChunkJson(token, finishReason = null)}\n\n")
                        flush()
                        delay(chunkDelayMs)
                    }
                    if (earlyClose) return@respondTextWriter
                    write("data: ${openAiChunkJson("", finishReason = "stop", usage = true)}\n\n")
                    write("data: [DONE]\n\n")
                    flush()
                }
                return@post
            }

            call.respond(openAiChatCompletionJson(content = reply, finishReason = "stop"))
        }

        // ── WebSocket – echo ───────────────────────────────────────────────
        webSocket("/ws") {
            send(Frame.Text("Connected to ReqLab WebSocket echo server. Send any message and it will be echoed."))
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text  -> send(Frame.Text("Echo: ${frame.readText()}"))
                    is Frame.Binary -> send(Frame.Binary(true, frame.data))
                    else           -> Unit
                }
            }
        }

        mcpAndOAuthRoutes()
    }
}

private suspend fun ApplicationCall.requireLlmBearer(): Boolean {
    val header = request.header("Authorization").orEmpty()
    val token = if (header.startsWith("Bearer ")) header.removePrefix("Bearer ").trim() else ""
    if (token == "test-token" || token == "llm-test-key") return true
    respond(HttpStatusCode.Unauthorized, openAiError("invalid_api_key", "Invalid or missing API key"))
    return false
}

private fun openAiError(type: String, message: String) = buildJsonObject {
    put("error", buildJsonObject {
        put("message", message)
        put("type", type)
        put("code", type)
    })
}

const val LLM_SHORT_REPLY = "Hello from ReqLab"

const val LLM_DEMO_REPLY =
    "Hello. ReqLab can test OpenAI-style chat APIs with one POST. " +
        "When stream is true, this connection stays open and each token arrives as an SSE event. " +
        "Watch the Body pane fill in, then open RAW to inspect every chunk."

internal fun llmWordTokens(text: String): List<String> {
    val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return buildList {
        add(words.first())
        words.drop(1).forEach { add(" $it") }
    }
}

private fun jsonQuote(value: String): String = JsonPrimitive(value).toString()

private fun openAiChatCompletionJson(content: String, finishReason: String) = buildJsonObject {
    put("id", "chatcmpl-reqlab-mock")
    put("object", "chat.completion")
    put("created", Instant.now().epochSecond)
    put("model", "mock-gpt")
    put("system_fingerprint", "fp_reqlab_mock")
    put("choices", buildJsonArray {
        add(buildJsonObject {
            put("index", 0)
            put("message", buildJsonObject {
                put("role", "assistant")
                put("content", content)
            })
            put("finish_reason", finishReason)
        })
    })
    put("usage", buildJsonObject {
        put("prompt_tokens", 24)
        put("completion_tokens", 48)
        put("total_tokens", 72)
    })
}

private fun openAiToolCallCompletionJson() = buildJsonObject {
    put("id", "chatcmpl-reqlab-tools")
    put("object", "chat.completion")
    put("model", "mock-gpt")
    put("choices", buildJsonArray {
        add(buildJsonObject {
            put("index", 0)
            put("message", buildJsonObject {
                put("role", "assistant")
                put("content", JsonNull)
                put("tool_calls", buildJsonArray {
                    add(buildJsonObject {
                        put("id", "call_1")
                        put("type", "function")
                        put("function", buildJsonObject {
                            put("name", "lookup_user")
                            put("arguments", """{"id":1}""")
                        })
                    })
                })
            })
            put("finish_reason", "tool_calls")
        })
    })
    put("usage", buildJsonObject {
        put("prompt_tokens", 18)
        put("completion_tokens", 10)
        put("total_tokens", 28)
    })
}

private fun openAiChunkJson(
    content: String,
    finishReason: String?,
    usage: Boolean = false,
    role: String? = null,
): String {
    val reason = if (finishReason == null) "null" else jsonQuote(finishReason)
    val delta = buildList {
        if (role != null) add("\"role\":${jsonQuote(role)}")
        add("\"content\":${jsonQuote(content)}")
    }.joinToString(",")
    val usageJson = if (usage) {
        ""","usage":{"prompt_tokens":24,"completion_tokens":48,"total_tokens":72}"""
    } else {
        ""
    }
    return """{"id":"chatcmpl-reqlab-mock","object":"chat.completion.chunk","model":"mock-gpt","choices":[{"index":0,"delta":{$delta},"finish_reason":$reason}]$usageJson}"""
}

private fun looksLikeTextContent(bytes: ByteArray, contentType: String?): Boolean {
    val normalizedType = contentType?.lowercase().orEmpty()
    if (normalizedType.startsWith("text/")) return true
    if (normalizedType.contains("json") || normalizedType.contains("xml") || normalizedType.contains("graphql") || normalizedType.contains("x-www-form-urlencoded")) {
        return true
    }
    if (bytes.isEmpty()) return false
    val sample = bytes.take(256)
    val controlChars = sample.count { b ->
        val c = b.toInt() and 0xFF
        c in 0..8 || c in 14..31
    }
    return controlChars < sample.size / 10
}

private fun firstWordsPreview(text: String, limit: Int): String {
    val words = text
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return ""
    val preview = words.take(limit).joinToString(" ")
    return if (words.size > limit) "$preview…" else preview
}

private fun loadQaResourceText(fileName: String): String? {
    val classpathText = Application::class.java.classLoader
        ?.getResourceAsStream(fileName)
        ?.bufferedReader()
        ?.use { it.readText() }
    if (classpathText != null) return classpathText

    val candidates = listOf(
        "qa-tests/src/resources/$fileName",
        "../qa-tests/src/resources/$fileName",
        "../../qa-tests/src/resources/$fileName",
    )
    for (path in candidates) {
        val file = File(path)
        if (file.exists()) return file.readText()
    }
    return null
}
