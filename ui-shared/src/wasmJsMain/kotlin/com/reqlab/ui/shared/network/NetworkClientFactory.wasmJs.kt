package com.reqlab.ui.shared.network

import com.reqlab.core.network.KtorApiClient
import com.reqlab.core.network.NetworkLogger
import com.reqlab.core.network.RetryPolicy
import com.reqlab.ui.shared.state.AppSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual object NetworkClientFactory {

    actual fun build(
        settings: AppSettings,
        logger: NetworkLogger,
        retryPolicy: RetryPolicy,
    ): KtorApiClient {
        val timeoutMs = settings.requestTimeoutSec.toLong() * 1_000L

        val httpClient = HttpClient(Js) {
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMs
            }

            if (settings.followRedirects) {
                install(HttpRedirect) { checkHttpMethod = false }
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

            expectSuccess = false
        }

        return KtorApiClient(httpClient = httpClient, logger = logger, retryPolicy = retryPolicy)
    }
}
