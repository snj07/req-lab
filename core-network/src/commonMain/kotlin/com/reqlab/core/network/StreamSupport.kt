package com.reqlab.core.network

import com.reqlab.core.model.RequestDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val streamJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun isSseContentType(contentType: String?): Boolean {
    val ct = contentType?.lowercase().orEmpty()
    return ct.contains("text/event-stream")
}

fun isNdjsonContentType(contentType: String?): Boolean {
    val ct = contentType?.lowercase().orEmpty()
    return ct.contains("ndjson") || ct.contains("x-ndjson")
}

fun isStreamingContentType(contentType: String?): Boolean =
    isSseContentType(contentType) || isNdjsonContentType(contentType)

fun requestLooksLikeStreaming(request: RequestDefinition): Boolean {
    val acceptStreaming = request.headers.any {
        it.enabled && it.key.equals("Accept", ignoreCase = true) &&
            (it.value.contains("text/event-stream", ignoreCase = true) ||
                it.value.contains("ndjson", ignoreCase = true))
    }
    val body = request.body.content.orEmpty()
    val streamTrue = Regex(""""stream"\s*:\s*true""").containsMatchIn(body)
    return acceptStreaming || streamTrue
}

data class SseEvent(
    val data: String,
    val eventType: String = "message",
    val isDone: Boolean = false,
)

/**
 * Incremental SSE parser. Feed one line at a time (without requiring the caller
 * to strip CR). A blank line dispatches the current event.
 *
 * Handles:
 *  - `:` comment / keep-alive lines
 *  - optional single leading space after `data:` / `event:`
 *  - multiple `data:` lines joined by `\n`
 *  - OpenAI `[DONE]` sentinel
 *  - CRLF and LF
 */
class SseParser {
    private var eventType: String = ""
    private val data = StringBuilder()

    fun feedLine(line: String): SseEvent? {
        val normalized = if (line.endsWith('\r')) line.dropLast(1) else line
        when {
            normalized.startsWith(":") -> return null
            normalized.startsWith("event:") -> {
                eventType = stripOneLeadingSpace(normalized.removePrefix("event:"))
                return null
            }
            normalized.startsWith("data:") -> {
                val payload = stripOneLeadingSpace(normalized.removePrefix("data:"))
                if (data.isNotEmpty()) data.append('\n')
                data.append(payload)
                return null
            }
            normalized.isBlank() && data.isNotEmpty() -> return dispatch()
            else -> return null
        }
    }

    fun flush(): SseEvent? = if (data.isNotEmpty()) dispatch() else null

    private fun dispatch(): SseEvent {
        val payload = data.toString()
        val type = eventType.ifEmpty { "message" }
        data.clear()
        eventType = ""
        return SseEvent(
            data = payload,
            eventType = type,
            isDone = payload.trim() == "[DONE]",
        )
    }

    private fun stripOneLeadingSpace(value: String): String =
        if (value.startsWith(' ')) value.drop(1) else value
}

object LlmTextAssembler {
    fun assemble(events: List<String>): String {
        if (events.isEmpty()) return ""
        val sb = StringBuilder()
        for (raw in events) {
            val piece = extractDeltaContent(raw)
            if (!piece.isNullOrEmpty()) sb.append(piece)
        }
        if (sb.isNotEmpty()) return sb.toString()
        return extractFullMessage(events.last()) ?: ""
    }

    fun assembleFromBody(bodyText: String, streamEvents: List<String> = emptyList()): String {
        if (streamEvents.isNotEmpty()) {
            val fromEvents = assemble(streamEvents)
            if (fromEvents.isNotEmpty()) return fromEvents
        }
        return extractFullMessage(bodyText) ?: ""
    }

    fun extractFinishReason(bodyText: String?, streamEvents: List<String>): String? {
        for (raw in streamEvents.asReversed()) {
            extractFinishReasonFromJson(raw)?.let { return it }
        }
        return extractFinishReasonFromJson(bodyText)
    }

    private fun extractDeltaContent(raw: String): String? {
        val obj = parseObject(raw) ?: return null
        obj.array("choices")?.firstObject()?.obj("delta")?.string("content")?.let { return it }
        obj.obj("message")?.string("content")?.let { return it }
        obj.string("response")?.let { return it }
        return null
    }

    private fun extractFullMessage(raw: String?): String? {
        val obj = parseObject(raw) ?: return null
        obj.array("choices")?.firstObject()?.obj("message")?.string("content")?.let { return it }
        obj.obj("message")?.string("content")?.let { return it }
        return null
    }

    private fun extractFinishReasonFromJson(raw: String?): String? {
        val obj = parseObject(raw) ?: return null
        return obj.array("choices")?.firstObject()?.string("finish_reason")
    }

    fun isNdjsonDone(raw: String): Boolean {
        val obj = parseObject(raw) ?: return false
        return obj["done"]?.jsonPrimitive?.booleanOrNull == true
    }

    private fun parseObject(raw: String?): JsonObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { streamJson.parseToJsonElement(raw).jsonObject }.getOrNull()
    }
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonArray.firstObject(): JsonObject? = firstOrNull() as? JsonObject
