package com.reqlab.core.network

data class RetryPolicy(
    val maxAttempts: Int = 1,
    val baseDelayMs: Long = 250,
    val maxDelayMs: Long = 2_500,
    val retryOnStatusCodes: Set<Int> = setOf(408, 429, 500, 502, 503, 504)
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(baseDelayMs >= 0) { "baseDelayMs must be non-negative" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be >= baseDelayMs" }
    }

    fun delayForAttempt(attempt: Int): Long {
        val exponential = baseDelayMs * (1L shl (attempt - 1).coerceAtMost(20))
        return exponential.coerceAtMost(maxDelayMs)
    }
}
