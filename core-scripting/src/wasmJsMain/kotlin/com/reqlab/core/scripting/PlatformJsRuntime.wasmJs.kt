package com.reqlab.core.scripting

/**
 * Browser-based JS runtime for Kotlin/Wasm JS target.
 *
 * Evaluates JavaScript using the host browser's `eval()`.
 * The script is always a self-contained IIFE that returns a JSON string.
 */

private fun jsEvalBrowser(code: JsString): JsString =
    js("String(eval(code))")

internal actual fun evaluateJs(script: String): String {
    return try {
        jsEvalBrowser(script.toJsString()).toString()
    } catch (e: Exception) {
        val msg = (e.message ?: "Unknown error")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        """{"error":"$msg"}"""
    }
}
