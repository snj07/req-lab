package com.reqlab.ui.shared.components

import com.reqlab.core.model.AuthConfig
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.json.Json5
import com.reqlab.core.model.FormDataEntry
import com.reqlab.core.model.GraphQlBody
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.ApiClient
import com.reqlab.core.network.LlmTextAssembler
import com.reqlab.core.network.NetworkEvent
import com.reqlab.core.network.NetworkLogger
import com.reqlab.core.network.NoOpNetworkLogger
import com.reqlab.core.network.RetryPolicy
import com.reqlab.core.network.VariableResolver
import com.reqlab.core.network.encodeGraphQlEnvelope
import com.reqlab.core.network.encodeBase64
import com.reqlab.core.scripting.ReqLabScriptEngine
import com.reqlab.core.scripting.ScriptContext
import com.reqlab.core.scripting.SendRequestResult
import com.reqlab.core.scripting.SendRequestSpec
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
private const val BINARY_ATTACHMENT_PREFIX = "reqlab-binary:"
private const val MAX_SCRIPT_CHAIN_DEPTH = 16

/**
 * Executes a sub-HTTP request from a [reqlab.sendRequest()] script call.
 * Uses [client] (built from the current AppSettings) to make the real call.
 */
private suspend fun executeSubRequest(client: ApiClient, spec: SendRequestSpec): SendRequestResult {
    val requestDef = RequestDefinition(
        id = "sr-${spec.url.hashCode()}",
        name = "sendRequest",
        method = HttpMethodType.entries.firstOrNull { it.name == spec.method } ?: HttpMethodType.GET,
        url = spec.url,
        headers = spec.headers.map { (k, v) -> KeyValueEntry(k, v) },
        body = spec.body?.let { RequestBody(BodyType.JSON, content = it) } ?: RequestBody(),
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )
    var result = SendRequestResult(statusCode = 0, statusText = "No response received")
    client.execute(requestDef).collect { event ->
        when (event) {
            is NetworkEvent.Success -> result = SendRequestResult(
                statusCode = event.response.statusCode,
                statusText = event.response.statusText,
                body = event.response.bodyText,
                headers = event.response.headers.associate { it.key to it.value },
                elapsedMs = event.response.metrics.responseTimeMs,
            )
            is NetworkEvent.Failure -> result = SendRequestResult(
                statusCode = 0,
                statusText = "Error: ${event.error.message}",
            )
            else -> {}
        }
    }
    return result
}

fun sendRequest(scope: CoroutineScope, state: AppState, tab: RequestTabState) {
    sendRequestInternal(scope, state, tab, chainDepth = 0)
}

