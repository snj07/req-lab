package com.reqlab.ui.shared.components

import com.reqlab.core.model.AuthConfig
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormDataEntry
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.NetworkEvent
import com.reqlab.core.network.NetworkLogger
import com.reqlab.core.network.RetryPolicy
import com.reqlab.core.network.VariableResolver
import com.reqlab.core.scripting.ReqLabScriptEngine
import com.reqlab.core.scripting.ScriptContext
import com.reqlab.ui.shared.network.NetworkClientFactory
import com.reqlab.ui.shared.persistence.TabsRepository
import com.reqlab.ui.shared.persistence.WorkspaceRepository
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.LogLevel
import com.reqlab.ui.shared.state.RequestTabState
import com.reqlab.ui.shared.state.TestResultEntry
import com.reqlab.ui.shared.platform.currentTimeMillis
import com.reqlab.ui.shared.platform.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Issues an HTTP request for [tab] and streams the result back into [tab]'s
 * state properties.  All heavy work runs off the main thread.
 */
private val scriptEngine = ReqLabScriptEngine()
private const val BINARY_ATTACHMENT_PREFIX = "reqlab-binary:"

fun sendRequest(scope: CoroutineScope, state: AppState, tab: RequestTabState) {
    if (tab.url.isBlank()) {
        state.log("URL is empty", LogLevel.WARNING)
        return
    }

    // H-1: Cancel any existing in-flight request before starting a new one.
    // This prevents concurrent requests racing to update the same tab state.
    tab.currentJob?.cancel()

    val job = scope.launch {
        tab.isLoading = true
        tab.response  = null
        tab.lastError = null
        state.testResults.clear()
        var effectiveUrl = tab.url
        var effectiveMethod = tab.method
        val effectiveHeaders = tab.headers
            .filter { it.enabled }
            .associate { it.key to it.value }
            .toMutableMap()
        val effectiveQueryParams = tab.params
            .filter { it.enabled }
            .associate { it.key to it.value }
            .toMutableMap()
        var effectiveBodyContent = tab.bodyContent
        val requestScopedScriptVars = mutableMapOf<String, String>()

        // ── Pre-request script ────────────────────────────────────────────
        if (tab.preRequestScript.isNotBlank()) {
            val layers = state.activeVariableLayers()
            val flatVars = buildMap<String, String> { layers.asReversed().forEach { putAll(it) } }
            val preCtx = ScriptContext(
                url       = tab.url,
                method    = tab.method.name,
                variables = flatVars,
                globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                collectionVariables = state.collectionVariables.toMap(),
                requestHeaders = effectiveHeaders,
                requestQueryParams = effectiveQueryParams,
                requestBody = tab.bodyContent,
            )
            // M-8: Clean up variables injected by the previous run of this script
            // so stale keys don't accumulate across re-sends.
            if (tab.scriptInjectedVarKeys.isNotEmpty()) {
                val env = state.selectedEnvironment
                env?.variables?.removeAll { it.key in tab.scriptInjectedVarKeys && it.kind == com.reqlab.ui.shared.state.HeaderKind.USER }
                tab.scriptInjectedVarKeys.clear()
            }
            val preResult = scriptEngine.executePreRequestScript(tab.preRequestScript, preCtx, state.settings.scriptPrefix)
            preResult.logs.forEach { state.log(it) }
            if (preResult.error != null) {
                state.log("⚠ Pre-request script error: ${preResult.error}", LogLevel.ERROR)
            }
            // Merge new variables from the script into the active environment
            if (preResult.newVariables.isNotEmpty()) {
                // M-8: Track which keys were added by this script run
                tab.scriptInjectedVarKeys.addAll(preResult.newVariables.keys)
                state.mergeScriptVariables(preResult.newVariables)
            }
            if (preResult.newRequestVariables.isNotEmpty()) {
                requestScopedScriptVars.putAll(preResult.newRequestVariables)
            }
            if (preResult.newGlobalVariables.isNotEmpty()) {
                state.mergeGlobalScriptVariables(preResult.newGlobalVariables)
            }
            if (preResult.newCollectionVariables.isNotEmpty()) {
                state.mergeCollectionScriptVariables(preResult.newCollectionVariables)
            }
            if (preResult.requestMutations.url != null) {
                effectiveUrl = preResult.requestMutations.url ?: effectiveUrl
            }
            if (preResult.requestMutations.method != null) {
                effectiveMethod = HttpMethodType.entries.firstOrNull {
                    it.name.equals(preResult.requestMutations.method, ignoreCase = true)
                } ?: effectiveMethod
            }
            if (preResult.requestMutations.body != null) {
                effectiveBodyContent = preResult.requestMutations.body ?: effectiveBodyContent
            }
            if (preResult.requestMutations.headers.isNotEmpty()) {
                effectiveHeaders.putAll(preResult.requestMutations.headers)
            }
            if (preResult.requestMutations.queryParams.isNotEmpty()) {
                effectiveQueryParams.putAll(preResult.requestMutations.queryParams)
            }
        }

        val effectiveUrlForLog = resolveUrlForLog(
            url = effectiveUrl,
            variableLayers = state.activeVariableLayers(),
            requestScopedVars = requestScopedScriptVars,
        )
        state.logNetworkEvent("→ $effectiveMethod $effectiveUrlForLog")

        try {
            val request = RequestDefinition(
                id = tab.id,
                name = tab.name,
                method = effectiveMethod,
                url = effectiveUrl,
                queryParams = effectiveQueryParams.map { KeyValueEntry(it.key, it.value) },
                headers = effectiveHeaders.map { KeyValueEntry(it.key, it.value) },
                auth = buildAuthConfig(tab),
                body = buildRequestBody(tab, effectiveBodyContent),
                createdAtEpochMillis = currentTimeMillis(),
                updatedAtEpochMillis = currentTimeMillis(),
            )

            val logger = object : NetworkLogger {
                override fun debug(message: String) { state.log(message) }
                override fun info(message: String)  { state.log(message) }
                override fun error(message: String, throwable: Throwable?) {
                    state.log(message, LogLevel.ERROR)
                }
            }

            val retryPolicy = if (tab.retryEnabled) {
                RetryPolicy(
                    maxAttempts = tab.retryCount.coerceAtLeast(1),
                    baseDelayMs = tab.retryDelayMs.coerceAtLeast(0L),
                    maxDelayMs  = (tab.retryDelayMs.coerceAtLeast(0L) * 10L)
                        .coerceAtLeast(tab.retryDelayMs),
                )
            } else {
                RetryPolicy(
                    maxAttempts = 1,
                    baseDelayMs = 0L,
                    maxDelayMs = 0L,
                )
            }

            val client = NetworkClientFactory.build(
                settings = state.settings,
                logger = logger,
                retryPolicy = retryPolicy,
            )

            client.execute(request, state.activeVariableLayers()).collect { event ->
                when (event) {
                    is NetworkEvent.Started -> {
                        state.recordHistory(
                            requestId = tab.id,
                            method = tab.method,
                            name = tab.name,
                            url = tab.url,
                        )
                        state.logNetworkEvent("Request started", LogLevel.INFO)
                    }
                    is NetworkEvent.RetryScheduled -> {
                        state.logNetworkEvent(
                            "Retry #${event.attempt} in ${event.delayMs}ms – ${event.reason}",
                            LogLevel.WARNING,
                        )
                    }
                    is NetworkEvent.Success -> {
                        tab.response  = event.response
                        tab.lastError = null
                        state.logNetworkEvent(
                            "← ${event.response.statusCode} ${event.response.statusText}" +
                                "  (${event.response.metrics.responseTimeMs}ms," +
                                " ${event.response.metrics.responseSizeBytes}B)",
                            LogLevel.SUCCESS,
                        )
                        // ── Post-request script ───────────────────────────
                        if (tab.testScript.isNotBlank()) {
                            val resp = event.response
                            val layers2 = state.activeVariableLayers()
                            val flatVars2 = buildMap<String, String> { layers2.asReversed().forEach { putAll(it) } }
                            val testCtx = ScriptContext(
                                url             = effectiveUrl,
                                method          = effectiveMethod.name,
                                statusCode      = resp.statusCode,
                                responseBody    = resp.bodyText,
                                responseHeaders = resp.headers.associate { it.key to it.value },
                                responseTimeMs  = resp.metrics.responseTimeMs,
                                variables       = buildMap {
                                    putAll(flatVars2)
                                    putAll(requestScopedScriptVars)
                                },
                                globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                                collectionVariables = state.collectionVariables.toMap(),
                                requestHeaders = effectiveHeaders,
                                requestQueryParams = effectiveQueryParams,
                                requestBody = effectiveBodyContent,
                            )
                            val testResult = scriptEngine.executeTestScript(tab.testScript, testCtx, state.settings.scriptPrefix)
                            testResult.logs.forEach { state.log(it) }
                            if (testResult.error != null) {
                                state.log("⚠ Post-request script error: ${testResult.error}", LogLevel.ERROR)
                            }
                            testResult.assertions.forEach { a ->
                                state.testResults.add(TestResultEntry(a.name, a.passed, a.message ?: ""))
                                state.log(
                                    "${if (a.passed) "✓" else "✗"} ${a.name}" +
                                        if (!a.passed && a.message != null) " — ${a.message}" else "",
                                    if (a.passed) LogLevel.SUCCESS else LogLevel.ERROR,
                                )
                            }
                            if (testResult.newVariables.isNotEmpty()) {
                                // M-8: Track test-script injected keys alongside pre-request keys
                                tab.scriptInjectedVarKeys.addAll(testResult.newVariables.keys)
                                state.mergeScriptVariables(testResult.newVariables)
                            }
                            if (testResult.newGlobalVariables.isNotEmpty()) {
                                state.mergeGlobalScriptVariables(testResult.newGlobalVariables)
                            }
                            if (testResult.newCollectionVariables.isNotEmpty()) {
                                state.mergeCollectionScriptVariables(testResult.newCollectionVariables)
                            }
                        }
                    }
                    is NetworkEvent.Failure -> {
                        tab.lastError = event.error.message ?: "Unknown error"
                        state.logNetworkEvent("✗ ${event.error.message}", LogLevel.ERROR)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                // Request was cancelled by the user – reset loading state cleanly.
                state.log("Request cancelled", LogLevel.WARNING)
            } else {
                tab.lastError = e.message ?: "Unknown error"
                state.log("✗ ${e.message ?: "Unknown error"}", LogLevel.ERROR)
            }
        } finally {
            tab.isLoading = false
            tab.currentJob = null
        }
    }
    // H-1: Store the job so it can be cancelled if Send is clicked again.
    tab.currentJob = job
}

/**
 * Saves [tab] to disk via [TabsRepository] and marks it as clean.
 * [onSaved] is invoked on the main thread after the save completes.
 */
fun saveRequest(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
    onSaved: (() -> Unit)? = null,
) {
    tab.syncSystemHeaders()
    scope.launch {
        val ok = withContext(ioDispatcher) { TabsRepository.save(state) }
        if (ok) {
            // Sync edited content back to the CollectionNode so workspace
            // export always reflects the latest saved state.
            state.syncTabToCollectionNode(tab)
            // Also persist the workspace immediately so export is up-to-date
            // even before the auto-save snapshotFlow fires.
            if (tab.collectionName != null) {
                withContext(ioDispatcher) { WorkspaceRepository.save(state) }
            }
            tab.markSaved()
            state.log("✓ Request saved: ${tab.name}", LogLevel.SUCCESS)
            onSaved?.invoke()
        } else {
            state.log("✗ Failed to save request: ${tab.name}", LogLevel.ERROR)
            state.showError(
                title = "Save failed",
                message = "The request could not be saved. Please try again.",
            )
        }
    }
}

// ── Internal helpers ────────────────────────────────────────────

fun buildAuthConfig(tab: RequestTabState): AuthConfig {
    val params = when (tab.authType) {
        AuthType.BASIC   -> mapOf("username" to tab.authUsername, "password" to tab.authPassword)
        AuthType.BEARER  -> mapOf("token" to tab.authToken)
        AuthType.JWT     -> mapOf("token" to tab.authToken)
        AuthType.API_KEY -> mapOf("key" to tab.authApiKey, "value" to tab.authApiValue)
        else             -> emptyMap()
    }
    return AuthConfig(type = tab.authType, params = params)
}

/**
 * Builds a [RequestBody] from [tab] state, using structured form rows when available.
 * Falls back to parsing [effectiveBodyContent] for older/simpler body types.
 */
fun buildRequestBody(tab: RequestTabState, effectiveBodyContent: String): RequestBody {
    return when (tab.bodyType) {
        BodyType.FORM_DATA -> {
            val rows = tab.formRows.filter { it.enabled }
            val formDataEntries = rows.map { com.reqlab.core.model.FormDataEntry(it.key, it.type, it.value, it.description, it.enabled) }
            val formEntries = rows.map { KeyValueEntry(it.key, it.value) }
            RequestBody(
                type = BodyType.FORM_DATA,
                formEntries = formEntries,
                formDataEntries = formDataEntries,
            )
        }
        BodyType.X_WWW_FORM_URLENCODED -> {
            val rows = tab.urlencodedRows.filter { it.enabled }
            val formEntries = if (rows.isNotEmpty()) {
                rows.map { KeyValueEntry(it.key, it.value) }
            } else {
                // Backward compat: parse from raw bodyContent
                effectiveBodyContent.split("&", "\n").mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx > 0) KeyValueEntry(part.substring(0, idx).trim(), part.substring(idx + 1).trim())
                    else null
                }
            }
            RequestBody(type = BodyType.X_WWW_FORM_URLENCODED, formEntries = formEntries)
        }
        else -> buildRequestBody(tab.bodyType, effectiveBodyContent)
    }
}

