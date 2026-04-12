package com.reqlab.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HttpMethodType {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    OPTIONS,
    HEAD
}

@Serializable
enum class BodyType {
    NONE,
    JSON,
    XML,           // raw XML body – syntax-highlighted, sent as application/xml
    HTML,          // raw HTML body – syntax-highlighted, sent as text/html
    JAVASCRIPT,    // raw JS body – syntax-highlighted, sent as application/javascript
    FORM_DATA,
    X_WWW_FORM_URLENCODED,
    RAW_TEXT,
    BINARY,
    GRAPHQL
}

/** Entry type for a form-data row (text value or file attachment). */
@Serializable
enum class FormEntryType { TEXT, FILE }

@Serializable
enum class AuthType {
    NONE,
    BASIC,
    BEARER,
    API_KEY,
    OAUTH2,
    JWT
}

@Serializable
data class KeyValueEntry(
    val key: String,
    val value: String,
    val enabled: Boolean = true,
    val secret: Boolean = false
)

/**
 * Extended entry for structured form-data rows.
 * Carries a [type] (text vs file) and an optional [description].
 */
@Serializable
data class FormDataEntry(
    val key: String,
    val type: FormEntryType = FormEntryType.TEXT,
    val value: String,
    val description: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class AuthConfig(
    val type: AuthType = AuthType.NONE,
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class GraphQlBody(
    val query: String = "",
    val operationName: String? = null,
    val variablesJson: String? = null
)

@Serializable
data class RequestBody(
    val type: BodyType = BodyType.NONE,
    val content: String? = null,
    val formEntries: List<KeyValueEntry> = emptyList(),
    val formDataEntries: List<FormDataEntry> = emptyList(),
    val binaryName: String? = null,
    val binaryBytesBase64: String? = null,
    val graphQl: GraphQlBody? = null
)

@Serializable
data class RequestDefinition(
    val id: String,
    val name: String,
    val method: HttpMethodType,
    val url: String,
    val queryParams: List<KeyValueEntry> = emptyList(),
    val headers: List<KeyValueEntry> = emptyList(),
    val cookies: List<KeyValueEntry> = emptyList(),
    val auth: AuthConfig = AuthConfig(),
    val body: RequestBody = RequestBody(),
    val tags: List<String> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
data class ResponseMetrics(
    val statusCode: Int,
    val responseTimeMs: Long,
    val responseSizeBytes: Long,
    // Phase-level timing breakdown (all in milliseconds, -1 = not available)
    val dnsMs: Long = -1,
    val connectMs: Long = -1,
    val tlsMs: Long = -1,
    val serverMs: Long = -1,
    val downloadMs: Long = -1,
)

@Serializable
data class ResponseDefinition(
    val requestId: String,
    val statusCode: Int,
    val statusText: String,
    val headers: List<KeyValueEntry>,
    val cookies: List<KeyValueEntry>,
    val bodyText: String,
    val contentType: String?,
    val executedAtEpochMillis: Long,
    val metrics: ResponseMetrics
)

@Serializable
data class HistoryEntry(
    val id: String,
    val requestId: String,
    val requestSnapshot: RequestDefinition,
    val responseSnapshot: ResponseDefinition?,
    val errorMessage: String? = null,
    val executedAtEpochMillis: Long
)

@Serializable
data class ApiKeyPlacement(
    @SerialName("in")
    val value: String = "header"
)
