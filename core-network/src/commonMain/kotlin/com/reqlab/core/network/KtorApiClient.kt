package com.reqlab.core.network

import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.model.ResponseDefinition
import com.reqlab.core.model.ResponseMetrics
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.encodeURLPath
import io.ktor.http.formUrlEncode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

class KtorApiClient(
    private val httpClient: HttpClient = defaultHttpClient(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val interceptors: List<NetworkInterceptor> = emptyList(),
    private val logger: NetworkLogger = NoOpNetworkLogger,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
) : ApiClient {

    override fun execute(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>>
    ): Flow<NetworkEvent> = flow {
        emit(NetworkEvent.Started(request.id, currentTimeMillis()))

        var attempt = 0
        var lastThrowable: Throwable? = null

        while (attempt < retryPolicy.maxAttempts) {
            attempt++
            val startTime = currentTimeMillis()

            try {
                val resolvedUrl = VariableResolver.resolve(request.url, variableLayers)
                if (resolvedUrl.startsWith("ws://") || resolvedUrl.startsWith("wss://")) {
                    val wsResponse = executeWebSocketRequest(request, resolvedUrl, startTime)
                    emit(NetworkEvent.Success(wsResponse))
                    return@flow
                }

                val preparedRequest = buildRequest(request, variableLayers)
                interceptors.forEach { interceptor -> interceptor.onRequest(preparedRequest) }

                val response = httpClient.request(preparedRequest)
                val headersReceivedTime = currentTimeMillis()
                val serverMs = headersReceivedTime - startTime
                val duration = currentTimeMillis() - startTime
                interceptors.forEach { interceptor -> interceptor.onResponse(response, duration) }

                val mappedResponse = response.toResponseDefinition(
                    request.id, duration,
                    serverMs = serverMs,
                    requestStartTime = startTime,
                )
                val shouldRetry = mappedResponse.statusCode in retryPolicy.retryOnStatusCodes

                if (!shouldRetry || attempt == retryPolicy.maxAttempts) {
                    emit(NetworkEvent.Success(mappedResponse))
                    return@flow
                }

                val delayMs = retryPolicy.delayForAttempt(attempt)
                emit(NetworkEvent.RetryScheduled(attempt, delayMs, "status=${mappedResponse.statusCode}"))
                delay(delayMs)
            } catch (throwable: Throwable) {
                lastThrowable = throwable
                logger.error("Request failed at attempt $attempt", throwable)
                interceptors.forEach { interceptor -> interceptor.onFailure(throwable, attempt) }

                if (attempt == retryPolicy.maxAttempts) {
                    break
                }

                val delayMs = retryPolicy.delayForAttempt(attempt)
                emit(NetworkEvent.RetryScheduled(attempt, delayMs, throwable.message ?: "unknown error"))
                delay(delayMs)
            }
        }

        emit(
            NetworkEvent.Failure(
                NetworkError(
                    requestId = request.id,
                    message = lastThrowable?.message ?: "Request failed",
                    cause = lastThrowable,
                    isRetryExhausted = true
                )
            )
        )
    }

    private fun buildRequest(
        request: RequestDefinition,
        variableLayers: List<Map<String, String>>
    ): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        val resolvedUrl = VariableResolver.resolve(request.url, variableLayers)

        builder.method = request.method.toKtorMethod()
        builder.url(resolvedUrl)

        request.queryParams.filter { it.enabled }.forEach { queryParam ->
            builder.url.parameters.append(
                queryParam.key,
                VariableResolver.resolve(queryParam.value, variableLayers)
            )
        }

        request.headers.filter { it.enabled }.forEach { header ->
            builder.header(header.key, VariableResolver.resolve(header.value, variableLayers))
        }

        if (request.cookies.isNotEmpty()) {
            builder.header(HttpHeaders.Cookie, request.cookies.filter { it.enabled }
                .joinToString(separator = "; ") { cookie ->
                    "${cookie.key}=${VariableResolver.resolve(cookie.value, variableLayers)}"
                })
        }

        applyAuth(builder, request, variableLayers)
        applyBody(builder, request, variableLayers)

        return builder
    }

    private fun applyAuth(
        builder: HttpRequestBuilder,
        request: RequestDefinition,
        variableLayers: List<Map<String, String>>
    ) {
        val auth = request.auth
        when (auth.type) {
            AuthType.NONE -> Unit
            AuthType.BASIC -> {
                val username = VariableResolver.resolve(auth.params["username"].orEmpty(), variableLayers)
                val password = VariableResolver.resolve(auth.params["password"].orEmpty(), variableLayers)
                val value = "$username:$password".encodeToByteArray().encodeBase64()
                builder.header(HttpHeaders.Authorization, "Basic $value")
            }

            AuthType.BEARER,
            AuthType.JWT -> {
                val token = VariableResolver.resolve(auth.params["token"].orEmpty(), variableLayers)
                if (token.isNotBlank()) {
                    builder.header(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            AuthType.API_KEY -> {
                val key = auth.params["key"].orEmpty()
                val value = VariableResolver.resolve(auth.params["value"].orEmpty(), variableLayers)
                val placement = auth.params["placement"]?.lowercase() ?: "header"
                if (placement == "query") {
                    builder.url.parameters.append(key, value)
                } else {
                    builder.header(key, value)
                }
            }

            AuthType.OAUTH2 -> {
                val accessToken = VariableResolver.resolve(auth.params["accessToken"].orEmpty(), variableLayers)
                if (accessToken.isNotBlank()) {
                    builder.header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }
        }
    }

    private suspend fun executeWebSocketRequest(
        request: RequestDefinition,
        resolvedUrl: String,
        requestStartTime: Long,
    ): ResponseDefinition {
        val session = httpClient.webSocketSession {
            url(resolvedUrl)
        }

        val transcript = mutableListOf<String>()
        withTimeout(5_000) {
            val firstFrame = session.incoming.receiveCatching().getOrNull()
            (firstFrame as? Frame.Text)?.readText()?.let { transcript += it }
            session.send(Frame.Text("hello"))
            val secondFrame = session.incoming.receiveCatching().getOrNull()
            (secondFrame as? Frame.Text)?.readText()?.let { transcript += it }
        }
        runCatching { session.outgoing.close() }

        val endTime = currentTimeMillis()
        val body = transcript.joinToString("\n")
        val bodySize = body.encodeToByteArray().size.toLong()

        return ResponseDefinition(
            requestId = request.id,
            statusCode = 101,
            statusText = "Switching Protocols",
            headers = emptyList(),
            cookies = emptyList(),
            bodyText = body,
            contentType = "text/plain",
            executedAtEpochMillis = endTime,
            metrics = ResponseMetrics(
                statusCode = 101,
                responseTimeMs = endTime - requestStartTime,
                responseSizeBytes = bodySize,
                serverMs = endTime - requestStartTime,
                downloadMs = 0,
            )
        )
    }

    private fun applyRawBody(
        builder: HttpRequestBuilder,
        contentType: ContentType,
        rawContent: String?,
        variableLayers: List<Map<String, String>>,
    ) {
        builder.contentType(contentType)
        builder.setBody(VariableResolver.resolve(rawContent.orEmpty(), variableLayers))
    }

    private fun applyBody(
        builder: HttpRequestBuilder,
        request: RequestDefinition,
        variableLayers: List<Map<String, String>>
    ) {
        val body = request.body
        when (body.type) {
            BodyType.NONE -> Unit
            BodyType.JSON -> applyRawBody(builder, ContentType.Application.Json, body.content, variableLayers)
            BodyType.RAW_TEXT -> applyRawBody(builder, ContentType.Text.Plain, body.content, variableLayers)
            BodyType.XML -> applyRawBody(builder, ContentType.Application.Xml, body.content, variableLayers)
            BodyType.HTML -> applyRawBody(builder, ContentType.Text.Html, body.content, variableLayers)
            BodyType.JAVASCRIPT -> applyRawBody(builder, ContentType.parse("application/javascript"), body.content, variableLayers)

            BodyType.GRAPHQL -> {
                builder.contentType(ContentType.Application.Json)
                val graphQlBody = body.graphQl
                val query = VariableResolver.resolve(graphQlBody?.query.orEmpty(), variableLayers)
                val operationName = graphQlBody?.operationName
                val variables = graphQlBody?.variablesJson
                val payload = buildString {
                    append("{\"query\":")
                    append(json.encodeToString(String.serializer(), query))
                    if (!operationName.isNullOrBlank()) {
                        append(",\"operationName\":")
                        append(json.encodeToString(String.serializer(), operationName))
                    }
                    if (!variables.isNullOrBlank()) {
                        append(",\"variables\":")
                        append(variables)
                    }
                    append("}")
                }
                builder.setBody(payload)
            }

            BodyType.X_WWW_FORM_URLENCODED -> {
                val parameters = Parameters.build {
                    body.formEntries.filter { it.enabled }.forEach { entry ->
                        append(entry.key, VariableResolver.resolve(entry.value, variableLayers))
                    }
                }
                builder.contentType(ContentType.Application.FormUrlEncoded)
                builder.setBody(parameters.formUrlEncode())
            }

            BodyType.FORM_DATA -> {
                // Prefer the typed formDataEntries (new structured rows) over the legacy formEntries.
                val entries = if (body.formDataEntries.isNotEmpty()) {
                    body.formDataEntries.filter { it.enabled }.map { e ->
                        KeyValueEntry(e.key, VariableResolver.resolve(e.value, variableLayers))
                    }
                } else {
                    body.formEntries.filter { it.enabled }.map { e ->
                        e.copy(value = VariableResolver.resolve(e.value, variableLayers))
                    }
                }
                val multipart = MultiPartFormDataContent(
                    formData {
                        entries.forEach { entry -> append(entry.key, entry.value) }
                    }
                )
                builder.setBody(multipart)
            }

            BodyType.BINARY -> {
                val payloadBytes = body.binaryBytesBase64
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { decodeBase64ToByteArray(it) }
                    ?: body.content.orEmpty().encodeToByteArray()
                body.binaryName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { builder.header("X-ReqLab-Filename", it) }
                builder.setBody(payloadBytes)
            }
        }
    }
}

private fun HttpMethodType.toKtorMethod(): HttpMethod = when (this) {
    HttpMethodType.GET -> HttpMethod.Get
    HttpMethodType.POST -> HttpMethod.Post
    HttpMethodType.PUT -> HttpMethod.Put
    HttpMethodType.PATCH -> HttpMethod.Patch
    HttpMethodType.DELETE -> HttpMethod.Delete
    HttpMethodType.OPTIONS -> HttpMethod.Options
    HttpMethodType.HEAD -> HttpMethod.Head
}

private suspend fun HttpResponse.toResponseDefinition(
    requestId: String,
    responseTimeMs: Long,
    serverMs: Long = -1,
    requestStartTime: Long = -1,
): ResponseDefinition {
    val bodyReadStart = currentTimeMillis()
    val bodyText = runCatching { bodyAsText() }.getOrElse { "" }
    val bodyReadEnd = currentTimeMillis()
    val downloadMs = bodyReadEnd - bodyReadStart
    val responseHeaders = headers.entries().flatMap { (name, values) ->
        values.map { value -> KeyValueEntry(name, value) }
    }
    val cookies = responseHeaders.filter { it.key.equals(HttpHeaders.SetCookie, ignoreCase = true) }
    val sizeFromHeader = headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
    val bodyBytes = bodyText.encodeToByteArray().size.toLong()
    val responseSize = if (sizeFromHeader > 0) sizeFromHeader else bodyBytes

    return ResponseDefinition(
        requestId = requestId,
        statusCode = status.value,
        statusText = status.description,
        headers = responseHeaders,
        cookies = cookies,
        bodyText = bodyText,
        contentType = contentType()?.toString(),
        executedAtEpochMillis = currentTimeMillis(),
        metrics = ResponseMetrics(
            statusCode = status.value,
            responseTimeMs = responseTimeMs,
            responseSizeBytes = responseSize,
            serverMs = serverMs,
            downloadMs = downloadMs,
        )
    )
}

private fun defaultHttpClient(): HttpClient = createPlatformHttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
            explicitNulls = false
        })
    }
    install(WebSockets)
    expectSuccess = false
}

