package com.reqlab.core.network.mcp

import com.reqlab.core.model.McpContent
import com.reqlab.core.model.McpCreateMessageRequest
import com.reqlab.core.model.McpCreateMessageResult
import com.reqlab.core.network.LlmTextAssembler
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.min

fun cancelledMcpSamplingResult(): McpCreateMessageResult = McpCreateMessageResult(
    content = McpContent(type = "text", text = ""),
    stopReason = "cancelled",
)

fun emptyMcpSamplingResult(): McpCreateMessageResult = McpCreateMessageResult(
    role = "assistant",
    content = McpContent(type = "text", text = ""),
    model = "mock",
    stopReason = "endTurn",
)

suspend fun forwardMcpSampling(
    httpClient: HttpClient,
    url: String,
    bearerToken: String?,
    request: McpCreateMessageRequest,
    maxTokensCap: Int? = null,
): McpCreateMessageResult {
    val maxTokens = when {
        maxTokensCap != null -> min(request.maxTokens, maxTokensCap)
        else -> request.maxTokens
    }.coerceAtLeast(1)
    val model = request.modelPreferences?.hints?.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: "mock-gpt"
    val messages = buildJsonArray {
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            addJsonObject {
                put("role", "system")
                put("content", prompt)
            }
        }
        request.messages.forEach { message ->
            addJsonObject {
                put("role", message.role)
                put("content", message.content.text.orEmpty())
            }
        }
    }
    val body = buildJsonObject {
        put("model", model)
        put("messages", messages)
        put("max_tokens", maxTokens)
        request.temperature?.let { put("temperature", it) }
    }
    val response = httpClient.post(url) {
        contentType(ContentType.Application.Json)
        if (!bearerToken.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $bearerToken")
        setBody(body.toString())
    }
    val text = response.bodyAsText()
    if (response.status.value >= 400) {
        throw McpProtocolException("LLM HTTP ${response.status.value}: ${text.take(240)}")
    }
    val parsed = runCatching { mcpJson.parseToJsonElement(text).jsonObject }.getOrNull()
    val content = LlmTextAssembler.assembleFromBody(text)
    if (content.isBlank()) {
        throw McpProtocolException("LLM returned empty content")
    }
    val responseModel = parsed?.get("model")?.jsonPrimitive?.contentOrNull ?: model
    val finish = LlmTextAssembler.extractFinishReason(text, emptyList())
    return McpCreateMessageResult(
        role = "assistant",
        content = McpContent(type = "text", text = content),
        model = responseModel,
        stopReason = mapFinishReason(finish),
    )
}

internal fun mapFinishReason(finish: String?): String = when (finish) {
    null, "stop" -> "endTurn"
    "length" -> "maxTokens"
    "content_filter" -> "contentFilter"
    else -> finish
}
