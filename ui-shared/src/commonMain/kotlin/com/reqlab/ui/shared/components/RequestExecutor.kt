package com.reqlab.ui.shared.components

import com.reqlab.core.model.AuthConfig
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.NetworkEvent
import com.reqlab.core.network.NetworkLogger
import com.reqlab.core.network.RetryPolicy
import com.reqlab.ui.shared.network.NetworkClientFactory
import com.reqlab.ui.shared.persistence.TabsRepository
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.LogLevel
import com.reqlab.ui.shared.state.RequestTabState
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
fun sendRequest(scope: CoroutineScope, state: AppState, tab: RequestTabState) {
    if (tab.url.isBlank()) {
        state.log("URL is empty", LogLevel.WARNING)
        return
    }

    scope.launch {
        tab.isLoading = true
        tab.response  = null
        tab.lastError = null
        state.log("→ ${tab.method} ${tab.url}")

        try {
            val request = RequestDefinition(
                id = tab.id,
                name = tab.name,
                method = tab.method,
                url = tab.url,
                queryParams = tab.params.filter { it.enabled }.map { KeyValueEntry(it.key, it.value) },
                headers = tab.headers.filter { it.enabled }.map { KeyValueEntry(it.key, it.value) },
                auth = buildAuthConfig(tab),
                body = RequestBody(type = tab.bodyType, content = tab.bodyContent.ifBlank { null }),
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

            val client = NetworkClientFactory.build(
                settings = state.settings,
                logger = logger,
                retryPolicy = RetryPolicy(
                    maxAttempts = tab.retryCount.coerceAtLeast(1),
                    baseDelayMs = tab.retryDelayMs.coerceAtLeast(0L),
                    maxDelayMs  = (tab.retryDelayMs.coerceAtLeast(0L) * 10L)
                        .coerceAtLeast(tab.retryDelayMs),
                ),
            )

            client.execute(request, state.activeVariableLayers()).collect { event ->
                when (event) {
                    is NetworkEvent.Started -> {
                        state.log("Request started", LogLevel.INFO)
                    }
                    is NetworkEvent.RetryScheduled -> {
                        state.log(
                            "Retry #${event.attempt} in ${event.delayMs}ms – ${event.reason}",
                            LogLevel.WARNING,
                        )
                    }
                    is NetworkEvent.Success -> {
                        tab.response  = event.response
                        tab.lastError = null
                        state.log(
                            "← ${event.response.statusCode} ${event.response.statusText}" +
                                "  (${event.response.metrics.responseTimeMs}ms," +
                                " ${event.response.metrics.responseSizeBytes}B)",
                            LogLevel.SUCCESS,
                        )
                    }
                    is NetworkEvent.Failure -> {
                        tab.lastError = event.error.message ?: "Unknown error"
                        state.log("✗ ${event.error.message}", LogLevel.ERROR)
                    }
                }
            }
        } catch (e: Exception) {
            tab.lastError = e.message ?: "Unknown error"
            state.log("✗ ${e.message ?: "Unknown error"}", LogLevel.ERROR)
        } finally {
            tab.isLoading = false
        }
    }
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
        withContext(ioDispatcher) { TabsRepository.save(state) }
        tab.markSaved()
        state.log("✓ Request saved: ${tab.name}", LogLevel.SUCCESS)
        onSaved?.invoke()
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

/** Builds a cURL command string for the given tab. */
fun buildCurlCommand(tab: RequestTabState): String {
    val parts = mutableListOf("curl", "-X ${tab.method.name}")

    tab.headers
        .filter { it.enabled && it.key.isNotBlank() }
        .forEach { parts += "-H ${shellQuote("${it.key}: ${it.value}")}" }

    when (tab.authType) {
        AuthType.BEARER -> {
            val token = tab.authToken.trim()
            if (token.isNotEmpty()) parts += "-H ${shellQuote("Authorization: Bearer $token")}"
        }
        AuthType.BASIC -> {
            if (tab.authUsername.isNotBlank() || tab.authPassword.isNotBlank()) {
                parts += "-u ${shellQuote("${tab.authUsername}:${tab.authPassword}")}"
            }
        }
        AuthType.API_KEY -> {
            if (tab.authApiKey.isNotBlank() && tab.authApiValue.isNotBlank()) {
                parts += "-H ${shellQuote("${tab.authApiKey}: ${tab.authApiValue}")}"
            }
        }
        AuthType.JWT -> {
            val token = tab.authToken.trim()
            if (token.isNotEmpty()) parts += "-H ${shellQuote("Authorization: Bearer $token")}"
        }
        AuthType.OAUTH2, AuthType.NONE -> Unit
    }

    if (tab.bodyType != com.reqlab.core.model.BodyType.NONE && tab.bodyContent.isNotBlank()) {
        parts += "--data ${shellQuote(tab.bodyContent)}"
    }

    parts += shellQuote(tab.url)
    return parts.joinToString(" \\\n  ")
}

private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
