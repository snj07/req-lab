package com.reqlab.ui.shared.network

import com.reqlab.core.network.KtorApiClient
import com.reqlab.core.network.NetworkLogger
import com.reqlab.core.network.RetryPolicy
import com.reqlab.ui.shared.state.AppSettings

/**
 * Builds a [KtorApiClient] fully configured from the current [AppSettings].
 *
 * Platform actuals provide the correct Ktor engine (CIO on Desktop, Js on Web).
 */
expect object NetworkClientFactory {

    fun build(
        settings: AppSettings,
        logger: NetworkLogger,
        retryPolicy: RetryPolicy = RetryPolicy(),
    ): KtorApiClient
}
