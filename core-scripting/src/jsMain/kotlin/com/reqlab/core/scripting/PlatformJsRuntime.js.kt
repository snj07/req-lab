@file:Suppress("NOTHING_TO_INLINE")

package com.reqlab.core.scripting

/**
 * Browser-based JS runtime for Kotlin/JS (IR) target.
 *
 * Evaluates JavaScript using the host browser's `eval()`.
 * The script is always a self-contained IIFE that returns a JSON string.
 */

@JsName("eval")
private external fun jsEval(code: String): dynamic

internal actual fun evaluateJs(script: String): String {
    return try {
        val result: dynamic = jsEval(script)
        result.toString() as String
    } catch (e: Exception) {
        val msg = (e.message ?: "Unknown error")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        """{"error":"$msg"}"""
    }
}
