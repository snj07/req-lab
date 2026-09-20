package com.reqlab.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryPolicyTest {

    private val policy = RetryPolicy(maxAttempts = 3)

    @Test
    fun client_construction_errors_are_not_retryable() {
        assertFalse(policy.isRetryable(IllegalArgumentException("invalid url")))
        assertFalse(policy.isRetryable(object : Exception("Fail to parse URL: ::::") {}))
    }

    @Test
    fun transport_failures_are_retryable() {
        assertTrue(policy.isRetryable(IllegalStateException("timeout-like failure")))
        assertTrue(policy.isRetryable(RuntimeException("connection reset")))
    }
}
