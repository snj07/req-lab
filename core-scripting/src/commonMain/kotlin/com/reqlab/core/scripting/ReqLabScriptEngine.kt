package com.reqlab.core.scripting

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Real JavaScript scripting engine for ReqLab.
 *
 * Uses a platform-specific JavaScript runtime ([evaluateJs]) to execute
 * actual JavaScript code — supporting variables, loops, conditionals,
 * functions, closures, and the full `reqlab.*` Postman-compatible API.
 *
 * Architecture:
 *   1. [ScriptBootstrap.build] generates a self-contained JS IIFE that
 *      sets up the `reqlab` namespace, injects context data, executes
 *      the user script, and returns results as a JSON string.
 *   2. [evaluateJs] evaluates that IIFE on the platform's JS engine.
 *   3. This class parses the JSON result into a [ScriptResult].
 */
class ReqLabScriptEngine(
    private val evaluator: (String) -> String = ::evaluateJs,
    private val bootstrapBuilder: (String, ScriptContext, String) -> String = ScriptBootstrap::build,
) : ScriptEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun executePreRequestScript(
        script: String,
        context: ScriptContext,
        prefix: String,
    ): ScriptResult = execute(script, context, prefix)

    override suspend fun executeTestScript(
        script: String,
        context: ScriptContext,
        prefix: String,
    ): ScriptResult = execute(script, context, prefix)

    private fun execute(
        script: String,
        context: ScriptContext,
        prefix: String,
    ): ScriptResult {
        if (script.isBlank()) {
            return ScriptResult(success = true)
        }
        return try {
            val jsProgram = bootstrapBuilder(script, context, prefix)
            val resultJson = evaluator(jsProgram)
            parseResult(resultJson)
        } catch (e: Exception) {
            ScriptResult(
                success = false,
                error = "Script execution failed: ${e.message}",
            )
        }
    }

    // ── JSON result parsing ───────────────────────────────────────────────

    private fun parseResult(resultJson: String): ScriptResult {
        val root = json.parseToJsonElement(resultJson).jsonObject

        val error = root["error"]?.jsonPrimitive?.contentOrNull

        val tests = root["tests"]?.jsonArray?.map { el ->
            val obj = el.jsonObject
            AssertionResult(
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                passed = obj["passed"]?.jsonPrimitive?.booleanOrNull ?: false,
                message = obj["message"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()

        val logs = root["logs"]?.jsonArray?.map { el ->
            el.jsonPrimitive.contentOrNull ?: ""
        } ?: emptyList()

        val envVars = jsonObjectToMap(root["envVars"])
        val globalVars = jsonObjectToMap(root["globalVars"])
        val collVars = jsonObjectToMap(root["collVars"])
        val reqVars = jsonObjectToMap(root["reqVars"])

        val mutObj = root["mutations"]?.jsonObject
        val mutations = ScriptRequestMutations(
            url = mutObj?.get("url")?.jsonPrimitive?.contentOrNull,
            method = mutObj?.get("method")?.jsonPrimitive?.contentOrNull,
            body = mutObj?.get("body")?.jsonPrimitive?.contentOrNull,
            headers = jsonObjectToMap(mutObj?.get("headers")),
            queryParams = jsonObjectToMap(mutObj?.get("queryParams")),
        )

        return ScriptResult(
            success = error == null && tests.all { it.passed },
            logs = logs,
            assertions = tests,
            error = error,
            newVariables = envVars,
            newGlobalVariables = globalVars,
            newCollectionVariables = collVars,
            newRequestVariables = reqVars,
            requestMutations = mutations,
        )
    }

    private fun jsonObjectToMap(element: kotlinx.serialization.json.JsonElement?): Map<String, String> {
        if (element == null || element !is JsonObject) return emptyMap()
        return element.entries.associate { (k, v) ->
            k to (v.jsonPrimitive.contentOrNull ?: "")
        }
    }
}