/**
 * Builds a [RequestBody] for the given body type and raw content string.
 * For FORM_DATA and X_WWW_FORM_URLENCODED, parses [content] as key=value pairs
 * separated by & or newlines and populates [RequestBody.formEntries].
 */
fun buildRequestBody(bodyType: BodyType, content: String): RequestBody {
    val rawContent = content.ifBlank { null }
    val binaryAttachment = parseBinaryAttachment(content)
    val formEntries = when (bodyType) {
        BodyType.FORM_DATA, BodyType.X_WWW_FORM_URLENCODED -> {
            content.split("&", "\n").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) KeyValueEntry(
                    key = part.substring(0, idx).trim(),
                    value = part.substring(idx + 1).trim(),
                ) else null
            }
        }
        else -> emptyList()
    }
    return RequestBody(
        type = bodyType,
        content = rawContent,
        formEntries = formEntries,
        binaryName = binaryAttachment?.first,
        binaryBytesBase64 = binaryAttachment?.second,
    )
}

private fun parseBinaryAttachment(content: String): Pair<String, String>? {
    if (!content.startsWith(BINARY_ATTACHMENT_PREFIX)) return null
    val separator = content.indexOf('\n')
    if (separator <= BINARY_ATTACHMENT_PREFIX.length || separator >= content.length - 1) return null
    val fileName = content.substring(BINARY_ATTACHMENT_PREFIX.length, separator).trim()
    val base64 = content.substring(separator + 1).trim()
    if (fileName.isBlank() || base64.isBlank()) return null
    return fileName to base64
}

