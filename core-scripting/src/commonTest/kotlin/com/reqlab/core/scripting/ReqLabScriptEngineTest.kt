package com.reqlab.core.scripting

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for [ReqLabScriptEngine] with real JavaScript execution.
 *
 * These tests validate that:
 *   - The JavaScript runtime executes actual JS code (variables, loops, functions)
 *   - The `reqlab.*` namespace API works correctly
 *   - All Chai-like assertions produce correct results
 *   - Response/request objects expose the right data
 *   - Variable scopes (environment, globals, collection) work
 *   - Request mutations operate correctly
 *   - Console logging captures output
 *   - Configurable prefix is supported
 *   - Global (no-prefix) backward compatibility works
 *   - Error handling is robust
 */
class ReqLabScriptEngineTest {

    private val engine = ReqLabScriptEngine()

    private fun ctx(
        status: Int = 200,
        body: String = """{"ok":true,"id":42,"name":"Alice","scores":[10,20,30]}""",
        responseHeaders: Map<String, String> = mapOf("Content-Type" to "application/json"),
        responseTimeMs: Long = 150,
        responseSizeBytes: Long = 512,
        variables: Map<String, String> = emptyMap(),
        globalVariables: Map<String, String> = emptyMap(),
        collectionVariables: Map<String, String> = emptyMap(),
        requestHeaders: Map<String, String> = emptyMap(),
        requestQueryParams: Map<String, String> = emptyMap(),
        requestBody: String? = null,
    ) = ScriptContext(
        url                = "https://api.example.com/users",
        method             = "GET",
        statusCode         = status,
        responseBody       = body,
        responseSizeBytes  = responseSizeBytes,
        responseHeaders    = responseHeaders,
        responseTimeMs     = responseTimeMs,
        variables          = variables,
        globalVariables    = globalVariables,
        collectionVariables = collectionVariables,
        requestHeaders     = requestHeaders,
        requestQueryParams = requestQueryParams,
        requestBody        = requestBody,
    )

    // ── Real JavaScript execution ─────────────────────────────────────────

    @Test
    fun realJs_variables_and_loops() = runTest {
        val r = engine.executeTestScript("""
            var scores = reqlab.response.json().scores
            for (var i = 0; i < scores.length; i++) {
                reqlab.test("score " + scores[i] + " > 0", function() {
                    reqlab.expect(scores[i]).to.be.above(0)
                })
            }
        """.trimIndent(), ctx())
        assertTrue(r.success, "Expected success but got error: ${r.error}")
        assertEquals(3, r.assertions.size)
        assertTrue(r.assertions.all { it.passed })
    }

