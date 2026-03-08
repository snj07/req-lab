package com.reqlab.core.scripting

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ReqLabScriptEngineTest {

    private val engine = ReqLabScriptEngine()

    @Test
    fun pre_request_console_log_is_captured() = runTest {
        val result = engine.executePreRequestScript(
            script = """
                pm.environment.set("requestUser", "alice")
                console.log("Requesting token for", pm.environment.get("requestUser"))
            """.trimIndent(),
            context = ScriptContext(
                url = "http://localhost:8080/api/token",
                method = "POST",
                variables = mapOf("baseUrl" to "http://localhost:8080"),
            ),
        )

        assertTrue(result.success)
        assertTrue(result.logs.any { it.contains("Requesting token for alice") })
        assertEquals("alice", result.newVariables["requestUser"])
    }

    @Test
    fun test_script_console_log_is_captured() = runTest {
        val result = engine.executeTestScript(
            script = """
                pm.test("Status is 200", function() {
                    pm.expect(pm.response.code).to.equal(200)
                })
                console.log("Status:", pm.response.code)
            """.trimIndent(),
            context = ScriptContext(
                url = "http://localhost:8080/api/users",
                method = "GET",
                statusCode = 200,
                responseBody = "{\"ok\":true}",
                responseHeaders = mapOf("content-type" to "application/json"),
                responseTimeMs = 12,
            ),
        )

        assertTrue(result.success)
        assertTrue(result.logs.any { it.contains("Status: 200") })
        assertTrue(result.assertions.isNotEmpty())
        assertTrue(result.assertions.all { it.passed })
    }
}
