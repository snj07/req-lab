package com.reqlab.core.scripting

import kotlinx.serialization.Serializable

@Serializable
data class ScriptResult(
    val success: Boolean,
    val logs: List<String> = emptyList(),
    val assertions: List<AssertionResult> = emptyList(),
    val error: String? = null,
    val newVariables: Map<String, String> = emptyMap(),
    val newGlobalVariables: Map<String, String> = emptyMap(),
    val newCollectionVariables: Map<String, String> = emptyMap(),
    val newRequestVariables: Map<String, String> = emptyMap(),
    val requestMutations: ScriptRequestMutations = ScriptRequestMutations(),
    val executionSetNextRequestCalled: Boolean = false,
    val executionNextRequest: String? = null,
    val executionSkipRequest: Boolean = false,
)

/**
 * Specification for a sub-HTTP request queued by [reqlab.sendRequest()] inside a script.
 * The Kotlin engine processes these after the main script phase completes.
 */
@Serializable
data class SendRequestSpec(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    /** Serialized JS callback source — captured via `callback.toString()` in the IIFE. */
    val callbackSource: String? = null,
)

/**
 * HTTP response returned to the engine after a [SendRequestSpec] sub-request is executed.
 */
@Serializable
data class SendRequestResult(
    val statusCode: Int,
    val statusText: String = "",
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
    val elapsedMs: Long = 0,
)

@Serializable
data class ScriptRequestMutations(
    val url: String? = null,
    val method: String? = null,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
)

@Serializable
data class AssertionResult(
    val name: String,
    val passed: Boolean,
    val message: String? = null,
)

/**
 * Script execution engine.
 *
 * Both execute methods accept a [prefix] controlling the namespace used in user scripts.
 * Default prefix is "reqlab":
 *   reqlab.test(...)  reqlab.expect(...)  reqlab.environment.set(...)
 *   reqlab.response.code  reqlab.globals.get("key")  reqlab.request.headers.add(...)
 *
 * Can be changed in Settings, e.g. to "api":
 *   api.test(...)     api.expect(...)     api.environment.set(...)
 */
interface ScriptEngine {
    suspend fun executePreRequestScript(
        script: String,
        context: ScriptContext,
        prefix: String = "reqlab",
    ): ScriptResult

    suspend fun executeTestScript(
        script: String,
        context: ScriptContext,
        prefix: String = "reqlab",
    ): ScriptResult
}

@Serializable
data class ScriptContext(
    val url: String,
    val method: String,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    val responseSizeBytes: Long? = null,
    val variables: Map<String, String> = emptyMap(),
    val globalVariables: Map<String, String> = emptyMap(),
    val collectionVariables: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseTimeMs: Long? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestQueryParams: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val streamEvents: List<String> = emptyList(),
    val assembledText: String? = null,
)
