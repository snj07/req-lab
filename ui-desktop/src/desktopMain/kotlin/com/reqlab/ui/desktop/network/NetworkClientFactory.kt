package com.reqlab.ui.desktop.network

import com.reqlab.core.network.KtorApiClient
import com.reqlab.core.network.NetworkLogger
import com.reqlab.core.network.RetryPolicy
import com.reqlab.ui.desktop.state.AppSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds a [KtorApiClient] fully configured from the current [AppSettings].
 *
 * Call this every time a request is sent so that the latest settings are always used.
 * Ktor client construction is lightweight on the CIO engine.
 */
object NetworkClientFactory {

    fun build(
        settings: AppSettings,
        logger: NetworkLogger,
        retryPolicy: RetryPolicy = RetryPolicy(),
    ): KtorApiClient {
        val timeoutMs = settings.requestTimeoutSec.toLong() * 1_000L

        val httpClient = HttpClient(CIO) {
            // ── timeout ─────────────────────────────────────────────────
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMs
                connectTimeoutMillis = minOf(timeoutMs, 10_000L)
                socketTimeoutMillis  = timeoutMs
            }

            // ── redirect ─────────────────────────────────────────────────
            if (settings.followRedirects) {
                install(HttpRedirect) {
                    checkHttpMethod = false
                    allowHttpsDowngrade = true
                }
            }

            // ── content negotiation ─────────────────────────────────────
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                    explicitNulls = false
                })
            }

            // ── proxy ─────────────────────────────────────────────────────
            engine {
                if (settings.proxyEnabled) {
                    val proxyUrl = settings.httpProxy.trim().ifBlank { settings.httpsProxy.trim() }
                    if (proxyUrl.isNotBlank()) {
                        proxy = runCatching { ProxyBuilder.http(Url(proxyUrl)) }.getOrNull()
                    }
                }
            }

            expectSuccess = false
        }

        return KtorApiClient(httpClient = httpClient, logger = logger, retryPolicy = retryPolicy)
    }
}
