package com.reqlab.core.scripting

import platform.JavaScriptCore.JSContext

/**
 * JavaScriptCore-based JS runtime for iOS (Kotlin/Native).
 *
 * Uses the system JavaScriptCore framework available on all Apple platforms.
 */

internal actual fun evaluateJs(script: String): String {
    return try {
        val context = JSContext()
        val result = context.evaluateScript(script)
        result?.toString() ?: """{"error":"JSC evaluation returned null"}"""
    } catch (e: Exception) {
        val msg = (e.message ?: "Unknown error")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        """{"error":"$msg"}"""
    }
}
