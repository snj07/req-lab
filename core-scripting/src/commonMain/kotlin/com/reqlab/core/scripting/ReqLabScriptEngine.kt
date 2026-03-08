package com.reqlab.core.scripting

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Pure Kotlin implementation of the ScriptEngine interface.
 *
 * Supports a Postman-compatible `pm` API subset:
 *
 * Pre-request scripts:
 *   pm.environment.set("key", "value")  – set/override an environment variable
 *   pm.environment.get("key")           – read a variable (useful in console.log)
 *   console.log("msg", ...)            – log a message
 *
 * Test scripts (run after the response arrives):
 *   pm.test("Test name", function() {
 *       pm.expect(pm.response.code).to.equal(200)
 *       pm.expect(pm.response.code).to.be.oneOf([200, 201])
 *       pm.expect(pm.response.code).to.be.above(199)
 *       pm.expect(pm.response.code).to.be.below(300)
 *       pm.expect(pm.response.text()).to.include("ok")
 *       pm.expect(pm.response.json().name).to.equal("Alice")
 *       pm.expect(pm.response.json().users[0].id).to.equal(1)
 *       pm.expect(pm.response.json().count).to.be.above(0)
 *       pm.response.to.have.status(200)
 *   })
 *   pm.expect(...).to.equal(...)        – standalone assertion (outside pm.test)
 *   console.log("msg")
 */
class ReqLabScriptEngine : ScriptEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun executePreRequestScript(
        script: String,
        context: ScriptContext,
    ): ScriptResult {
        val interp = Interpreter(context, isPreRequest = true, json = json)
        interp.run(script)
        return ScriptResult(
            success       = interp.error == null,
            logs          = interp.logs,
            assertions    = emptyList(),
            error         = interp.error,
            newVariables  = interp.newVariables,
        )
    }

    override suspend fun executeTestScript(
        script: String,
        context: ScriptContext,
    ): ScriptResult {
        val interp = Interpreter(context, isPreRequest = false, json = json)
        interp.run(script)
        return ScriptResult(
            success       = interp.error == null && interp.assertions.all { it.passed },
            logs          = interp.logs,
            assertions    = interp.assertions,
            error         = interp.error,
            newVariables  = interp.newVariables,
        )
    }
}

// ── Interpreter ────────────────────────────────────────────────────────────

