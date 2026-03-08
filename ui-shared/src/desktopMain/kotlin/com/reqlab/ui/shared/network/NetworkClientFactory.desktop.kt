package com.reqlab.ui.shared.network

import com.reqlab.core.network.KtorApiClient
import com.reqlab.core.network.NetworkLogger
import com.reqlab.core.network.RetryPolicy
import com.reqlab.ui.shared.state.AppSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual object NetworkClientFactory {

    actual fun build(
        settings: AppSettings,
        logger: NetworkLogger,
        retryPolicy: RetryPolicy,
    ): KtorApiClient {
        val timeoutMs = settings.requestTimeoutSec.toLong() * 1_000L

        val httpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMs
                connectTimeoutMillis = minOf(timeoutMs, 10_000L)
                socketTimeoutMillis  = timeoutMs
            }

            if (settings.followRedirects) {
                install(HttpRedirect) {
                    checkHttpMethod = false
                    allowHttpsDowngrade = true
                }
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                    explicitNulls = false
                })
            }

            install(WebSockets)

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
