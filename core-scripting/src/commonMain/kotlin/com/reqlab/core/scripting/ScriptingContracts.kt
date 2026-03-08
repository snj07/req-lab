package com.reqlab.core.scripting

import kotlinx.serialization.Serializable

@Serializable
data class ScriptResult(
    val success: Boolean,
    val logs: List<String> = emptyList(),
    val assertions: List<AssertionResult> = emptyList(),
    val error: String? = null,
    /** Variables set by pm.environment.set() during script execution. */
    val newVariables: Map<String, String> = emptyMap(),
)

@Serializable
data class AssertionResult(
    val name: String,
    val passed: Boolean,
    val message: String? = null
)

interface ScriptEngine {
    suspend fun executePreRequestScript(script: String, context: ScriptContext): ScriptResult
    suspend fun executeTestScript(script: String, context: ScriptContext): ScriptResult
}

@Serializable
data class ScriptContext(
    val url: String,
    val method: String,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    val variables: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseTimeMs: Long? = null,
)
