package com.reqlab.ui.desktop.integration

import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.BodyType
import com.reqlab.server.module
import com.reqlab.ui.shared.components.buildCurlCommand
import com.reqlab.ui.shared.components.buildPowerShellCommand
import com.reqlab.ui.shared.components.buildPythonCommand
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.state.RequestTabState
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopyCommandIntegrationTest {

    companion object {
        private const val PORT = 18080
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

        private fun commandExists(command: String): Boolean {
            val process = ProcessBuilder("sh", "-lc", "command -v $command >/dev/null 2>&1")
                .redirectErrorStream(true)
                .start()
            return process.waitFor() == 0
        }

        private fun pythonRequestsAvailable(): Boolean {
            val process = ProcessBuilder("sh", "-lc", "python3 -c 'import requests' >/dev/null 2>&1")
                .redirectErrorStream(true)
                .start()
            return process.waitFor() == 0
        }

        private fun runShell(command: String): Pair<Int, String> {
            val process = ProcessBuilder("sh", "-lc", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            return process.waitFor() to output
        }

        private fun baseLayers(): List<Map<String, String>> = listOf(
            mapOf(
                "baseUrl" to "http://localhost:$PORT",
                "traceId" to "trace-123",
                "name" to "john",
                "token" to "secret-token-9",
            )
        )

        private fun sampleTab(): RequestTabState = RequestTabState(
            name = "Copy Command Test",
            method = HttpMethodType.GET,
            url = "{{baseUrl}}/api/search",
        ).apply {
            params.clear()
            params.add(MutableKeyValue("q", "{{name}}"))
            params.add(MutableKeyValue("page", "1"))

            headers.clear()
            headers.add(MutableKeyValue("X-Trace-Id", "{{traceId}}"))
            headers.add(MutableKeyValue("Accept", "application/json"))

            authType = com.reqlab.core.model.AuthType.BEARER
            authToken = "{{token}}"
        }
    }

    @Test
    fun generated_copy_commands_are_fully_resolved() {
        val tab = sampleTab()
        val layers = baseLayers()

        val curl = buildCurlCommand(tab, layers)
        val python = buildPythonCommand(tab, layers)
        val powershell = buildPowerShellCommand(tab, layers)

        assertFalse(curl.contains("{{"))
        assertFalse(python.contains("{{"))
        assertFalse(powershell.contains("{{"))

        assertTrue(curl.contains("http://localhost:$PORT/api/search?q=john&page=1"))
        assertTrue(curl.contains("Authorization: Bearer secret-token-9"))
        assertTrue(curl.contains("X-Trace-Id: trace-123"))

        assertTrue(python.contains("http://localhost:$PORT/api/search?q=john&page=1"))
        assertTrue(python.contains("Authorization"))
        assertTrue(python.contains("secret-token-9"))

        assertTrue(powershell.contains("http://localhost:$PORT/api/search?q=john&page=1"))
        assertTrue(powershell.contains("Authorization"))
        assertTrue(powershell.contains("secret-token-9"))
    }

    @Test
    fun unresolved_placeholders_are_removed_from_copy_commands() {
        val tab = RequestTabState(
            name = "Missing var",
            method = HttpMethodType.POST,
            url = "{{baseUrl}}/api/raw",
        ).apply {
            bodyType = BodyType.RAW_TEXT
            bodyContent = "hello {{unknown}}"
            headers.clear()
            headers.add(MutableKeyValue("X-Trace-Id", "{{missingTrace}}"))
        }

        val curl = buildCurlCommand(tab, emptyList())
        val python = buildPythonCommand(tab, emptyList())
        val powershell = buildPowerShellCommand(tab, emptyList())

        assertFalse(curl.contains("{{"))
        assertFalse(python.contains("{{"))
        assertFalse(powershell.contains("{{"))
    }

    @Test
    fun copy_commands_resolve_variables_for_body_types_and_header_auth_combinations() {
        data class Case(
            val bodyType: BodyType,
            val url: String,
            val body: String,
            val expectedUrl: String,
            val expectedBodyFragment: String,
            val authType: com.reqlab.core.model.AuthType,
        )

        val cases = listOf(
            Case(
                bodyType = BodyType.JSON,
                url = "{{baseUrl}}/api/json",
                body = "{\"name\":\"{{name}}\"}",
                expectedUrl = "http://localhost:$PORT/api/json",
                expectedBodyFragment = "john",
                authType = com.reqlab.core.model.AuthType.BEARER,
            ),
            Case(
                bodyType = BodyType.RAW_TEXT,
                url = "{{baseUrl}}/api/raw",
                body = "raw-{{name}}",
                expectedUrl = "http://localhost:$PORT/api/raw",
                expectedBodyFragment = "raw-john",
                authType = com.reqlab.core.model.AuthType.API_KEY,
            ),
            Case(
                bodyType = BodyType.X_WWW_FORM_URLENCODED,
                url = "{{baseUrl}}/api/urlencoded",
                body = "username={{name}}&trace={{traceId}}",
                expectedUrl = "http://localhost:$PORT/api/urlencoded",
                expectedBodyFragment = "username=john",
                authType = com.reqlab.core.model.AuthType.NONE,
            ),
            Case(
                bodyType = BodyType.GRAPHQL,
                url = "{{baseUrl}}/api/graphql",
                body = "{\"query\":\"query { user(id: \\\"{{name}}\\\") { id } }\"}",
                expectedUrl = "http://localhost:$PORT/api/graphql",
                expectedBodyFragment = "john",
                authType = com.reqlab.core.model.AuthType.BEARER,
            ),
        )

        val layers = baseLayers()

        cases.forEach { case ->
            val tab = RequestTabState(
                name = "case-${case.bodyType}",
                method = HttpMethodType.POST,
                url = case.url,
            ).apply {
                headers.clear()
                headers.add(MutableKeyValue("X-Trace-Id", "{{traceId}}"))
                headers.add(MutableKeyValue("X-Static", "static-value"))

                bodyType = case.bodyType
                bodyContent = case.body

                authType = case.authType
                authToken = "{{token}}"
                authApiKey = "X-Api-Key"
                authApiValue = "{{token}}"
            }

            val curl = buildCurlCommand(tab, layers)
            val python = buildPythonCommand(tab, layers)
            val powershell = buildPowerShellCommand(tab, layers)

            listOf(curl, python, powershell).forEach { command ->
                assertFalse(command.contains("{{"), "Unresolved variable found in command: $command")
                assertTrue(command.contains(case.expectedUrl), "URL not resolved in: $command")
                assertTrue(command.contains(case.expectedBodyFragment), "Body not resolved in: $command")
                assertTrue(command.contains("trace-123"), "Header value not resolved in: $command")
            }
        }
    }

    @Test
    fun curl_command_executes_successfully_against_dummy_server() {
        assumeTrue(commandExists("curl"))

        val command = buildCurlCommand(sampleTab(), baseLayers())
        val (exitCode, output) = runShell(command)

        assertEquals(0, exitCode, "curl failed: $output")
        assertTrue(output.contains("Result for 'john'"), "Unexpected response: $output")
    }

    @Test
    fun python_command_executes_successfully_against_dummy_server() {
        assumeTrue(commandExists("python3"))
        assumeTrue(pythonRequestsAvailable())

        val script = buildPythonCommand(sampleTab(), baseLayers())
        val temp = File.createTempFile("reqlab-copy-test", ".py")
        temp.writeText(script)

        try {
            val (exitCode, output) = runShell("python3 ${temp.absolutePath}")
            assertEquals(0, exitCode, "python failed: $output")
            assertTrue(output.contains("200"), "Expected 200 in output: $output")
            assertTrue(output.contains("Result for 'john'"), "Unexpected output: $output")
        } finally {
            temp.delete()
        }
    }

    @Test
    fun powershell_command_executes_successfully_against_dummy_server_when_available() {
        assumeTrue(commandExists("pwsh"))

        val command = buildPowerShellCommand(sampleTab(), baseLayers())
        val escaped = command.replace("\"", "\\\"")
        val (exitCode, output) = runShell("pwsh -NoProfile -Command \"$escaped | Out-Null\"")

        assertEquals(0, exitCode, "pwsh failed: $output")
    }
}
