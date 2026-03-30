package com.reqlab.core.scripting

/**
 * Evaluates a JavaScript program string and returns the result as a string.
 *
 * Each platform provides a concrete implementation:
 * - **Desktop / Android (JVM):** GraalVM Polyglot JS engine
 * - **wasmJs / js (Browser):** Native `eval()`
 * - **iOS (Native):** JavaScriptCore (`JSContext`)
 *
 * The input [script] is a complete IIFE produced by [ScriptBootstrap.build]
 * that returns a JSON string with test results, logs, and variable changes.
 */
internal expect fun evaluateJs(script: String): String
