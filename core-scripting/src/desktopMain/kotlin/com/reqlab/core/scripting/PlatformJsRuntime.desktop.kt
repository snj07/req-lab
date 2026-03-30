package com.reqlab.core.scripting

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.PolyglotException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * GraalVM Polyglot JS runtime for Desktop (JVM).
 *
 * Reuses a shared [Engine] for fast context creation while creating
 * a fresh [Context] per evaluation for sandboxing.
 */

private val graalEngine: Engine by lazy {
    Engine.newBuilder()
        .option("engine.WarnInterpreterOnly", "false")
        .build()
}

    private const val SCRIPT_TIMEOUT_SECONDS = 5L

internal actual fun evaluateJs(script: String): String {
    return try {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<String> {
                val ctx = Context.newBuilder("js")
                    .engine(graalEngine)
                    .build()
                ctx.use { c ->
                    val result = c.eval("js", script)
                    if (result.isString) result.asString() else result.toString()
                }
            }
            future.get(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is PolyglotException) throw cause
            throw e
        } catch (_: TimeoutException) {
            """{"error":"Script execution timed out after ${SCRIPT_TIMEOUT_SECONDS} seconds"}"""
        } finally {
            executor.shutdownNow()
        }
    } catch (e: PolyglotException) {
        val msg = (e.message ?: "Unknown JS error")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        """{"error":"$msg"}"""
    } catch (e: Exception) {
        val msg = (e.message ?: "Unknown error")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        """{"error":"$msg"}"""
    }
}