    @Test
    fun realJs_conditionals() = runTest {
        val r = engine.executeTestScript("""
            var code = reqlab.response.code
            if (code === 200) {
                reqlab.test("success path", function() {
                    reqlab.expect(code).to.equal(200)
                })
            } else {
                reqlab.test("error path", function() {
                    reqlab.expect(code).to.not.equal(200)
                })
            }
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals(1, r.assertions.size)
        assertEquals("success path", r.assertions[0].name)
    }

    @Test
    fun realJs_functions() = runTest {
        val r = engine.executeTestScript("""
            function checkField(obj, field) {
                reqlab.test(field + " exists", function() {
                    reqlab.expect(obj[field]).to.exist
                })
            }
            var data = reqlab.response.json()
            checkField(data, "ok")
            checkField(data, "id")
            checkField(data, "name")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals(3, r.assertions.size)
    }

    @Test
    fun realJs_array_forEach() = runTest {
        val r = engine.executeTestScript("""
            var items = reqlab.response.json().scores
            items.forEach(function(score) {
                reqlab.test("score " + score + " is positive", function() {
                    reqlab.expect(score).to.be.above(0)
                })
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals(3, r.assertions.size)
    }

    @Test
    fun realJs_closures() = runTest {
        val r = engine.executeTestScript("""
            var createChecker = function(expected) {
                return function(actual) {
                    reqlab.expect(actual).to.equal(expected)
                }
            }
            var check200 = createChecker(200)
            reqlab.test("closure check", function() {
                check200(reqlab.response.code)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun realJs_string_operations() = runTest {
        val r = engine.executeTestScript("""
            var body = reqlab.response.text()
            var upper = body.toUpperCase()
            reqlab.test("text operations work", function() {
                reqlab.expect(upper).to.include("ALICE")
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun realJs_json_path_access() = runTest {
        val r = engine.executeTestScript("""
            var data = reqlab.response.json()
            reqlab.test("nested access", function() {
                reqlab.expect(data.name).to.equal("Alice")
                reqlab.expect(data.scores[0]).to.equal(10)
                reqlab.expect(data.scores.length).to.equal(3)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    // ── Assertion types ───────────────────────────────────────────────────

    @Test
    fun assertions_equal() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("equal", function() {
                reqlab.expect(reqlab.response.code).to.equal(200)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun assertions_not_equal() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("not equal", function() {
                reqlab.expect(reqlab.response.code).to.not.equal(404)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun assertions_above_below() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("above", function() {
                reqlab.expect(reqlab.response.code).to.be.above(100)
            })
            reqlab.test("below", function() {
                reqlab.expect(reqlab.response.code).to.be.below(500)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals(2, r.assertions.size)
    }

    @Test
    fun assertions_at_least_at_most() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("at least", function() {
                reqlab.expect(reqlab.response.code).to.be.at.least(200)
            })
            reqlab.test("at most", function() {
                reqlab.expect(reqlab.response.code).to.be.at.most(200)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun assertions_include() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("include", function() {
                reqlab.expect(reqlab.response.text()).to.include("Alice")
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun assertions_match_regex() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("match", function() {
                reqlab.expect(reqlab.response.text()).to.match(/Alice/)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun assertions_oneOf() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("oneOf", function() {
                reqlab.expect(reqlab.response.code).to.be.oneOf([200, 201, 204])
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun assertions_exist() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("exist", function() {
                reqlab.expect(reqlab.response.json().name).to.exist
            })
            reqlab.test("not exist", function() {
                reqlab.expect(reqlab.response.json().missing).to.not.exist
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun assertions_ok_empty_null() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("ok", function() {
                reqlab.expect(reqlab.response.json().ok).to.be.ok
            })
            reqlab.test("empty", function() {
                reqlab.expect("").to.be.empty
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun assertion_failure_captured() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("should fail", function() {
                reqlab.expect(reqlab.response.code).to.equal(404)
            })
        """.trimIndent(), ctx(status = 200))
        assertFalse(r.success)
        assertEquals(1, r.assertions.size)
        assertFalse(r.assertions[0].passed)
        assertNotNull(r.assertions[0].message)
    }

    // ── Response object ───────────────────────────────────────────────────

    @Test
    fun response_code_and_status() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("code", function() {
                reqlab.expect(reqlab.response.code).to.equal(200)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun response_time_and_size() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("time", function() {
                reqlab.expect(reqlab.response.responseTime).to.equal(150)
            })
            reqlab.test("size", function() {
                reqlab.expect(reqlab.response.size).to.equal(512)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun response_headers() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("header get", function() {
                reqlab.expect(reqlab.response.headers.get("Content-Type")).to.equal("application/json")
            })
            reqlab.test("header bracket", function() {
                reqlab.expect(reqlab.response.headers["content-type"]).to.equal("application/json")
            })
        """.trimIndent(), ctx())
        assertTrue(r.success, "Failed: ${r.assertions.map { "${it.name}: ${it.passed} ${it.message}" }}")
    }

    @Test
    fun response_text_and_json() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("text", function() {
                reqlab.expect(reqlab.response.text()).to.include("Alice")
            })
            reqlab.test("json", function() {
                reqlab.expect(reqlab.response.json().id).to.equal(42)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    // ── Variable scopes ───────────────────────────────────────────────────

    @Test
    fun environment_set_get() = runTest {
        val r = engine.executePreRequestScript("""
            reqlab.environment.set("token", "abc123")
            reqlab.console.log("set token")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("abc123", r.newVariables["token"])
        assertTrue(r.logs.any { it.contains("set token") })
    }

    @Test
    fun environment_get_existing() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("env get", function() {
                reqlab.expect(reqlab.environment.get("host")).to.equal("localhost")
            })
        """.trimIndent(), ctx(variables = mapOf("host" to "localhost")))
        assertTrue(r.success)
    }

    @Test
    fun globals_set() = runTest {
        val r = engine.executePreRequestScript("""
            reqlab.globals.set("apiKey", "key123")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("key123", r.newGlobalVariables["apiKey"])
    }

    @Test
    fun collection_variables_set() = runTest {
        val r = engine.executePreRequestScript("""
            reqlab.collectionVariables.set("baseUrl", "https://api.test.com")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("https://api.test.com", r.newCollectionVariables["baseUrl"])
    }

    @Test
    fun request_variables_set() = runTest {
        val r = engine.executePreRequestScript("""
            reqlab.variables.set("requestOnly", "local-1")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("local-1", r.newRequestVariables["requestOnly"])
    }

    // ── Request mutations ─────────────────────────────────────────────────

    @Test
    fun request_set_header() = runTest {
        val r = engine.executePreRequestScript("""
            reqlab.request.headers.add("X-Custom", "value1")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("value1", r.requestMutations.headers["X-Custom"])
    }

    @Test
    fun request_set_method_and_url() = runTest {
        val r = engine.executePreRequestScript("""
            request.setMethod("POST")
            request.setUrl("https://api.new.com/data")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("POST", r.requestMutations.method)
        assertEquals("https://api.new.com/data", r.requestMutations.url)
    }

    @Test
    fun request_set_query_param() = runTest {
        val r = engine.executePreRequestScript("""
            request.setQueryParam("page", "2")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("2", r.requestMutations.queryParams["page"])
    }

    @Test
    fun request_read_url_and_method() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("url", function() {
                reqlab.expect(reqlab.request.url).to.include("example.com")
            })
            reqlab.test("method", function() {
                reqlab.expect(reqlab.request.method).to.equal("GET")
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    // ── Console logging ───────────────────────────────────────────────────

    @Test
    fun console_log() = runTest {
        val r = engine.executeTestScript("""
            reqlab.console.log("hello", "world")
            console.log("direct log")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertTrue(r.logs.any { it.contains("hello world") })
        assertTrue(r.logs.any { it.contains("direct log") })
    }

    // ── Configurable prefix ───────────────────────────────────────────────

    @Test
    fun alternative_prefix() = runTest {
        val r = engine.executeTestScript("""
            api.test("with api prefix", function() {
                api.expect(api.response.code).to.equal(200)
            })
        """.trimIndent(), ctx(), prefix = "api")
        assertTrue(r.success, "Failed: ${r.error}")
        assertEquals(1, r.assertions.size)
        assertTrue(r.assertions[0].passed)
    }

    // ── Global backward compatibility ─────────────────────────────────────

    @Test
    fun global_test_expect() = runTest {
        val r = engine.executeTestScript("""
            test("global test", function() {
                expect(response.code).to.equal(200)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
    }

    @Test
    fun global_env_set() = runTest {
        val r = engine.executePreRequestScript("""
            env.set("key", "value")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("value", r.newVariables["key"])
    }

    // ── Error handling ────────────────────────────────────────────────────

    @Test
    fun blank_script() = runTest {
        val r = engine.executeTestScript("", ctx())
        assertTrue(r.success)
    }

    @Test
    fun script_syntax_error() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("will fail", function() {
                var x = ;
            })
        """.trimIndent(), ctx())
        // Should not crash; either the IIFE catches it or the engine does
        assertNotNull(r.error ?: r.assertions.firstOrNull()?.message)
    }

    @Test
    fun runtime_error_in_test() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("runtime error", function() {
                var obj = null
                reqlab.expect(obj.missing).to.exist
            })
        """.trimIndent(), ctx())
        assertFalse(r.assertions.isEmpty())
        assertFalse(r.assertions[0].passed)
    }

    // ── Complex workflow ──────────────────────────────────────────────────

    @Test
    fun full_workflow() = runTest {
        // Pre-request: set variables and headers
        val pre = engine.executePreRequestScript("""
            reqlab.environment.set("requestUser", "alice")
            reqlab.request.headers.add("X-User", reqlab.environment.get("requestUser"))
            reqlab.console.log("Pre-request done")
        """.trimIndent(), ctx())
        assertTrue(pre.success)
        assertEquals("alice", pre.newVariables["requestUser"])
        assertEquals("alice", pre.requestMutations.headers["X-User"])

        // Test: check response and extract data
        val test = engine.executeTestScript("""
            reqlab.test("status ok", function() {
                reqlab.expect(reqlab.response.code).to.equal(200)
            })
            reqlab.test("has user data", function() {
                var data = reqlab.response.json()
                reqlab.expect(data.name).to.equal("Alice")
                reqlab.expect(data.id).to.be.above(0)
            })
            reqlab.environment.set("userId", String(reqlab.response.json().id))
        """.trimIndent(), ctx())
        assertTrue(test.success)
        assertEquals(2, test.assertions.size)
        assertEquals("42", test.newVariables["userId"])
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Assertion Aliases
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun assertion_alias_eql() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("eql alias", function() {
                reqlab.expect(200).to.eql(200)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_alias_equals() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("equals alias", function() {
                reqlab.expect("hello").to.equals("hello")
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_alias_contain() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("contain alias", function() {
                reqlab.expect("hello world").to.contain("world")
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_alias_includes() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("includes alias", function() {
                reqlab.expect("abcdef").to.includes("bcd")
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_alias_contains() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("contains alias", function() {
                reqlab.expect([1,2,3]).to.contains(2)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_alias_greaterThan() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("greaterThan alias", function() {
                reqlab.expect(10).to.be.greaterThan(5)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_alias_lessThan() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("lessThan alias", function() {
                reqlab.expect(3).to.be.lessThan(10)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Getter Assertions (true, false, null, undefined)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun assertion_getter_true() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("true getter", function() {
                reqlab.expect(true).to.be.true
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_getter_false() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("false getter", function() {
                reqlab.expect(false).to.be.false
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_getter_null() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("null getter", function() {
                reqlab.expect(null).to.be.null
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_getter_undefined() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("undefined getter", function() {
                var x;
                reqlab.expect(x).to.be.undefined
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_not_true() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("not true", function() {
                reqlab.expect(false).to.not.be.true
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Property & Length Assertions
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun assertion_have_property() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("have property", function() {
                var data = reqlab.response.json()
                reqlab.expect(data).to.have.property("name")
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_not_have_property() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("not have property", function() {
                var data = reqlab.response.json()
                reqlab.expect(data).to.not.have.property("missing")
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_lengthOf() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("lengthOf", function() {
                reqlab.expect([1,2,3]).to.have.lengthOf(3)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_lengthOf_string() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("string length", function() {
                reqlab.expect("hello").to.have.lengthOf(5)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Negated Assertions
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun assertion_not_include() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("not include", function() {
                reqlab.expect("hello").to.not.include("xyz")
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_not_match() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("not match", function() {
                reqlab.expect("hello").to.not.match(/^xyz/)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_not_above() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("not above", function() {
                reqlab.expect(5).to.not.be.above(10)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_not_ok() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("not ok", function() {
                reqlab.expect(0).to.not.be.ok
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_not_empty() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("not empty", function() {
                reqlab.expect("hello").to.not.be.empty
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Empty on Various Types
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun assertion_empty_array() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("empty array", function() {
                reqlab.expect([]).to.be.empty
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_empty_object() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("empty object", function() {
                reqlab.expect({}).to.be.empty
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_empty_null() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("null is empty", function() {
                reqlab.expect(null).to.be.empty
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Include on Arrays
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun assertion_include_array() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("array includes", function() {
                reqlab.expect([10, 20, 30]).to.include(20)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_not_include_array() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("array not includes", function() {
                reqlab.expect([10, 20, 30]).to.not.include(99)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Deep Equality
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun assertion_deep_equal_object() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("deep equal", function() {
                reqlab.expect({a:1, b:2}).to.equal({a:1, b:2})
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun assertion_deep_equal_array() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("deep equal array", function() {
                reqlab.expect([1,2,3]).to.equal([1,2,3])
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Variable Scope Operations
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun environment_unset() = runTest {
        val r = engine.executePreRequestScript("""
            reqlab.environment.set("temp", "val1")
            reqlab.environment.unset("temp")
        """.trimIndent(), ctx(variables = mapOf("existing" to "keep")))
        assertTrue(r.success)
        // After unset, the var should not be in newVariables
        assertFalse(r.newVariables.containsKey("temp"))
    }

    @Test
    fun globals_has() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("globals has", function() {
                reqlab.expect(reqlab.globals.has("preSet")).to.be.true
            })
        """.trimIndent(), ctx(globalVariables = mapOf("preSet" to "value")))
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun globals_toObject() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("globals toObject", function() {
                var obj = reqlab.globals.toObject()
                reqlab.expect(typeof obj).to.equal("object")
                reqlab.expect(obj.k1).to.equal("v1")
            })
        """.trimIndent(), ctx(globalVariables = mapOf("k1" to "v1")))
        assertTrue(r.success)
        assertTrue(r.assertions.all { it.passed })
    }

    @Test
    fun collectionVariables_has_and_toObject() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("collection has", function() {
                reqlab.expect(reqlab.collectionVariables.has("cKey")).to.be.true
            })
            reqlab.test("collection toObject", function() {
                var obj = reqlab.collectionVariables.toObject()
                reqlab.expect(obj.cKey).to.equal("cVal")
            })
        """.trimIndent(), ctx(collectionVariables = mapOf("cKey" to "cVal")))
        assertTrue(r.success)
        assertTrue(r.assertions.all { it.passed })
    }

    @Test
    fun variables_get_cascade_priority() = runTest {
        // variables.get should return request-scoped first, then env, then collection, then global
        val r = engine.executeTestScript("""
            reqlab.variables.set("reqOnly", "fromReq")
            reqlab.test("request scope wins", function() {
                reqlab.expect(reqlab.variables.get("reqOnly")).to.equal("fromReq")
            })
            reqlab.test("env over global", function() {
                reqlab.expect(reqlab.variables.get("shared")).to.equal("fromEnv")
            })
            reqlab.test("global fallback", function() {
                reqlab.expect(reqlab.variables.get("globalOnly")).to.equal("fromGlobal")
            })
        """.trimIndent(), ctx(
            variables = mapOf("shared" to "fromEnv"),
            globalVariables = mapOf("shared" to "fromGlobal", "globalOnly" to "fromGlobal"),
        ))
        assertTrue(r.success)
        assertTrue(r.assertions.all { it.passed })
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Request Mutations
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun request_setBody() = runTest {
        val r = engine.executePreRequestScript("""
            reqlab.request.setBody('{"key":"value"}')
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("""{"key":"value"}""", r.requestMutations.body)
    }

    @Test
    fun request_headers_upsert() = runTest {
        val r = engine.executePreRequestScript("""
            reqlab.request.headers.upsert("X-Custom", "upserted")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("upserted", r.requestMutations.headers["X-Custom"])
    }

    @Test
    fun request_body_getter() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("body getter", function() {
                reqlab.expect(reqlab.request.body).to.equal("original body")
            })
        """.trimIndent(), ctx(requestBody = "original body"))
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun request_query_get() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("query get", function() {
                reqlab.expect(reqlab.request.query.get("page")).to.equal("1")
            })
        """.trimIndent(), ctx(requestQueryParams = mapOf("page" to "1")))
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Response Utilities
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun response_status_text() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("status text", function() {
                reqlab.expect(reqlab.response.status).to.equal("OK")
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun response_statusCode_alias() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("statusCode alias", function() {
                reqlab.expect(reqlab.response.statusCode).to.equal(200)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun response_time_alias() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("time alias", function() {
                reqlab.expect(reqlab.response.time).to.equal(150)
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun response_text_null_body() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("null body returns empty string", function() {
                reqlab.expect(reqlab.response.text()).to.equal("")
            })
        """.trimIndent(), ctx(body = ""))
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun response_json_invalid() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("invalid json throws", function() {
                var threw = false
                try {
                    reqlab.response.json()
                } catch(e) {
                    threw = true
                }
                reqlab.expect(threw).to.be.true
            })
        """.trimIndent(), ctx(body = "not json"))
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Mixed Pass/Fail
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun mixed_pass_fail_assertions() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("passes", function() {
                reqlab.expect(200).to.equal(200)
            })
            reqlab.test("fails intentionally", function() {
                reqlab.expect(200).to.equal(404)
            })
            reqlab.test("also passes", function() {
                reqlab.expect("hello").to.include("hell")
            })
        """.trimIndent(), ctx())
        assertFalse(r.success) // overall should fail due to one failure
        assertEquals(3, r.assertions.size)
        assertTrue(r.assertions[0].passed)
        assertFalse(r.assertions[1].passed)
        assertTrue(r.assertions[2].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Console Edge Cases
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun console_log_null_undefined() = runTest {
        val r = engine.executeTestScript("""
            reqlab.console.log(null, undefined)
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertTrue(r.logs.any { it.contains("null") })
        assertTrue(r.logs.any { it.contains("undefined") })
    }

    @Test
    fun console_log_object() = runTest {
        val r = engine.executeTestScript("""
            reqlab.console.log({a: 1, b: "two"})
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertTrue(r.logs.isNotEmpty())
        assertTrue(r.logs[0].contains("\"a\""))
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Script Injection Safety
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun script_with_quotes_and_backslashes() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("quote test", function() {
                var s = "He said \"hello\" with a \\ backslash"
                reqlab.expect(s).to.include("hello")
            })
        """.trimIndent(), ctx())
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun response_body_with_special_chars() = runTest {
        val body = """{"msg":"line1\nline2","path":"C:\\Users\\test"}"""
        val r = engine.executeTestScript("""
            reqlab.test("special chars in body", function() {
                var data = reqlab.response.json()
                reqlab.expect(data.msg).to.include("line1")
            })
        """.trimIndent(), ctx(body = body))
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Error Handling
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun throw_non_error_string() = runTest {
        val r = engine.executeTestScript("""
            throw "string error"
        """.trimIndent(), ctx())
        assertNotNull(r.error)
        assertTrue(r.error!!.contains("string error"))
    }

    @Test
    fun throw_non_error_number() = runTest {
        val r = engine.executeTestScript("""
            throw 42
        """.trimIndent(), ctx())
        assertNotNull(r.error)
    }

    @Test
    fun runtime_type_error() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("type error", function() {
                var x = null
                x.foo()
            })
        """.trimIndent(), ctx())
        assertFalse(r.assertions.isEmpty())
        assertFalse(r.assertions[0].passed)
    }

    @Test
    fun reference_error_in_test() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("ref error", function() {
                undeclaredVariable.something
            })
        """.trimIndent(), ctx())
        assertFalse(r.assertions.isEmpty())
        assertFalse(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Multiple Variable Scopes Together
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun all_variable_scopes_set_and_read() = runTest {
        val r = engine.executePreRequestScript("""
            reqlab.environment.set("eKey", "eVal")
            reqlab.globals.set("gKey", "gVal")
            reqlab.collectionVariables.set("cKey", "cVal")
            reqlab.variables.set("rKey", "rVal")
            reqlab.console.log("env:", reqlab.environment.get("eKey"))
            reqlab.console.log("glob:", reqlab.globals.get("gKey"))
            reqlab.console.log("coll:", reqlab.collectionVariables.get("cKey"))
            reqlab.console.log("req:", reqlab.variables.get("rKey"))
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("eVal", r.newVariables["eKey"])
        assertEquals("gVal", r.newGlobalVariables["gKey"])
        assertEquals("cVal", r.newCollectionVariables["cKey"])
        assertEquals("rVal", r.newRequestVariables["rKey"])
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Multiple Tests, Various Status Codes
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun status_404_handling() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("status is 404", function() {
                reqlab.expect(reqlab.response.code).to.equal(404)
            })
            reqlab.test("status text is Not Found", function() {
                reqlab.expect(reqlab.response.status).to.equal("Not Found")
            })
        """.trimIndent(), ctx(status = 404))
        assertTrue(r.success)
        assertTrue(r.assertions.all { it.passed })
    }

    @Test
    fun status_500_handling() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("status is 500", function() {
                reqlab.expect(reqlab.response.code).to.equal(500)
            })
            reqlab.test("status text is Internal Server Error", function() {
                reqlab.expect(reqlab.response.status).to.equal("Internal Server Error")
            })
        """.trimIndent(), ctx(status = 500))
        assertTrue(r.success)
        assertTrue(r.assertions.all { it.passed })
    }

    @Test
    fun status_201_handling() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("201 Created", function() {
                reqlab.expect(reqlab.response.code).to.equal(201)
                reqlab.expect(reqlab.response.status).to.equal("Created")
            })
        """.trimIndent(), ctx(status = 201))
        assertTrue(r.success)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Namespace Aliases
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun global_alias_env_get_set() = runTest {
        val r = engine.executePreRequestScript("""
            env.set("aliasKey", "aliasVal")
            reqlab.console.log(env.get("aliasKey"))
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("aliasVal", r.newVariables["aliasKey"])
    }

    @Test
    fun global_alias_vars_set() = runTest {
        val r = engine.executePreRequestScript("""
            vars.set("vKey", "vVal")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("vVal", r.newRequestVariables["vKey"])
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Pre-request vs Test Script Parity
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun pre_request_can_run_assertions() = runTest {
        val r = engine.executePreRequestScript("""
            reqlab.test("pre-request assertion", function() {
                reqlab.expect(1 + 1).to.equal(2)
            })
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals(1, r.assertions.size)
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun test_script_can_mutate_request() = runTest {
        // Technically mutations in test scripts are unusual but should still work
        val r = engine.executeTestScript("""
            reqlab.request.headers.add("X-Post-Test", "yes")
        """.trimIndent(), ctx())
        assertTrue(r.success)
        assertEquals("yes", r.requestMutations.headers["X-Post-Test"])
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Large Response Body
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun large_response_body() = runTest {
        val largeBody = """{"items":[""" + (1..100).joinToString(",") { """{"id":$it,"value":"item-$it"}""" } + """]}"""
        val r = engine.executeTestScript("""
            reqlab.test("parse large body", function() {
                var data = reqlab.response.json()
                reqlab.expect(data.items).to.have.lengthOf(100)
                reqlab.expect(data.items[0].id).to.equal(1)
                reqlab.expect(data.items[99].id).to.equal(100)
            })
        """.trimIndent(), ctx(body = largeBody))
        assertTrue(r.success)
        assertTrue(r.assertions[0].passed)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDED QE COVERAGE — Edge Cases with Empty/Null Context
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun empty_response_body() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("empty body", function() {
                reqlab.expect(reqlab.response.text()).to.equal("")
            })
        """.trimIndent(), ctx(body = ""))
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun response_with_no_headers() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("no headers", function() {
                reqlab.expect(reqlab.response.headers.get("NonExistent")).to.not.exist
            })
        """.trimIndent(), ctx(responseHeaders = emptyMap()))
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun response_size_zero() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("zero size", function() {
                reqlab.expect(reqlab.response.size).to.equal(0)
            })
        """.trimIndent(), ctx(responseSizeBytes = 0, body = ""))
        assertTrue(r.assertions[0].passed)
    }

    @Test
    fun status_text_extended_codes() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("418 status text", function() {
                reqlab.expect(reqlab.response.status).to.equal("I'm a Teapot")
            })
        """.trimIndent(), ctx(status = 418))
        assertTrue(r.success)
    }

    @Test
    fun console_log_circular_object_does_not_crash_script() = runTest {
        val r = engine.executeTestScript("""
            reqlab.test("circular object logging", function() {
                var o = {}
                o.self = o
                reqlab.console.log("obj", o)
                reqlab.expect(true).to.be.true
            })
        """.trimIndent(), ctx())
        assertTrue(r.success, "Expected logging to be resilient, got error: ${r.error}")
        assertTrue(r.logs.any { it.contains("obj") })
    }

    @Test
    fun response_body_with_nul_character_is_safely_injected() = runTest {
        val bodyWithNul = "prefix\u0000suffix"
        val r = engine.executeTestScript("""
            reqlab.test("nul-safe body", function() {
                reqlab.expect(reqlab.response.text()).to.include("prefix")
                reqlab.expect(reqlab.response.text()).to.include("suffix")
            })
        """.trimIndent(), ctx(body = bodyWithNul, responseHeaders = mapOf("Content-Type" to "text/plain")))
        assertTrue(r.success, "Expected control chars to be escaped safely, got error: ${r.error}")
    }

    @Test
    fun infinite_loop_script_times_out() = runTest {
        val r = engine.executeTestScript("""
            while (true) {}
        """.trimIndent(), ctx())
        assertFalse(r.success)
        assertNotNull(r.error)
        assertTrue(r.error!!.contains("timed out", ignoreCase = true))
    }

    @Test
    fun status_text_extended_5xx_and_unknown() = runTest {
        val expected = mapOf(
            501 to "Not Implemented",
            502 to "Bad Gateway",
            503 to "Service Unavailable",
            504 to "Gateway Timeout",
            505 to "HTTP Version Not Supported",
            506 to "Variant Also Negotiates",
            507 to "Insufficient Storage",
            508 to "Loop Detected",
            510 to "Not Extended",
            511 to "Network Authentication Required",
            599 to "",
        )

        expected.forEach { (statusCode, statusText) ->
            val result = engine.executeTestScript(
                """
                reqlab.test("status $statusCode", function() {
                    reqlab.expect(reqlab.response.status).to.equal(${if (statusText.isEmpty()) "\"\"" else "\"$statusText\""})
                })
                """.trimIndent(),
                ctx(status = statusCode),
            )
            assertTrue(result.success, "Expected mapped status text for $statusCode, got: ${result.error}")
        }
    }

    @Test
    fun response_body_escape_newline_carriage_tab_and_unicode_separators() = runTest {
        val special = "a\nb\rc\td\u2028e\u2029f"
        val r = engine.executeTestScript(
            """
            reqlab.test("special chars preserved", function() {
                var t = reqlab.response.text()
                reqlab.expect(t.charCodeAt(1)).to.equal(10)
                reqlab.expect(t.charCodeAt(3)).to.equal(13)
                reqlab.expect(t.charCodeAt(5)).to.equal(9)
                reqlab.expect(t.charCodeAt(7)).to.equal(8232)
                reqlab.expect(t.charCodeAt(9)).to.equal(8233)
            })
            """.trimIndent(),
            ctx(body = special, responseHeaders = mapOf("Content-Type" to "text/plain")),
        )

        assertTrue(r.success, "Expected special characters to survive JS bootstrap escaping, got: ${r.error}")
    }

    @Test
    fun execute_returns_script_execution_failed_when_evaluator_throws() = runTest {
        val failingEngine = ReqLabScriptEngine(
            evaluator = { throw IllegalStateException("boom") },
        )

        val r = failingEngine.executeTestScript(
            "reqlab.test('noop', function(){ reqlab.expect(true).to.be.true })",
            ctx(),
        )

        assertFalse(r.success)
        assertEquals("Script execution failed: boom", r.error)
    }

    @Test
    fun assertion_result_serializer_companion_is_accessible() {
        val encoded = Json.encodeToString(
            AssertionResult.serializer(),
            AssertionResult(name = "n", passed = true, message = null),
        )
        assertTrue(encoded.contains("\"name\":\"n\""))
    }

    @Test
    fun scripting_contract_serializers_are_accessible() {
        val contextJson = Json.encodeToString(
            ScriptContext.serializer(),
            ScriptContext(url = "https://example.com", method = "GET"),
        )
        val mutationsJson = Json.encodeToString(
            ScriptRequestMutations.serializer(),
            ScriptRequestMutations(url = "https://example.com/next", method = "POST"),
        )
        val resultJson = Json.encodeToString(
            ScriptResult.serializer(),
            ScriptResult(success = true),
        )

        assertTrue(contextJson.contains("\"url\":\"https://example.com\""))
        assertTrue(mutationsJson.contains("\"method\":\"POST\""))
        assertTrue(resultJson.contains("\"success\":true"))
    }

    @Test
    fun response_statusIs_pass_and_fail_behaviour() = runTest {
        val pass = engine.executeTestScript(
            """
            reqlab.test("statusIs pass", function() {
                reqlab.response.statusIs(200)
                reqlab.expect(true).to.be.true
            })
            """.trimIndent(),
            ctx(status = 200),
        )
        assertTrue(pass.success)

        val fail = engine.executeTestScript(
            """
            reqlab.test("statusIs fail", function() {
                reqlab.response.statusIs(201)
            })
            """.trimIndent(),
            ctx(status = 200),
        )
        assertFalse(fail.success)
        assertTrue(fail.assertions.isNotEmpty())
        assertFalse(fail.assertions.first().passed)
    }

    @Test
    fun scope_clear_methods_remove_all_values() = runTest {
        val r = engine.executePreRequestScript(
            """
            reqlab.environment.clear()
            reqlab.globals.clear()
            reqlab.collectionVariables.clear()

            reqlab.test("env cleared", function() {
                reqlab.expect(Object.keys(reqlab.environment.toObject()).length).to.equal(0)
            })
            reqlab.test("globals cleared", function() {
                reqlab.expect(Object.keys(reqlab.globals.toObject()).length).to.equal(0)
            })
            reqlab.test("collection cleared", function() {
                reqlab.expect(Object.keys(reqlab.collectionVariables.toObject()).length).to.equal(0)
            })
            """.trimIndent(),
            ctx(
                variables = mapOf("e1" to "v1"),
                globalVariables = mapOf("g1" to "v1"),
                collectionVariables = mapOf("c1" to "v1"),
            ),
        )

        assertTrue(r.success)
        assertTrue(r.assertions.all { it.passed })
        assertTrue(r.newVariables.isEmpty())
        assertTrue(r.newGlobalVariables.isEmpty())
        assertTrue(r.newCollectionVariables.isEmpty())
    }

    @Test
    fun request_setHeader_and_headers_get_use_mutated_value() = runTest {
        val r = engine.executePreRequestScript(
            """
            reqlab.request.setHeader("X-Trace", "new-trace")
            reqlab.test("headers.get returns mutated", function() {
                reqlab.expect(reqlab.request.headers.get("X-Trace")).to.equal("new-trace")
            })
            """.trimIndent(),
            ctx(requestHeaders = mapOf("X-Trace" to "old-trace")),
        )

        assertTrue(r.success)
        assertEquals("new-trace", r.requestMutations.headers["X-Trace"])
        assertTrue(r.assertions.all { it.passed })
    }

    @Test
    fun request_params_alias_get_reads_query_values() = runTest {
        val r = engine.executeTestScript(
            """
            reqlab.test("params alias get", function() {
                reqlab.expect(reqlab.request.params.get("page")).to.equal("3")
            })
            """.trimIndent(),
            ctx(requestQueryParams = mapOf("page" to "3")),
        )

        assertTrue(r.success)
        assertTrue(r.assertions.first().passed)
    }

    @Test
    fun global_aliases_for_global_and_collection_scopes_work() = runTest {
        val r = engine.executePreRequestScript(
            """
            global.set("gAlias", "gVal")
            collection.set("cAlias", "cVal")
            reqlab.test("global alias read", function() {
                reqlab.expect(global.get("gAlias")).to.equal("gVal")
            })
            reqlab.test("collection alias read", function() {
                reqlab.expect(collection.get("cAlias")).to.equal("cVal")
            })
            """.trimIndent(),
            ctx(),
        )

        assertTrue(r.success)
        assertEquals("gVal", r.newGlobalVariables["gAlias"])
        assertEquals("cVal", r.newCollectionVariables["cAlias"])
        assertTrue(r.assertions.all { it.passed })
    }
}