private class Interpreter(
    private val ctx: ScriptContext,
    private val isPreRequest: Boolean,
    private val json: Json,
) {
    val logs          = mutableListOf<String>()
    val assertions    = mutableListOf<AssertionResult>()
    val newVariables  = mutableMapOf<String, String>()
    var error: String? = null

    private val parsedJson: JsonElement? by lazy {
        ctx.responseBody?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
    }

    fun run(script: String) {
        runCatching { interpret(script) }.onFailure { e ->
            error = "Script error: ${e.message}"
        }
    }

    private fun interpret(script: String) {
        val normalized = script.trimIndent()
        var pos = 0
        while (pos < normalized.length) {
            // Skip whitespace and empty lines
            while (pos < normalized.length && normalized[pos].isWhitespace()) pos++
            if (pos >= normalized.length) break

            when {
                normalized.startsWith("pm.test(", pos)           -> pos = handlePmTest(normalized, pos)
                normalized.startsWith("pm.environment.set(", pos) -> pos = handleEnvSet(normalized, pos)
                normalized.startsWith("pm.environment.get(", pos) -> pos = skipStatement(normalized, pos)
                normalized.startsWith("pm.expect(", pos)          -> pos = handleExpect(normalized, pos, testName = null)
                normalized.startsWith("pm.response.to.have.status(", pos) -> pos = handleResponseStatus(normalized, pos, testName = null)
                normalized.startsWith("console.log(", pos)        -> pos = handleConsoleLog(normalized, pos)
                normalized.startsWith("//") -> pos = skipLineComment(normalized, pos)
                normalized.startsWith("/*") -> pos = skipBlockComment(normalized, pos)
                else -> pos = skipStatement(normalized, pos)
            }
        }
    }

    // ── pm.test("name", function() { ... }) ───────────────────────────────

    private fun handlePmTest(s: String, start: Int): Int {
        val nameStart = s.indexOf('"', start + 8).takeIf { it >= 0 } ?: return skipStatement(s, start)
        val nameEnd   = s.indexOf('"', nameStart + 1).takeIf { it >= 0 } ?: return skipStatement(s, start)
        val testName  = s.substring(nameStart + 1, nameEnd)

        // find the opening brace of the function body
        val braceOpen = s.indexOf('{', nameEnd).takeIf { it >= 0 } ?: return skipStatement(s, start)
        val bodyRange = extractBraceBlock(s, braceOpen) ?: return skipStatement(s, start)
        val body      = s.substring(braceOpen + 1, bodyRange.first)

        // accumulate assertions within the test body
        var bodyPos = 0
        val trimmedBody = body.trimIndent()
        while (bodyPos < trimmedBody.length) {
            while (bodyPos < trimmedBody.length && trimmedBody[bodyPos].isWhitespace()) bodyPos++
            if (bodyPos >= trimmedBody.length) break
            bodyPos = when {
                trimmedBody.startsWith("pm.expect(", bodyPos)          -> handleExpect(trimmedBody, bodyPos, testName)
                trimmedBody.startsWith("pm.response.to.have.status(", bodyPos) -> handleResponseStatus(trimmedBody, bodyPos, testName)
                trimmedBody.startsWith("console.log(", bodyPos)        -> handleConsoleLog(trimmedBody, bodyPos)
                trimmedBody.startsWith("//", bodyPos)                  -> skipLineComment(trimmedBody, bodyPos)
                else                                                    -> skipStatement(trimmedBody, bodyPos)
            }
        }

        return bodyRange.second + 1
    }

    // ── pm.expect(expr).to.XXX(val) ───────────────────────────────────────

    private fun handleExpect(s: String, start: Int, testName: String?): Int {
        val parenOpen = start + "pm.expect(".length - 1
        val exprRange = extractParenBlock(s, parenOpen) ?: return skipStatement(s, start)
        val exprSrc   = s.substring(parenOpen + 1, exprRange.first).trim()

        // parse the chain after pm.expect(...)
        var cur = exprRange.second + 1
        // skip .to .be .not (qualifiers)
        val chain = buildString {
            while (cur < s.length && (s[cur] == '.' || s[cur].isLetter() || s[cur].isDigit())) {
                append(s[cur++])
            }
        }

        // now cur should be at the '(' of the terminal assertion
        if (cur >= s.length || s[cur] != '(') return cur

        val argRange = extractParenBlock(s, cur) ?: return skipStatement(s, cur)
        val argSrc   = s.substring(cur + 1, argRange.first).trim()
        val endPos   = argRange.second + 1

        val actual   = evalExpr(exprSrc)
        val expected = evalArgument(argSrc)

        val assertion = when {
            chain.endsWith(".equal")      -> assertEquals(testName, exprSrc, actual, expected)
            chain.endsWith(".eql")        -> assertEquals(testName, exprSrc, actual, expected)
            chain.endsWith(".include")    -> assertInclude(testName, exprSrc, actual, expected?.toString())
            chain.endsWith(".oneOf")      -> assertOneOf(testName, exprSrc, actual, argSrc)
            chain.endsWith(".above")      -> assertAbove(testName, exprSrc, actual, expected)
            chain.endsWith(".below")      -> assertBelow(testName, exprSrc, actual, expected)
            chain.endsWith(".least")      -> assertAtLeast(testName, exprSrc, actual, expected)
            chain.endsWith(".most")       -> assertAtMost(testName, exprSrc, actual, expected)
            chain.endsWith(".empty")      -> assertEmpty(testName, exprSrc, actual)
            chain.endsWith(".ok")         -> assertTruthy(testName, exprSrc, actual)
            chain.endsWith(".exist")      -> assertExists(testName, exprSrc, actual)
            chain.endsWith(".null")       -> assertNull(testName, exprSrc, actual)
            else                          -> null
        }
        assertion?.let { assertions.add(it) }

        return skipToStatementEnd(s, endPos)
    }

    // ── pm.response.to.have.status(200) ───────────────────────────────────

    private fun handleResponseStatus(s: String, start: Int, testName: String?): Int {
        val parenOpen = s.indexOf('(', start)
        val argRange  = parenOpen.takeIf { it >= 0 }?.let { extractParenBlock(s, it) } ?: return skipStatement(s, start)
        val expected  = s.substring(parenOpen + 1, argRange.first).trim().toIntOrNull()
        val actual    = ctx.statusCode
        val name      = testName?.let { "$it · status" } ?: "Status code is ${expected}"
        if (expected != null) {
            assertions.add(
                AssertionResult(name, actual == expected,
                    if (actual == expected) null else "Expected $expected but got $actual")
            )
        }
        return argRange.second + 1
    }

    // ── pm.environment.set("key", "value") ────────────────────────────────

    private fun handleEnvSet(s: String, start: Int): Int {
        val parenOpen = start + "pm.environment.set".length
        val argRange  = extractParenBlock(s, parenOpen) ?: return skipStatement(s, start)
        val args      = s.substring(parenOpen + 1, argRange.first)
        val parts     = splitTopLevelArgs(args)
        if (parts.size >= 2) {
            val key   = unquote(parts[0].trim())
            val value = evalArgument(parts[1].trim())?.toString() ?: ""
            newVariables[key] = value
            logs.add("[pre-request] env.set($key = \"$value\")")
        }
        return argRange.second + 1
    }

    // ── console.log("msg", ...) ────────────────────────────────────────────

    private fun handleConsoleLog(s: String, start: Int): Int {
        val parenOpen = start + "console.log".length
        val argRange  = extractParenBlock(s, parenOpen) ?: return skipStatement(s, start)
        val argsRaw   = s.substring(parenOpen + 1, argRange.first)
        val parts     = splitTopLevelArgs(argsRaw)
        val rendered  = parts.joinToString(" ") { arg ->
            evalArgument(arg.trim())?.toString() ?: "undefined"
        }
        logs.add("[console] $rendered")
        return argRange.second + 1
    }

    // ── Expression evaluator ───────────────────────────────────────────────

    /**
     * Evaluates a limited expression language:
     *   pm.response.code → Int?
     *   pm.response.status → String?
     *   pm.response.text() → String?
     *   pm.response.responseTime → Long?
     *   pm.response.json() → JsonElement?
     *   pm.response.json().field → Any?
     *   pm.response.json().arr[0].field → Any?
     *   pm.response.headers["name"] → String?
     *   pm.environment.get("key") → String?
     *   numeric literal, string literal, boolean
     */
    private fun evalExpr(expr: String): Any? {
        val e = expr.trim()
        return when {
            e == "pm.response.code"                 -> ctx.statusCode
            e == "pm.response.status"               -> ctx.statusCode?.toString()
            e == "pm.response.responseTime"         -> ctx.responseTimeMs
            e.startsWith("pm.response.text()")      -> ctx.responseBody
            e.startsWith("pm.response.json()")      -> {
                val path = e.removePrefix("pm.response.json()").trim()
                if (path.isEmpty()) parsedJson
                else resolveJsonPath(parsedJson, path)
            }
            e.startsWith("pm.response.headers[")   -> {
                val key = unquote(e.substringAfter('[').substringBefore(']'))
                ctx.responseHeaders[key]
                    ?: ctx.responseHeaders[key.lowercase()]
                    ?: ctx.responseHeaders.entries
                        .firstOrNull { it.key.lowercase() == key.lowercase() }?.value
            }
            e.startsWith("pm.environment.get(")    -> {
                val key = unquote(e.substringAfter('(').substringBefore(')').trim())
                ctx.variables[key] ?: newVariables[key]
            }
            else                                    -> evalLiteral(e)
        }
    }

    /** Evaluates a string/number/boolean literal or sub-expression. */
    private fun evalArgument(raw: String): Any? {
        val t = raw.trim()
        return when {
            t.startsWith("\"") || t.startsWith("'") -> unquote(t)
            t == "true"          -> true
            t == "false"         -> false
            t == "null"          -> null
            t.startsWith("[")    -> null // arrays handled by specific assertion logic
            else                 -> t.toIntOrNull() ?: t.toLongOrNull() ?: t.toDoubleOrNull() ?: evalExpr(t)
        }
    }

    private fun evalLiteral(t: String): Any? =
        when {
            t.startsWith('"') || t.startsWith('\'') -> unquote(t)
            t == "true"  -> true
            t == "false" -> false
            t == "null"  -> null
            else         -> t.toIntOrNull() ?: t.toLongOrNull() ?: t.toDoubleOrNull()
        }

    // ── JSON path resolution ───────────────────────────────────────────────

    /**
     * Resolves a dot/bracket path like `.name`, `.users[0].id`, `["key"]`
     * against a JsonElement.
     */
    private fun resolveJsonPath(element: JsonElement?, path: String): Any? {
        if (element == null || path.isBlank()) return jsonToKotlin(element)
        var cur: JsonElement? = element
        val tokens = tokenizePath(path)
        for (token in tokens) {
            cur = when {
                token.startsWith("[") && token.endsWith("]") -> {
                    val inner = token.substring(1, token.length - 1).trim()
                    val idx   = inner.toIntOrNull()
                    if (idx != null) {
                        (cur as? JsonArray)?.getOrNull(idx)
                    } else {
                        val key = unquote(inner)
                        (cur as? JsonObject)?.get(key)
                    }
                }
                else -> (cur as? JsonObject)?.get(token)
            }
        }
        return jsonToKotlin(cur)
    }

    /** Tokenizes a path like `.name.users[0].id` into ["name", "users", "[0]", "id"]. */
    private fun tokenizePath(path: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        val p = path.trimStart('.')
        while (i < p.length) {
            when {
                p[i] == '.' -> { i++; continue }
                p[i] == '[' -> {
                    val end = p.indexOf(']', i)
                    tokens += p.substring(i, if (end >= 0) end + 1 else p.length)
                    i = if (end >= 0) end + 1 else p.length
                }
                else -> {
                    val start = i
                    while (i < p.length && p[i] != '.' && p[i] != '[') i++
                    tokens += p.substring(start, i)
                }
            }
        }
        return tokens
    }

    /** Converts a JsonElement to a Kotlin type for comparison. */
    private fun jsonToKotlin(el: JsonElement?): Any? = when (el) {
        null, JsonNull  -> null
        is JsonPrimitive -> when {
            el.isString         -> el.content
            el.content == "true"  -> true
            el.content == "false" -> false
            else -> el.content.toIntOrNull()
                ?: el.content.toLongOrNull()
                ?: el.content.toDoubleOrNull()
                ?: el.content
        }
        is JsonObject   -> el   // returned as-is for contains / type checks
        is JsonArray    -> el
    }

    // ── Assertion builders ─────────────────────────────────────────────────

    private fun assertEquals(testName: String?, exprSrc: String, actual: Any?, expected: Any?): AssertionResult {
        val name    = testName?.let { "$it · $exprSrc equals $expected" } ?: "$exprSrc equals $expected"
        val passed  = compareValues(actual, expected)
        return AssertionResult(name, passed, if (passed) null else "Expected $expected but got $actual")
    }

    private fun assertInclude(testName: String?, exprSrc: String, actual: Any?, expected: String?): AssertionResult {
        val name   = testName?.let { "$it · $exprSrc includes $expected" } ?: "$exprSrc includes $expected"
        val passed = expected != null && actual?.toString()?.contains(expected) == true
        return AssertionResult(name, passed, if (passed) null else "\"$actual\" does not include \"$expected\"")
    }

    private fun assertOneOf(testName: String?, exprSrc: String, actual: Any?, arrayLiteral: String): AssertionResult {
        val name     = testName?.let { "$it · $exprSrc oneOf $arrayLiteral" } ?: "$exprSrc oneOf $arrayLiteral"
        val elements = arrayLiteral.trim().removePrefix("[").removeSuffix("]")
            .split(',')
            .map { evalLiteral(it.trim()) }
        val passed   = elements.any { compareValues(actual, it) }
        return AssertionResult(name, passed, if (passed) null else "$actual not in $arrayLiteral")
    }

    private fun assertAbove(testName: String?, exprSrc: String, actual: Any?, expected: Any?): AssertionResult {
        val name   = testName?.let { "$it · $exprSrc above $expected" } ?: "$exprSrc above $expected"
        val passed = toDouble(actual) != null && toDouble(expected) != null && toDouble(actual)!! > toDouble(expected)!!
        return AssertionResult(name, passed, if (passed) null else "$actual is not above $expected")
    }

    private fun assertBelow(testName: String?, exprSrc: String, actual: Any?, expected: Any?): AssertionResult {
        val name   = testName?.let { "$it · $exprSrc below $expected" } ?: "$exprSrc below $expected"
        val passed = toDouble(actual) != null && toDouble(expected) != null && toDouble(actual)!! < toDouble(expected)!!
        return AssertionResult(name, passed, if (passed) null else "$actual is not below $expected")
    }

    private fun assertAtLeast(testName: String?, exprSrc: String, actual: Any?, expected: Any?): AssertionResult {
        val name   = testName?.let { "$it · $exprSrc >= $expected" } ?: "$exprSrc >= $expected"
        val passed = toDouble(actual) != null && toDouble(expected) != null && toDouble(actual)!! >= toDouble(expected)!!
        return AssertionResult(name, passed, if (passed) null else "$actual is not >= $expected")
    }

    private fun assertAtMost(testName: String?, exprSrc: String, actual: Any?, expected: Any?): AssertionResult {
        val name   = testName?.let { "$it · $exprSrc <= $expected" } ?: "$exprSrc <= $expected"
        val passed = toDouble(actual) != null && toDouble(expected) != null && toDouble(actual)!! <= toDouble(expected)!!
        return AssertionResult(name, passed, if (passed) null else "$actual is not <= $expected")
    }

    private fun assertEmpty(testName: String?, exprSrc: String, actual: Any?): AssertionResult {
        val name   = testName?.let { "$it · $exprSrc is empty" } ?: "$exprSrc is empty"
        val passed = actual == null || actual.toString().isEmpty()
        return AssertionResult(name, passed, if (passed) null else "$actual is not empty")
    }

    private fun assertTruthy(testName: String?, exprSrc: String, actual: Any?): AssertionResult {
        val name   = testName?.let { "$it · $exprSrc is truthy" } ?: "$exprSrc is truthy"
        val passed = actual != null && actual != false && actual != 0 && actual.toString() != ""
        return AssertionResult(name, passed, if (passed) null else "$actual is not truthy")
    }

    private fun assertExists(testName: String?, exprSrc: String, actual: Any?): AssertionResult {
        val name   = testName?.let { "$it · $exprSrc exists" } ?: "$exprSrc exists"
        val passed = actual != null
        return AssertionResult(name, passed, if (passed) null else "$exprSrc is null/undefined")
    }

    private fun assertNull(testName: String?, exprSrc: String, actual: Any?): AssertionResult {
        val name   = testName?.let { "$it · $exprSrc is null" } ?: "$exprSrc is null"
        val passed = actual == null
        return AssertionResult(name, passed, if (passed) null else "$exprSrc is not null (got $actual)")
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun compareValues(a: Any?, b: Any?): Boolean {
        if (a == b) return true
        val ad = toDouble(a)
        val bd = toDouble(b)
        if (ad != null && bd != null) return ad == bd
        return a?.toString() == b?.toString()
    }

    private fun toDouble(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else      -> null
    }

    private fun unquote(s: String): String {
        val t = s.trim()
        return when {
            (t.startsWith('"') && t.endsWith('"')) || (t.startsWith('\'') && t.endsWith('\'')) ->
                t.substring(1, t.length - 1)
            else -> t
        }
    }

    /**
     * Extracts the contents of a matching `(...)` block starting at [open].
     * Returns Pair(indexOfLastContentChar, indexOfCloseParen).
     */
    private fun extractParenBlock(s: String, open: Int): Pair<Int, Int>? {
        if (open >= s.length || s[open] != '(') return null
        var depth = 0
        var i = open
        while (i < s.length) {
            when (s[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return Pair(i, i) }
                '"', '\'' -> {
                    val q = s[i]; i++
                    while (i < s.length && s[i] != q) { if (s[i] == '\\') i++; i++ }
                }
            }
            i++
        }
        return null
    }

    /**
     * Extracts the content of a matching `{...}` block starting at [open].
     * Returns Pair(indexOfLastContentChar, indexOfCloseBrace).
     */
    private fun extractBraceBlock(s: String, open: Int): Pair<Int, Int>? {
        if (open >= s.length || s[open] != '{') return null
        var depth = 0
        var i = open
        while (i < s.length) {
            when (s[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return Pair(i, i) }
                '"', '\'' -> {
                    val q = s[i]; i++
                    while (i < s.length && s[i] != q) { if (s[i] == '\\') i++; i++ }
                }
            }
            i++
        }
        return null
    }

    /** Splits top-level comma-separated args (respects nested parens/braces/quotes). */
    private fun splitTopLevelArgs(s: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        var i = 0
        while (i < s.length) {
            when (s[i]) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> depth--
                '"', '\'' -> {
                    val q = s[i]; i++
                    while (i < s.length && s[i] != q) { if (s[i] == '\\') i++; i++ }
                }
                ',' -> if (depth == 0) { parts += s.substring(start, i); start = i + 1 }
            }
            i++
        }
        parts += s.substring(start)
        return parts
    }

    private val NEWLINE = Regex("""\r?\n""")

    private fun skipLineComment(s: String, pos: Int): Int {
        val nl = s.indexOf('\n', pos)
        return if (nl < 0) s.length else nl + 1
    }

    private fun skipBlockComment(s: String, pos: Int): Int {
        val end = s.indexOf("*/", pos + 2)
        return if (end < 0) s.length else end + 2
    }

    private fun skipStatement(s: String, pos: Int): Int {
        // skip to next ';', '}', or newline — whichever comes first
        var i = pos
        while (i < s.length && s[i] != ';' && s[i] != '\n') {
            when (s[i]) {
                '(' -> { extractParenBlock(s, i)?.let { i = it.second }; i++ ; continue }
                '{' -> { extractBraceBlock(s, i)?.let { i = it.second }; i++; continue }
                '"', '\'' -> {
                    val q = s[i]; i++
                    while (i < s.length && s[i] != q) { if (s[i] == '\\') i++; i++ }
                    i++; continue
                }
            }
            i++
        }
        return if (i < s.length) i + 1 else i
    }

    private fun skipToStatementEnd(s: String, from: Int): Int {
        var i = from
        while (i < s.length && (s[i] == ';' || s[i] == ' ' || s[i] == '\r' || s[i] == '\n')) i++
        return i
    }

    // Unused — kept for potential future use
    private val newVariablesMirror get() = newVariables
}