/** Builds a cURL command string for the given tab, resolving {{vars}} from variable layers. */
fun buildCurlCommand(tab: RequestTabState, variableLayers: List<Map<String, String>> = emptyList()): String {
    fun resolve(s: String) = VariableResolver.resolve(s, variableLayers, removeUnresolved = true)

    val parts = mutableListOf("curl", "-X ${tab.method.name}")

    tab.headers
        .filter { it.enabled && it.key.isNotBlank() }
        .forEach { parts += "-H ${shellQuote("${resolve(it.key)}: ${resolve(it.value)}")}" }

    when (tab.authType) {
        AuthType.BEARER -> {
            val token = resolve(tab.authToken).trim()
            if (token.isNotEmpty()) parts += "-H ${shellQuote("Authorization: Bearer $token")}"
        }
        AuthType.BASIC -> {
            if (tab.authUsername.isNotBlank() || tab.authPassword.isNotBlank()) {
                parts += "-u ${shellQuote("${resolve(tab.authUsername)}:${resolve(tab.authPassword)}")}"
            }
        }
        AuthType.API_KEY -> {
            if (tab.authApiKey.isNotBlank() && tab.authApiValue.isNotBlank()) {
                parts += "-H ${shellQuote("${resolve(tab.authApiKey)}: ${resolve(tab.authApiValue)}")}"
            }
        }
        AuthType.JWT -> {
            val token = resolve(tab.authToken).trim()
            if (token.isNotEmpty()) parts += "-H ${shellQuote("Authorization: Bearer $token")}"
        }
        AuthType.OAUTH2, AuthType.NONE -> Unit
    }

    if (tab.bodyType != com.reqlab.core.model.BodyType.NONE && tab.bodyContent.isNotBlank()) {
        parts += "--data ${shellQuote(resolve(tab.bodyContent))}"
    }

    // Build URL with inline query params
    val resolvedUrl = buildUrlWithParams(resolve(tab.url), tab, variableLayers, removeUnresolved = true)
    parts += shellQuote(resolvedUrl)
    return parts.joinToString(" \\\n  ")
}

