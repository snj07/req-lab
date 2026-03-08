package com.reqlab.ui.shared.network

import com.reqlab.core.network.NoOpNetworkLogger
import com.reqlab.ui.shared.state.AppSettings
import org.junit.Test
import kotlin.test.assertNotNull

/**
 * Unit tests for [NetworkClientFactory].
 *
 * These tests verify that the factory constructs a [com.reqlab.core.network.KtorApiClient]
 * without throwing for the various settings combinations that matter in production.
 */
class NetworkClientFactoryTest {

    @Test
    fun build_returns_non_null_client_with_default_settings() {
        val settings = AppSettings()
        val client = NetworkClientFactory.build(settings, NoOpNetworkLogger)
        assertNotNull(client)
    }

    @Test
    fun build_with_follow_redirects_disabled_does_not_throw() {
        val settings = AppSettings().apply { followRedirects = false }
        val client = NetworkClientFactory.build(settings, NoOpNetworkLogger)
        assertNotNull(client)
    }

    @Test
    fun build_with_follow_redirects_enabled_does_not_throw() {
        val settings = AppSettings().apply { followRedirects = true }
        val client = NetworkClientFactory.build(settings, NoOpNetworkLogger)
        assertNotNull(client)
    }

    @Test
    fun build_with_proxy_enabled_and_valid_http_url_does_not_throw() {
        val settings = AppSettings().apply {
            proxyEnabled = true
            httpProxy    = "http://proxy.example.com:8080"
        }
        val client = NetworkClientFactory.build(settings, NoOpNetworkLogger)
        assertNotNull(client)
    }

    @Test
    fun build_with_proxy_enabled_but_blank_urls_skips_proxy_silently() {
        val settings = AppSettings().apply {
            proxyEnabled = true
            httpProxy    = ""
            httpsProxy   = "   " // whitespace only
        }
        val client = NetworkClientFactory.build(settings, NoOpNetworkLogger)
        assertNotNull(client)
    }

    @Test
    fun build_with_proxy_disabled_ignores_proxy_fields() {
        val settings = AppSettings().apply {
            proxyEnabled = false
            httpProxy    = "http://should-be-ignored.example.com:9090"
        }
        val client = NetworkClientFactory.build(settings, NoOpNetworkLogger)
        assertNotNull(client)
    }

    @Test
    fun build_with_short_timeout_does_not_throw() {
        val settings = AppSettings().apply { requestTimeoutSec = 1 }
        val client = NetworkClientFactory.build(settings, NoOpNetworkLogger)
        assertNotNull(client)
    }

    @Test
    fun build_with_large_timeout_does_not_throw() {
        val settings = AppSettings().apply { requestTimeoutSec = 300 }
        val client = NetworkClientFactory.build(settings, NoOpNetworkLogger)
        assertNotNull(client)
    }
}
