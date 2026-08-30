package com.reqlab.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal val mcpMockJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal const val MCP_WAIT_FOR_KEY = "_reqlabWaitFor"
internal const val MCP_CALLBACK_SAMPLE = "srv-sample"
internal const val MCP_CALLBACK_ELICIT = "srv-elicit"
internal const val MCP_CALLBACK_ROOTS = "srv-roots"
internal const val MCP_CALLBACK_PING = "srv-ping"
internal const val MCP_CALLBACK_TIMEOUT_MS = 60_000L

data class McpMockSession(
    val id: String = UUID.randomUUID().toString(),
    val subscribed: MutableSet<String> = ConcurrentHashMap.newKeySet(),
    var logLevel: String = "info",
    val lastReplies: ConcurrentHashMap<String, JsonObject> = ConcurrentHashMap(),
    val callbackReplies: ConcurrentHashMap<String, CompletableDeferred<JsonObject>> = ConcurrentHashMap(),
    val serverPushes: Channel<McpOutbound> = Channel(Channel.UNLIMITED),
)

data class McpOutbound(
    val envelope: JsonObject,
    val sseEventId: String? = null,
    val eventType: String = "message",
)

object McpMockProtocol {
    val sessions = ConcurrentHashMap<String, McpMockSession>()
    val oauthClients = ConcurrentHashMap<String, OAuthClientRecord>()
    val oauthCodes = ConcurrentHashMap<String, OAuthCodeRecord>()
    val oauthTokens = ConcurrentHashMap<String, String>()
    const val ISSUED_ACCESS_TOKEN = "mcp-oauth-token"
    const val ISSUED_REFRESH_TOKEN = "mcp-refresh-token"

    fun session(id: String?): McpMockSession? = id?.let { sessions[it] }

    fun requireOrCreate(id: String?): McpMockSession {
        if (id != null) sessions[id]?.let { return it }
        val created = McpMockSession(id ?: UUID.randomUUID().toString())
        sessions[created.id] = created
        return created
    }

