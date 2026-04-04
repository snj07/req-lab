package com.reqlab.ui.desktop.integration

import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.NetworkEvent
import com.reqlab.core.network.NoOpNetworkLogger
import com.reqlab.core.network.RetryPolicy
import com.reqlab.server.module
import com.reqlab.ui.shared.network.NetworkClientFactory
import com.reqlab.ui.shared.state.AppSettings
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsImpactIntegrationTest {

    companion object {
        private const val PORT = 18081
        private lateinit var server: io.ktor.server.engine.EmbeddedServer<*, *>

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = embeddedServer(Netty, port = PORT, module = io.ktor.server.application.Application::module)
            server.start(wait = false)
            Thread.sleep(300)
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server.stop(500, 2000)
        }
    }

    @Test
    fun should_apply_request_timeout_setting_to_runtime_behavior() = runTest {
        val request = RequestDefinition(
            id = "timeout-settings-test",
            name = "Timeout Behavior",
            method = HttpMethodType.GET,
            url = "http://localhost:$PORT/api/slow?ms=1200",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val shortTimeout = AppSettings().apply {
            requestTimeoutSec = 1
        }
        val shortTimeoutClient = NetworkClientFactory.build(
            settings = shortTimeout,
            logger = NoOpNetworkLogger,
            retryPolicy = RetryPolicy(maxAttempts = 1),
        )
        val shortTimeoutEvents = shortTimeoutClient.execute(request, emptyList()).toList()
        assertTrue(shortTimeoutEvents.last() is NetworkEvent.Failure)

        val longTimeout = AppSettings().apply {
            requestTimeoutSec = 5
        }
        val longTimeoutClient = NetworkClientFactory.build(
            settings = longTimeout,
            logger = NoOpNetworkLogger,
            retryPolicy = RetryPolicy(maxAttempts = 1),
        )
        val longTimeoutEvents = longTimeoutClient.execute(request, emptyList()).toList()
        assertTrue(longTimeoutEvents.last() is NetworkEvent.Success)
        val response = (longTimeoutEvents.last() as NetworkEvent.Success).response
        assertEquals(200, response.statusCode)
    }

    @Test
    fun should_apply_timeout_setting_and_fail_slow_requests() = runTest {
        val request = RequestDefinition(
            id = "timeout-test",
            name = "Timeout",
            method = HttpMethodType.GET,
            url = "http://localhost:$PORT/api/slow?ms=2000",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val settings = AppSettings().apply {
            requestTimeoutSec = 1
            followRedirects = true
        }

        val client = NetworkClientFactory.build(
            settings = settings,
            logger = NoOpNetworkLogger,
            retryPolicy = RetryPolicy(maxAttempts = 1),
        )

        val events = client.execute(request, emptyList()).toList()
        assertTrue(events.first() is NetworkEvent.Started)
        assertTrue(events.last() is NetworkEvent.Failure)
    }
}
