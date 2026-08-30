package com.reqlab.core.network.mcp

import com.reqlab.core.model.McpConnectionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.ServerSocket
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

actual val mcpStdioSupported: Boolean = true

actual fun createStdioTransport(config: McpConnectionConfig): McpTransport {
    val pathEnv = loginShellPath()
    val windows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
    val argv = resolveStdioArgv(
        command = config.command,
        args = config.args,
        workingDir = config.workingDir,
        userDir = System.getProperty("user.dir").orEmpty(),
        exists = { java.io.File(it).isFile },
        pathEnv = pathEnv,
        pathSeparator = java.io.File.pathSeparator,
        extraExtensions = if (windows) listOf(".cmd", ".exe", ".bat") else emptyList(),
    )
    require(argv.isNotEmpty()) { "stdio command is required" }
    val builder = ProcessBuilder(argv)
    if (!config.workingDir.isNullOrBlank()) builder.directory(java.io.File(config.workingDir))
    val env = builder.environment()
    if (pathEnv.isNotBlank()) env["PATH"] = pathEnv
    config.env.forEach { (k, v) -> env[k] = v }
    builder.redirectErrorStream(false)
    val process = try {
        builder.start()
    } catch (e: java.io.IOException) {
        throw McpTransportException(
            "Cannot run program \"${argv.first()}\" (${argv.joinToString(" ")}): ${e.message}",
            e,
        )
    }
    val lines = Channel<String>(Channel.UNLIMITED)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val stdoutJob = scope.launch {
        process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                lines.send(line)
            }
        }
        lines.close()
    }
    val stderrJob = scope.launch {
        process.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                reader.readLine() ?: break
            }
        }
    }
    val hook = thread(start = false, isDaemon = true, name = "mcp-stdio-shutdown") {
        process.destroy()
    }
    runCatching { Runtime.getRuntime().addShutdownHook(hook) }
    return NdjsonStdioTransport(
        scope = scope,
        incomingLines = lines,
        writeLine = { line ->
            process.outputStream.write((line + "\n").toByteArray(Charsets.UTF_8))
            process.outputStream.flush()
        },
        onClose = {
            stdoutJob.cancel()
            stderrJob.cancel()
            process.destroy()
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        },
    )
}

actual fun mcpSecureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}

actual val mcpInteractiveOAuthSupported: Boolean = true

actual suspend fun mcpOpenAuthorizeUrlAndAwaitCode(authorizeUrl: String, redirectPort: Int): String {
    val server = ServerSocket(redirectPort)
    try {
        if (Desktop.isDesktopSupported()) {
            runCatching { Desktop.getDesktop().browse(URI(authorizeUrl)) }
        }
        val socket = server.accept()
        socket.soTimeout = 30_000
        val reader = socket.getInputStream().bufferedReader()
        val requestLine = reader.readLine().orEmpty()
        val query = requestLine.substringAfter("?", "").substringBefore(" ")
        val code = query.split("&").map { it.split("=", limit = 2) }
            .firstOrNull { it.firstOrNull() == "code" }
            ?.getOrNull(1)
            ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
            ?: throw McpProtocolException("No authorization code in redirect")
        val body = "<html><body>ReqLab authorized. You can close this window.</body></html>"
        socket.getOutputStream().write(
            "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                .toByteArray(),
        )
        socket.close()
        return code
    } finally {
        server.close()
    }
}

/**
 * GUI-launched apps often have a short PATH. Load the login-shell PATH so
 * commands such as `npx` and Homebrew binaries resolve. Cache the first lookup.
 */
internal fun loginShellPath(): String {
    cachedLoginShellPath?.let { return it }
    val fallback = System.getenv("PATH").orEmpty()
    val os = System.getProperty("os.name").orEmpty().lowercase()
    if (os.contains("win")) {
        cachedLoginShellPath = fallback
        return fallback
    }
    val shell = System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: "/bin/sh"
    val path = try {
        val process = ProcessBuilder(shell, "-ilc", "printf %s \"\$PATH\"")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(3, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            fallback
        } else {
            val out = process.inputStream.bufferedReader().readText()
            out.lineSequence().map { it.trim() }.lastOrNull { it.contains('/') && it.contains(':') }
                ?: fallback
        }
    } catch (_: Exception) {
        fallback
    }
    val merged = mergePath(path, fallback, java.io.File.pathSeparator)
    cachedLoginShellPath = merged
    return merged
}

@Volatile
private var cachedLoginShellPath: String? = null