    fun handle(
        raw: String,
        session: McpMockSession,
        extraOut: MutableList<McpOutbound> = mutableListOf(),
    ): JsonObject? {
        val obj = runCatching { mcpMockJson.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return rpcError(null, -32700, "Parse error")
        val method = obj["method"]?.jsonPrimitive?.contentOrNull
        val id = obj["id"]
        val params = obj["params"] as? JsonObject
        if (method == null) {
            val isResponse = obj.containsKey("result") || obj.containsKey("error")
            if (isResponse && id != null && id !is JsonNull) {
                completeCallback(session, jsonIdKey(id), obj)
                return null
            }
            return if (id != null && id !is JsonNull) rpcError(id, -32600, "Invalid request") else null
        }
        if (id == null || id is JsonNull) {
            // notification
            return null
        }
        return when (method) {
            "initialize" -> rpcResult(id, initializeResult())
            "ping" -> rpcResult(id, buildJsonObject {})
            "tools/list" -> rpcResult(id, toolsList(params))
            "tools/call" -> callTool(id, params, session, extraOut)
            "resources/list" -> rpcResult(id, resourcesList())
            "resources/templates/list" -> rpcResult(id, resourceTemplates())
            "resources/read" -> rpcResult(id, resourceRead(params))
            "resources/subscribe" -> {
                val uri = params?.string("uri").orEmpty()
                session.subscribed += uri
                extraOut += McpOutbound(rpcNotification("notifications/resources/updated", buildJsonObject { put("uri", uri) }))
                rpcResult(id, buildJsonObject {})
            }
            "resources/unsubscribe" -> {
                session.subscribed -= params?.string("uri").orEmpty()
                rpcResult(id, buildJsonObject {})
            }
            "prompts/list" -> rpcResult(id, promptsList())
            "prompts/get" -> rpcResult(id, promptGet(params))
            "completion/complete" -> rpcResult(id, complete(params))
            "logging/setLevel" -> {
                session.logLevel = params?.string("level") ?: "info"
                rpcResult(id, buildJsonObject {})
            }
            else -> rpcError(id, -32601, "Method not found: $method")
        }
    }

    private fun initializeResult() = buildJsonObject {
        put("protocolVersion", "2025-06-18")
        put("capabilities", buildJsonObject {
            put("tools", buildJsonObject { put("listChanged", true) })
            put("resources", buildJsonObject {
                put("subscribe", true)
                put("listChanged", true)
            })
            put("prompts", buildJsonObject { put("listChanged", true) })
            put("logging", buildJsonObject {})
            put("completions", buildJsonObject {})
        })
        put("serverInfo", buildJsonObject {
            put("name", "ReqLab MCP Mock")
            put("version", "1.18.0")
        })
        put("instructions", "Deterministic ReqLab sample MCP server")
    }

    private fun toolsList(params: JsonObject?): JsonObject {
        val cursor = params?.string("cursor")
        val tools = listOf(
            tool("echo", "Echo the text argument", "text"),
            tool("add", "Add two numbers", "a", "b"),
            tool("fail", "Return a tool-level isError result"),
            tool("slow", "Emit progress notifications then finish"),
            tool("trigger_sampling", "Ask the client to sample a message"),
            tool("trigger_elicitation", "Ask the client to fill a form"),
            tool("trigger_roots", "Ask the client for roots/list"),
            tool("trigger_ping", "Ask the client to answer ping"),
        )
        return if (cursor == "page2") {
            buildJsonObject { put("tools", buildJsonArray {}) }
        } else {
            buildJsonObject {
                put("tools", buildJsonArray { tools.forEach { add(it) } })
            }
        }
    }

    private fun tool(name: String, description: String, vararg props: String) = buildJsonObject {
        put("name", name)
        put("description", description)
        put("inputSchema", buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                props.forEach { put(it, buildJsonObject { put("type", "string") }) }
            })
        })
    }

    private fun callTool(
        id: JsonElement,
        params: JsonObject?,
        session: McpMockSession,
        extraOut: MutableList<McpOutbound>,
    ): JsonObject {
        val name = params?.string("name") ?: return rpcError(id, -32602, "Missing tool name")
        val args = params["arguments"] as? JsonObject
        val token = ((params["_meta"] as? JsonObject)?.get("progressToken"))
        return when (name) {
            "echo" -> rpcResult(id, toolText(args?.string("text") ?: ""))
            "add" -> {
                val a = args?.string("a")?.toIntOrNull() ?: args?.get("a")?.jsonPrimitive?.intOrNull ?: 0
                val b = args?.string("b")?.toIntOrNull() ?: args?.get("b")?.jsonPrimitive?.intOrNull ?: 0
                rpcResult(id, toolText((a + b).toString()))
            }
            "fail" -> rpcResult(id, toolText("tool failed", isError = true))
            "slow" -> {
                if (token != null) {
                    extraOut += McpOutbound(rpcNotification("notifications/progress", buildJsonObject {
                        put("progressToken", token)
                        put("progress", 1)
                        put("total", 2)
                        put("message", "halfway")
                    }))
                }
                rpcResult(id, toolText("done"))
            }
            "trigger_sampling" -> triggerCallback(
                session = session,
                extraOut = extraOut,
                callId = id,
                callbackId = MCP_CALLBACK_SAMPLE,
                envelope = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", MCP_CALLBACK_SAMPLE)
                    put("method", "sampling/createMessage")
                    put("params", buildJsonObject {
                        put("messages", buildJsonArray {
                            add(buildJsonObject {
                                put("role", "user")
                                put("content", buildJsonObject {
                                    put("type", "text")
                                    put("text", "Say hi")
                                })
                            })
                        })
                        put("maxTokens", 32)
                    })
                },
            )
            "trigger_elicitation" -> triggerCallback(
                session = session,
                extraOut = extraOut,
                callId = id,
                callbackId = MCP_CALLBACK_ELICIT,
                envelope = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", MCP_CALLBACK_ELICIT)
                    put("method", "elicitation/create")
                    put("params", buildJsonObject {
                        put("message", "What is your name?")
                        put("requestedSchema", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("name", buildJsonObject { put("type", "string") })
                            })
                        })
                    })
                },
            )
            "trigger_roots" -> triggerCallback(
                session = session,
                extraOut = extraOut,
                callId = id,
                callbackId = MCP_CALLBACK_ROOTS,
                envelope = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", MCP_CALLBACK_ROOTS)
                    put("method", "roots/list")
                    put("params", buildJsonObject {})
                },
            )
            "trigger_ping" -> triggerCallback(
                session = session,
                extraOut = extraOut,
                callId = id,
                callbackId = MCP_CALLBACK_PING,
                envelope = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", MCP_CALLBACK_PING)
                    put("method", "ping")
                    put("params", buildJsonObject {})
                },
            )
            else -> rpcError(id, -32602, "Unknown tool $name")
        }
    }

    private fun triggerCallback(
        session: McpMockSession,
        extraOut: MutableList<McpOutbound>,
        callId: JsonElement,
        callbackId: String,
        envelope: JsonObject,
    ): JsonObject {
        session.callbackReplies.getOrPut(callbackId) { CompletableDeferred() }
        extraOut += McpOutbound(envelope)
        return waitMarker(callId, callbackId)
    }

    fun waitMarker(callId: JsonElement, callbackId: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", callId)
        put(MCP_WAIT_FOR_KEY, callbackId)
    }

    fun waitForCallbackId(result: JsonObject?): String? =
        result?.get(MCP_WAIT_FOR_KEY)?.jsonPrimitive?.contentOrNull

    fun completeCallback(session: McpMockSession, id: String?, envelope: JsonObject) {
        if (id.isNullOrBlank()) return
        session.lastReplies[id] = envelope
        session.callbackReplies.getOrPut(id) { CompletableDeferred() }.complete(envelope)
    }

    suspend fun awaitCallback(
        session: McpMockSession,
        id: String,
        timeoutMs: Long = MCP_CALLBACK_TIMEOUT_MS,
    ): JsonObject? {
        session.lastReplies[id]?.let { return it }
        val deferred = session.callbackReplies.getOrPut(id) { CompletableDeferred() }
        session.lastReplies[id]?.let { return it }
        return withTimeoutOrNull(timeoutMs) { deferred.await() }
    }

    suspend fun resolveWaitResult(session: McpMockSession, result: JsonObject?): JsonObject? {
        val waitFor = waitForCallbackId(result) ?: return result
        val callId = result?.get("id") ?: return result
        val echoed = awaitCallback(session, waitFor)
        return if (echoed == null) {
            rpcResult(callId, toolText("Timed out waiting for client reply to $waitFor", isError = true))
        } else {
            rpcResult(callId, toolText(echoed.toString()))
        }
    }

    fun jsonIdKey(id: JsonElement?): String? {
        if (id == null || id is JsonNull) return null
        val primitive = id as? JsonPrimitive ?: return id.toString()
        return primitive.content
    }

    private fun resourcesList() = buildJsonObject {
        put("resources", buildJsonArray {
            add(buildJsonObject {
                put("uri", "reqlab://docs/welcome")
                put("name", "welcome")
                put("mimeType", "text/plain")
            })
        })
    }

    private fun resourceTemplates() = buildJsonObject {
        put("resourceTemplates", buildJsonArray {
            add(buildJsonObject {
                put("uriTemplate", "reqlab://docs/{name}")
                put("name", "docs")
            })
        })
    }

    private fun resourceRead(params: JsonObject?): JsonObject {
        val uri = params?.string("uri") ?: return buildJsonObject { put("contents", buildJsonArray {}) }
        return buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("uri", uri)
                    put("mimeType", "text/plain")
                    put("text", "Welcome to ReqLab MCP ($uri)")
                })
            })
        }
    }

    private fun promptsList() = buildJsonObject {
        put("prompts", buildJsonArray {
            add(buildJsonObject {
                put("name", "greet")
                put("description", "Greet someone")
                put("arguments", buildJsonArray {
                    add(buildJsonObject {
                        put("name", "name")
                        put("required", true)
                    })
                })
            })
        })
    }

    private fun promptGet(params: JsonObject?): JsonObject {
        val name = (params?.get("arguments") as? JsonObject)?.string("name") ?: "world"
        return buildJsonObject {
            put("description", "greeting")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonObject {
                        put("type", "text")
                        put("text", "Hello $name")
                    })
                })
            })
        }
    }

    private fun complete(params: JsonObject?): JsonObject {
        val argument = (params?.get("argument") as? JsonObject)?.string("value").orEmpty()
        val values = listOf("welcome", "secret", "guide").filter { it.startsWith(argument) }
        return buildJsonObject {
            put("completion", buildJsonObject {
                put("values", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
                put("hasMore", false)
            })
        }
    }

    fun sseFrame(outbound: McpOutbound, eventId: Int): String = buildString {
        append("id: ").append(eventId).append('\n')
        if (outbound.eventType != "message") append("event: ").append(outbound.eventType).append('\n')
        append("data: ").append(outbound.envelope.toString()).append("\n\n")
    }

    fun sseEndpointFrame(path: String): String = "event: endpoint\ndata: $path\n\n"

    internal fun toolText(text: String, isError: Boolean = false) = buildJsonObject {
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        })
        put("isError", isError)
    }

    fun rpcResult(id: JsonElement, result: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    fun rpcError(id: JsonElement?, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        if (id != null) put("id", id) else put("id", JsonNull)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
        })
    }

    fun rpcNotification(method: String, params: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", method)
        put("params", params)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}

data class OAuthClientRecord(val clientId: String, val redirectUris: List<String>)
data class OAuthCodeRecord(val code: String, val clientId: String, val codeChallenge: String, val redirectUri: String)

internal fun sha256Base64Url(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