private fun sendRequestInternal(scope: CoroutineScope, state: AppState, tab: RequestTabState, chainDepth: Int) {
    if (tab.url.isBlank()) {
        state.log("URL is empty", LogLevel.WARNING)
        return
    }

    // H-1: Cancel any existing in-flight request before starting a new one.
    // This prevents concurrent requests racing to update the same tab state.
    tab.currentJob?.cancel()

    val job = scope.launch {
        // Create a fresh script engine per request, wired to the current settings
        // so reqlab.sendRequest() sub-calls inherit proxy/timeout configuration.
        val subReqClient = NetworkClientFactory.build(state.settings, NoOpNetworkLogger, RetryPolicy(maxAttempts = 1))
        val scriptEngine = ReqLabScriptEngine(
            sendRequestExecutor = { spec -> executeSubRequest(subReqClient, spec) },
        )
        tab.isLoading = true
        tab.response  = null
        tab.lastError = null
        tab.streamChunks.clear()
        tab.liveStreamText = ""
        state.testResults.clear()
        // Strip embedded query string from the URL: tab.params is the single source
        // of truth for query parameters (syncUrlFromParams embeds them for display,
        // but passing both to KtorApiClient would cause each param to be appended
        // twice — once from the parsed URL and once from effectiveQueryParams).
        // Fragment is client-only and must not be glued onto a query value.
        val urlParts = splitRequestUrl(tab.url.trim())
        var effectiveUrl = urlParts.base
        var effectiveMethod = tab.method
        val effectiveHeaders = enabledHeadersForSend(tab).toMutableList()
        // Query parameters are an ordered multi-map: repeated keys such as
        // `tag=kotlin&tag=ktor` are distinct values and must reach the wire.
        // Do not use associate() here because it silently keeps only the last
        // row for each key.
        val effectiveQueryParams = tab.params
            .filter { it.enabled }
            .map { KeyValueEntry(it.key, it.value, secret = it.secret) }
            .toMutableList()
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
                requestHeaders = effectiveHeaders.associate { it.key to it.value },
                // The current script API exposes a map/get-by-name view. Keep its
                // historical last-value behavior without collapsing the wire list.
                requestQueryParams = effectiveQueryParams.associate { it.key to it.value },
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
            if (preResult.executionSkipRequest) {
                state.log("↷ Request skipped by script", LogLevel.INFO)
                tab.isLoading = false
                tab.currentJob = null
                return@launch
            }
            if (preResult.requestMutations.url != null) {
                val scriptParts = splitRequestUrl(preResult.requestMutations.url!!)
                effectiveUrl = scriptParts.base
                effectiveQueryParams.clear()
                effectiveQueryParams.addAll(
                    parseQueryPairs(scriptParts.query).map { KeyValueEntry(it.first, it.second) },
                )
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
                preResult.requestMutations.headers.forEach { (key, value) ->
                    val firstIndex = effectiveHeaders.indexOfFirst { it.key.equals(key, ignoreCase = true) }
                    effectiveHeaders.removeAll { it.key.equals(key, ignoreCase = true) }
                    val replacement = KeyValueEntry(key, value)
                    if (firstIndex >= 0) {
                        effectiveHeaders.add(firstIndex, replacement)
                    } else {
                        effectiveHeaders.add(replacement)
                    }
                }
            }
            if (preResult.requestMutations.queryParams.isNotEmpty()) {
                preResult.requestMutations.queryParams.forEach { (key, value) ->
                    // setQueryParam is an upsert-by-name operation. If the original
                    // request contained repeated values for this key, replace the
                    // group with the explicitly scripted value.
                    val firstIndex = effectiveQueryParams.indexOfFirst { it.key == key }
                    effectiveQueryParams.removeAll { it.key == key }
                    val replacement = KeyValueEntry(key, value)
                    if (firstIndex >= 0) {
                        effectiveQueryParams.add(firstIndex, replacement)
                    } else {
                        effectiveQueryParams.add(replacement)
                    }
                }
            }
        }

        val resolvedBaseForLog = resolveUrlForLog(
            url = effectiveUrl,
            variableLayers = state.activeVariableLayers(),
            requestScopedVars = requestScopedScriptVars,
        )
        val effectiveUrlForLog = if (effectiveQueryParams.isNotEmpty()) {
            val qs = encodeOrderedQuery(effectiveQueryParams.map { it.key to it.value }, preserveTemplates = true)
            "$resolvedBaseForLog?$qs"
        } else {
            resolvedBaseForLog
        }
        state.logNetworkEvent("→ $effectiveMethod $effectiveUrlForLog")

        try {
            val request = prepareTabRequest(
                tab = tab,
                method = effectiveMethod,
                url = effectiveUrl,
                queryParams = effectiveQueryParams,
                headers = effectiveHeaders,
                bodyContent = effectiveBodyContent,
            ).toRequestDefinition()

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
                    is NetworkEvent.Chunk -> {
                        tab.streamChunks.add(event.raw)
                        tab.liveStreamText = LlmTextAssembler.assemble(tab.streamChunks.toList())
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
                                requestHeaders = effectiveHeaders.associate { it.key to it.value },
                                requestQueryParams = effectiveQueryParams.associate { it.key to it.value },
                                requestBody = effectiveBodyContent,
                                streamEvents = resp.streamEvents,
                                assembledText = resp.assembledText ?: tab.liveStreamText.ifBlank { null },
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
                            if (testResult.executionSetNextRequestCalled) {
                                val token = testResult.executionNextRequest
                                if (token == null) {
                                    state.log("↷ Script setNextRequest(null): stopping chain", LogLevel.INFO)
                                } else {
                                    if (chainDepth >= MAX_SCRIPT_CHAIN_DEPTH) {
                                        state.log("⚠ Script chain limit reached ($MAX_SCRIPT_CHAIN_DEPTH)", LogLevel.WARNING)
                                    } else {
                                        val target = state.resolveScriptRequestTarget(
                                            token = token,
                                            preferredCollectionId = tab.collectionId,
                                        )
                                        if (target == null) {
                                            state.log("⚠ setNextRequest target not found or ambiguous: $token", LogLevel.WARNING)
                                        } else {
                                            state.log("↷ setNextRequest → ${target.name}", LogLevel.INFO)
                                            state.openRequest(
                                                requestId = target.requestId,
                                                name = target.name,
                                                method = target.method,
                                                url = target.url,
                                            )
                                            val nextTab = state.activeTab
                                            if (nextTab != null) {
                                                sendRequestInternal(scope, state, nextTab, chainDepth + 1)
                                            }
                                        }
                                    }
                                }
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
        val checkpoint = tab.captureSaveCheckpoint()
        val collectionSynced = tab.collectionName == null || state.syncTabToCollectionNode(tab)
        // The clean marker must be part of the persisted tab snapshot. Previously
        // TabsRepository.save ran first, so an immediate reload restored a dirty tab.
        tab.markSaved()
        val tabsSaved = withContext(ioDispatcher) { TabsRepository.save(state) }
        val workspaceSaved = if (tab.collectionName != null && collectionSynced) {
            withContext(ioDispatcher) { WorkspaceRepository.save(state) }
        } else {
            collectionSynced
        }
        val ok = tabsSaved && workspaceSaved
        if (ok) {
            state.log("✓ Request saved: ${tab.name}", LogLevel.SUCCESS)
            onSaved?.invoke()
        } else {
            tab.restoreSaveCheckpoint(checkpoint.copy(isDirty = true))
            // Best effort: if tabs were written before workspace failed, put the
            // original dirty marker back on disk as well as in memory.
            withContext(ioDispatcher) { TabsRepository.save(state) }
            state.log("✗ Failed to save request: ${tab.name}", LogLevel.ERROR)
            state.showError(
                title = "Save failed",
                message = "The request could not be saved. Please try again.",
            )
        }
    }
}

// ── Internal helpers ────────────────────────────────────────────

/**
 * Canonical tab-to-request projection shared by Send and every Copy As format.
 * Script mutations are supplied as overrides by Send; Copy uses the tab defaults.
 */
internal data class PreparedTabRequest(
    val id: String,
    val name: String,
    val method: HttpMethodType,
    val url: String,
    val fragment: String,
    val queryParams: List<KeyValueEntry>,
    val headers: List<KeyValueEntry>,
    val auth: AuthConfig,
    val body: RequestBody,
) {
    fun toRequestDefinition(): RequestDefinition = RequestDefinition(
        id = id,
        name = name,
        method = method,
        url = url,
        queryParams = queryParams.filter { it.enabled && it.key.isNotBlank() },
        headers = headers.filter { it.enabled && it.key.isNotBlank() },
        auth = auth,
        body = body,
        createdAtEpochMillis = currentTimeMillis(),
        updatedAtEpochMillis = currentTimeMillis(),
    )
}

internal fun prepareTabRequest(
    tab: RequestTabState,
    method: HttpMethodType = tab.method,
    url: String = tab.url.trim(),
    queryParams: List<KeyValueEntry> = tab.params
        .filter { it.enabled }
        .map { KeyValueEntry(it.key, it.value, secret = it.secret) },
    headers: List<KeyValueEntry> = enabledHeadersForSend(tab),
    bodyContent: String = tab.bodyContent,
): PreparedTabRequest {
    val parts = splitRequestUrl(url)
    return PreparedTabRequest(
        id = tab.id,
        name = tab.name,
        method = method,
        url = parts.base,
        fragment = parts.fragment,
        queryParams = queryParams,
        headers = headers,
        auth = buildAuthConfig(tab),
        body = buildRequestBody(tab, bodyContent),
    )
}

fun buildAuthConfig(tab: RequestTabState): AuthConfig {
    val params = when (tab.authType) {
        AuthType.BASIC   -> mapOf("username" to tab.authUsername, "password" to tab.authPassword)
        AuthType.BEARER  -> mapOf("token" to tab.authToken)
        AuthType.JWT     -> mapOf("token" to tab.authToken)
        AuthType.API_KEY -> mapOf("key" to tab.authApiKey, "value" to tab.authApiValue)
        else             -> emptyMap()
    }
    return AuthConfig(
        type = tab.authType,
        params = params,
        placement = if (tab.authType == AuthType.API_KEY) {
            com.reqlab.ui.shared.state.normalizeApiKeyPlacement(tab.authApiPlacement)
        } else null,
    )
}

/**
 * Builds a [RequestBody] from [tab] state, using structured form rows when available.
 * Falls back to parsing [effectiveBodyContent] for older/simpler body types.
 */
fun buildRequestBody(tab: RequestTabState, effectiveBodyContent: String): RequestBody {
    return when (tab.bodyType) {
        BodyType.FORM_DATA -> {
            val rows = tab.formRows.filter { it.enabled }
            val formDataEntries = rows.map {
                com.reqlab.core.model.FormDataEntry(it.key, it.type, it.value, it.description, it.enabled, it.bytesBase64)
            }
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
        BodyType.GRAPHQL -> RequestBody(type = BodyType.GRAPHQL, graphQl = GraphQlBody(query = effectiveBodyContent))
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
        content = if (bodyType == BodyType.GRAPHQL) null else rawContent,
        formEntries = formEntries,
        graphQl = if (bodyType == BodyType.GRAPHQL) GraphQlBody(query = content) else null,
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
fun buildCurlCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
): String {
    val request = prepareTabRequest(tab)
    val parts = mutableListOf("curl", "-X ${request.method.name}")

    preparedHeaderList(request, variableLayers).forEach { (key, value) ->
        parts += "-H ${shellQuote("$key: $value")}"
    }
    val body = prepareCopyBody(request.body, variableLayers, allowJson5)
    if (body.multipart != null) {
        body.multipart.forEach { (key, value) -> parts += "--form-string ${shellQuote("$key=$value")}" }
    } else if (body.content != null) {
        parts += "--data-binary ${shellQuote(body.content)}"
    }

    // Build URL with inline query params
    val resolvedUrl = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    parts += shellQuote(resolvedUrl)
    return parts.joinToString(" \\\n  ")
}

internal fun enabledHeadersForSend(tab: RequestTabState): List<KeyValueEntry> =
    tab.headers
        .filter { it.enabled && it.key.isNotBlank() }
        .map { KeyValueEntry(it.key, it.value, secret = it.secret) }

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
fun buildCurlCommandRaw(tab: RequestTabState, allowJson5: Boolean = true): String =
    buildCurlCommand(tab, emptyList(), allowJson5)

/** Python `requests` snippet resolving {{vars}}. */
fun buildPythonCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
): String {
    val request = prepareTabRequest(tab)
    val sb = StringBuilder()
    sb.appendLine("import requests")
    sb.appendLine()

    val headers = preparedHeaderList(request, variableLayers)
    if (headers.isNotEmpty()) {
        sb.appendLine("headers = {")
        headers.forEach { (k, v) -> sb.appendLine("    ${pyStr(k)}: ${pyStr(v)},") }
        sb.appendLine("}")
        sb.appendLine()
    }

    val url = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    val method = request.method.name.uppercase()

    val body = prepareCopyBody(request.body, variableLayers, allowJson5)
    if (body.multipart != null) {
        sb.appendLine("files = [")
        body.multipart.forEach { (key, value) ->
            sb.appendLine("    (${pyStr(key)}, (None, ${pyStr(value)})),")
        }
        sb.appendLine("]")
        sb.appendLine()
    } else if (body.content != null) {
        sb.appendLine("data = ${pyStr(body.content)}")
        sb.appendLine()
    }
    sb.append("response = requests.request(${pyStr(method)}, ${pyStr(url)}")
    if (headers.isNotEmpty()) sb.append(", headers=headers")
    if (body.multipart != null) sb.append(", files=files")
    else if (body.content != null) sb.append(", data=data")
    sb.appendLine(")")
    sb.appendLine("print(response.status_code, response.text)")
    return sb.toString().trimEnd()
}

/** HTTPie CLI snippet resolving {{vars}}. */
fun buildHTTPieCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
): String {
    val request = prepareTabRequest(tab)
    val parts = mutableListOf("http", tab.method.name)
    val url = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    parts += shellQuote(url)

    preparedHeaderList(request, variableLayers, removeUnresolved = true).forEach { (k, v) -> parts += shellQuote("$k:$v") }

    if (request.body.type != com.reqlab.core.model.BodyType.NONE && tab.bodyContent.isNotBlank()) {
        parts += "--raw"
        parts += shellQuote(prepareCopyBody(request.body, variableLayers, allowJson5).content.orEmpty())
    }

    return parts.joinToString(" \\\n  ")
}

/** PowerShell `Invoke-WebRequest` snippet resolving {{vars}}. */
fun buildPowerShellCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
): String {
    val request = prepareTabRequest(tab)
    val sb = StringBuilder()
    val url = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    val headers = preparedHeaderList(request, variableLayers)

    if (headers.isNotEmpty()) {
        sb.appendLine("\$headers = @{")
        headers.forEach { (k, v) -> sb.appendLine("    '${k.replace("'", "''")}'='${v.replace("'", "''")}'") }
        sb.appendLine("}")
        sb.appendLine()
    }

    sb.append("Invoke-WebRequest -Uri '${url.replace("'", "''")}' -Method ${request.method.name}")
    if (headers.isNotEmpty()) sb.append(" -Headers \$headers")

    val body = prepareCopyBody(request.body, variableLayers, allowJson5)
    if (body.multipart != null) {
        val fields = body.multipart.joinToString("; ") { (key, value) ->
            "'${key.replace("'", "''")}'='${value.replace("'", "''")}'"
        }
        sb.append(" -Form @{$fields}")
    } else if (body.content != null) {
        sb.append(" -Body '${body.content.replace("'", "''")}'")
    }

    return sb.toString().trimEnd()
}

// ── Curl / format helpers ────────────────────────────────────────────

private fun buildUrlWithParams(
    request: PreparedTabRequest,
    variableLayers: List<Map<String, String>>,
    removeUnresolved: Boolean = false,
): String {
    val queryEntries = request.queryParams.filter { it.enabled && it.key.isNotBlank() }.map { p ->
        VariableResolver.resolve(p.key, variableLayers, removeUnresolved) to
            VariableResolver.resolve(p.value, variableLayers, removeUnresolved)
    }.toMutableList()
    val auth = request.auth
    if (auth.type == AuthType.API_KEY && effectiveApiKeyPlacement(auth) == "query") {
        val key = VariableResolver.resolve(auth.params["key"].orEmpty(), variableLayers, removeUnresolved)
        if (key.isNotBlank()) {
            queryEntries += key to VariableResolver.resolve(
                auth.params["value"].orEmpty(), variableLayers, removeUnresolved,
            )
        }
    }
    // Strip any embedded query string from resolvedBase: tab.params is the single
    // source of truth for query parameters (kept in sync by syncParamsFromUrl /
    // syncUrlFromParams). Appending to a URL that already contains those params
    // would duplicate every parameter in the final request URL.
    val qs = encodeOrderedQuery(queryEntries, preserveTemplates = !removeUnresolved)
    val resolvedBase = VariableResolver.resolve(request.url, variableLayers, removeUnresolved)
    val resolvedFragment = VariableResolver.resolve(request.fragment, variableLayers, removeUnresolved)
    return joinRequestUrl(resolvedBase, qs, resolvedFragment)
}

private data class PreparedCopyBody(
    val content: String? = null,
    val multipart: List<Pair<String, String>>? = null,
)

private fun prepareCopyBody(
    body: RequestBody,
    variableLayers: List<Map<String, String>>,
    allowJson5: Boolean,
): PreparedCopyBody {
    fun resolve(value: String) = VariableResolver.resolve(value, variableLayers, removeUnresolved = true)
    return when (body.type) {
        BodyType.NONE -> PreparedCopyBody()
        BodyType.BINARY -> error("Embedded binary has no reusable file path")
        BodyType.FORM_DATA -> {
            require(body.formDataEntries.none { it.type == com.reqlab.core.model.FormEntryType.FILE }) {
                "Embedded multipart file has no reusable file path"
            }
            val entries = if (body.formDataEntries.isNotEmpty()) {
                body.formDataEntries.map { it.key to resolve(it.value) }
            } else body.formEntries.map { it.key to resolve(it.value) }
            PreparedCopyBody(multipart = entries)
        }
        BodyType.X_WWW_FORM_URLENCODED -> PreparedCopyBody(content =
            encodeOrderedQuery(body.formEntries.filter { it.enabled }.map { it.key to resolve(it.value) }))
        BodyType.GRAPHQL -> PreparedCopyBody(content =
            encodeGraphQlEnvelope(body.graphQl ?: GraphQlBody(), variableLayers, allowJson5))
        else -> PreparedCopyBody(content = body.content?.let { content ->
            if (allowJson5 && body.type == BodyType.JSON && content.isNotBlank()) {
                Json5.toWireJson(resolve(content)).getOrDefault(resolve(content))
            } else resolve(content)
        })
    }
}

internal fun copyHeaderList(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>>,
    omitMultipartContentType: Boolean = true,
): List<Pair<String, String>> = preparedHeaderList(
    prepareTabRequest(tab), variableLayers, removeUnresolved = true,
    omitMultipartContentType = omitMultipartContentType,
)

private fun preparedHeaderList(
    request: PreparedTabRequest,
    variableLayers: List<Map<String, String>>,
    removeUnresolved: Boolean = true,
    omitMultipartContentType: Boolean = true,
): List<Pair<String, String>> {
    fun resolve(s: String) = VariableResolver.resolve(s, variableLayers, removeUnresolved)
    val list = mutableListOf<Pair<String, String>>()
    request.headers.filter { it.enabled && it.key.isNotBlank() }
        .forEach { list += resolve(it.key) to resolve(it.value) }
    val auth = request.auth
    when (auth.type) {
        AuthType.BASIC -> {
            val encoded = "${resolve(auth.params["username"].orEmpty())}:${resolve(auth.params["password"].orEmpty())}"
                .encodeToByteArray().encodeBase64()
            list += "Authorization" to "Basic $encoded"
        }
        AuthType.BEARER, AuthType.JWT -> {
            val token = resolve(auth.params["token"].orEmpty()).trim()
            if (token.isNotEmpty()) list += "Authorization" to "Bearer $token"
        }
        AuthType.API_KEY -> {
            if (effectiveApiKeyPlacement(auth) != "query") {
                val key = resolve(auth.params["key"].orEmpty())
                if (key.isNotBlank()) list += key to resolve(auth.params["value"].orEmpty())
            }
        }
        else -> Unit
    }
    return list.filterNot { (key, _) ->
        omitMultipartContentType && request.body.type == BodyType.FORM_DATA &&
            key.equals("Content-Type", ignoreCase = true)
    }
}

internal fun effectiveApiKeyPlacement(auth: AuthConfig): String =
    com.reqlab.ui.shared.state.normalizeApiKeyPlacement(auth.placement ?: auth.params["placement"])

private fun pyStr(s: String): String = "\"" + s
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t") + "\""

internal fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
