package com.reqlab.server

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.Base64

fun main() {
    println("==========================================================")
    println("  ReqLab Sample API Server")
    println("  Listening on  http://localhost:8080")
    println("  WebSocket     ws://localhost:8080/ws")
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
        }

        // ── Query Params ───────────────────────────────────────────────────
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

        // ── Scripting / chaining helpers ───────────────────────────────────

        /**
         * GET /api/timestamp
         * Returns the current server time in multiple formats.
         * Useful in pre-request scripts: pm.environment.set("ts", pm.response.json().unix)
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
         * and store the token: pm.environment.set("token", pm.response.json().token)
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
                    put("hint",  "Add a pre-request script that sets the X-Token header via pm.environment.set()")
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
         * Test scripts can assert: pm.expect(pm.response.json().valid).to.equal(true)
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
    }
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