private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

private val base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

private fun ByteArray.encodeBase64(): String {
    if (isEmpty()) return ""
    val result = StringBuilder((size + 2) / 3 * 4)
    var index = 0
    while (index < size) {
        val b0 = this[index++].toInt() and 0xFF
        val b1 = if (index < size) this[index++].toInt() and 0xFF else -1
        val b2 = if (index < size) this[index++].toInt() and 0xFF else -1

        result.append(base64Alphabet[b0 ushr 2])
        result.append(base64Alphabet[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])

        if (b1 >= 0) {
            result.append(base64Alphabet[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)])
        } else {
            result.append('=')
        }

        if (b2 >= 0) {
            result.append(base64Alphabet[b2 and 0x3F])
        } else {
            result.append('=')
        }
    }
    return result.toString()
}

private fun decodeBase64ToByteArray(input: String): ByteArray {
    val cleaned = input.filterNot { it.isWhitespace() }
    if (cleaned.isEmpty()) return ByteArray(0)

    val output = ArrayList<Byte>((cleaned.length * 3) / 4)
    var index = 0

    fun decodeChar(c: Char): Int = when (c) {
        in 'A'..'Z' -> c.code - 'A'.code
        in 'a'..'z' -> c.code - 'a'.code + 26
        in '0'..'9' -> c.code - '0'.code + 52
        '+' -> 62
        '/' -> 63
        '=' -> -2
        else -> -1
    }

    while (index < cleaned.length) {
        val c0 = decodeChar(cleaned[index++])
        val c1 = if (index < cleaned.length) decodeChar(cleaned[index++]) else -2
        val c2 = if (index < cleaned.length) decodeChar(cleaned[index++]) else -2
        val c3 = if (index < cleaned.length) decodeChar(cleaned[index++]) else -2

        if (c0 < 0 || c1 < 0 || c2 == -1 || c3 == -1) continue

        val b0 = (c0 shl 2) or (c1 ushr 4)
        output.add((b0 and 0xFF).toByte())

        if (c2 >= 0) {
            val b1 = ((c1 and 0x0F) shl 4) or (c2 ushr 2)
            output.add((b1 and 0xFF).toByte())
        }

        if (c3 >= 0 && c2 >= 0) {
            val b2 = ((c2 and 0x03) shl 6) or c3
            output.add((b2 and 0xFF).toByte())
        }
    }

    return output.toByteArray()
}
