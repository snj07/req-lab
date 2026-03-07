package com.reqlab.core.network

import org.koin.core.module.Module
import org.koin.dsl.module

fun networkModule(
    retryPolicy: RetryPolicy = RetryPolicy(),
    interceptors: List<NetworkInterceptor> = emptyList(),
    logger: NetworkLogger = NoOpNetworkLogger
): Module = module {
    single<ApiClient> {
        KtorApiClient(
            retryPolicy = retryPolicy,
            interceptors = interceptors,
            logger = logger
        )
    }
}
