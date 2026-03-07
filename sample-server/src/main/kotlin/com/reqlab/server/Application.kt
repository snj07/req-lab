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
            call.receiveMultipart().forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        @Suppress("DEPRECATION")
                        val bytes = part.streamProvider().readBytes()
                        files += buildJsonObject {
                            put("filename", part.originalFileName ?: "unnamed")
                            put("fieldName", part.name ?: "file")
                            put("sizeBytes", bytes.size)
                        }
                    }
                    else -> Unit
                }
                part.dispose()
            }
            if (files.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, buildJsonObject {
                    put("error", "No file part found in multipart request")
                })
            } else {
                call.respond(HttpStatusCode.OK, buildJsonObject {
                    put("message", "File(s) uploaded successfully")
                    put("files", buildJsonArray { files.forEach { add(it) } })
                })
            }
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
