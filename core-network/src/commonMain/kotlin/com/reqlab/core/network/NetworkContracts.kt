package com.reqlab.core.network

import com.reqlab.core.model.ResponseDefinition
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse

sealed interface NetworkEvent {
    data class Started(val requestId: String, val timestampEpochMillis: Long) : NetworkEvent
    data class RetryScheduled(val attempt: Int, val delayMs: Long, val reason: String) : NetworkEvent
    data class Success(val response: ResponseDefinition) : NetworkEvent
    data class Failure(val error: NetworkError) : NetworkEvent
}

data class NetworkError(
    val requestId: String,
    val message: String,
    val cause: Throwable? = null,
    val isRetryExhausted: Boolean = false
)

interface NetworkLogger {
    fun debug(message: String)
    fun info(message: String)
    fun error(message: String, throwable: Throwable? = null)
}

object NoOpNetworkLogger : NetworkLogger {
    override fun debug(message: String) = Unit
    override fun info(message: String) = Unit
    override fun error(message: String, throwable: Throwable?) = Unit
}

interface NetworkInterceptor {
    suspend fun onRequest(builder: HttpRequestBuilder) = Unit
    suspend fun onResponse(response: HttpResponse, durationMs: Long) = Unit
    suspend fun onFailure(throwable: Throwable, attempt: Int) = Unit
}