internal fun resolveUrlForLog(
    url: String,
    variableLayers: List<Map<String, String>>,
    requestScopedVars: Map<String, String> = emptyMap(),
): String {
    val layers = if (requestScopedVars.isNotEmpty()) {
        listOf(requestScopedVars) + variableLayers
    } else {
        variableLayers
    }
    return VariableResolver.resolve(url, layers, removeUnresolved = false)
}

/** cURL command with raw (unresolved) {{variables}} preserved – useful for sharing templates. */
fun buildCurlCommandRaw(tab: RequestTabState): String = buildCurlCommand(tab, emptyList())

/** Python `requests` snippet resolving {{vars}}. */
fun buildPythonCommand(tab: RequestTabState, variableLayers: List<Map<String, String>> = emptyList()): String {
    fun resolve(s: String) = VariableResolver.resolve(s, variableLayers, removeUnresolved = true)

    val sb = StringBuilder()
    sb.appendLine("import requests")
    sb.appendLine()

    val headers = buildHeaderMap(tab, variableLayers, removeUnresolved = true)
    if (headers.isNotEmpty()) {
        sb.appendLine("headers = {")
        headers.forEach { (k, v) -> sb.appendLine("    ${pyStr(k)}: ${pyStr(v)},") }
        sb.appendLine("}")
        sb.appendLine()
    }

    val url = buildUrlWithParams(resolve(tab.url), tab, variableLayers, removeUnresolved = true)
    val method = tab.method.name.uppercase()

    if (tab.bodyType != com.reqlab.core.model.BodyType.NONE && tab.bodyContent.isNotBlank()) {
        val body = resolve(tab.bodyContent)
        sb.appendLine("data = ${pyStr(body)}")
        sb.appendLine()
        sb.append("response = requests.${method.lowercase()}(${pyStr(url)}")
        if (headers.isNotEmpty()) sb.append(", headers=headers")
        sb.appendLine(", data=data)")
    } else {
        sb.append("response = requests.${method.lowercase()}(${pyStr(url)}")
        if (headers.isNotEmpty()) sb.append(", headers=headers")
        sb.appendLine(")")
    }
    sb.appendLine("print(response.status_code, response.text)")
    return sb.toString().trimEnd()
}

/** HTTPie CLI snippet resolving {{vars}}. */
fun buildHTTPieCommand(tab: RequestTabState, variableLayers: List<Map<String, String>> = emptyList()): String {
    fun resolve(s: String) = VariableResolver.resolve(s, variableLayers, removeUnresolved = true)

    val parts = mutableListOf("http", tab.method.name)
    val url = buildUrlWithParams(resolve(tab.url), tab, variableLayers, removeUnresolved = true)
    parts += shellQuote(url)

    buildHeaderMap(tab, variableLayers, removeUnresolved = true).forEach { (k, v) -> parts += shellQuote("$k:$v") }

    if (tab.bodyType != com.reqlab.core.model.BodyType.NONE && tab.bodyContent.isNotBlank()) {
        parts += "--raw"
        parts += shellQuote(resolve(tab.bodyContent))
    }

    return parts.joinToString(" \\\n  ")
}

/** PowerShell `Invoke-WebRequest` snippet resolving {{vars}}. */
fun buildPowerShellCommand(tab: RequestTabState, variableLayers: List<Map<String, String>> = emptyList()): String {
    fun resolve(s: String) = VariableResolver.resolve(s, variableLayers, removeUnresolved = true)

    val sb = StringBuilder()
    val url = buildUrlWithParams(resolve(tab.url), tab, variableLayers, removeUnresolved = true)
    val headers = buildHeaderMap(tab, variableLayers, removeUnresolved = true)

    if (headers.isNotEmpty()) {
        sb.appendLine("\$headers = @{")
        headers.forEach { (k, v) -> sb.appendLine("    '${k.replace("'", "''")}'='${v.replace("'", "''")}'") }
        sb.appendLine("}")
        sb.appendLine()
    }

    sb.append("Invoke-WebRequest -Uri '${url.replace("'", "''")}' -Method ${tab.method.name}")
    if (headers.isNotEmpty()) sb.append(" -Headers \$headers")

    if (tab.bodyType != com.reqlab.core.model.BodyType.NONE && tab.bodyContent.isNotBlank()) {
        val body = resolve(tab.bodyContent).replace("'", "''")
        sb.append(" -Body '$body'")
    }

    return sb.toString().trimEnd()
}

// ── Curl / format helpers ────────────────────────────────────────────

private fun buildUrlWithParams(
    resolvedBase: String,
    tab: RequestTabState,
    variableLayers: List<Map<String, String>>,
    removeUnresolved: Boolean = false,
): String {
    val enabledParams = tab.params.filter { it.enabled && it.key.isNotBlank() }
    if (enabledParams.isEmpty()) return resolvedBase
    val separator = if (resolvedBase.contains('?')) "&" else "?"
    val qs = enabledParams.joinToString("&") { p ->
        "${VariableResolver.resolve(p.key, variableLayers, removeUnresolved)}=${VariableResolver.resolve(p.value, variableLayers, removeUnresolved)}"
    }
    return "$resolvedBase$separator$qs"
}

private fun buildHeaderMap(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>>,
    removeUnresolved: Boolean = false,
): Map<String, String> {
    fun resolve(s: String) = VariableResolver.resolve(s, variableLayers, removeUnresolved)
    val map = linkedMapOf<String, String>()
    tab.headers.filter { it.enabled && it.key.isNotBlank() }
        .forEach { map[resolve(it.key)] = resolve(it.value) }
    when (tab.authType) {
        AuthType.BEARER, AuthType.JWT -> {
            val token = resolve(tab.authToken).trim()
            if (token.isNotEmpty()) map["Authorization"] = "Bearer $token"
        }
        AuthType.API_KEY -> {
            if (tab.authApiKey.isNotBlank() && tab.authApiValue.isNotBlank())
                map[resolve(tab.authApiKey)] = resolve(tab.authApiValue)
        }
        else -> Unit
    }
    return map
}

private fun pyStr(s: String): String = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""

internal fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
