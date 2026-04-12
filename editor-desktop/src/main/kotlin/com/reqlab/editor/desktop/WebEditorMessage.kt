package com.reqlab.editor.desktop

import com.reqlab.editor.core.LanguageMode

/**
 * Typed message protocol between the Kotlin host application and the
 * CodeMirror editor page ([EditorHtmlBundle]).
 *
 * Messages are serialised to JSON strings for transmission over the
 * WebView's postMessage / CefMessageRouter bridge.
 *
 * ── HOST → EDITOR (commands) ──────────────────────────────────────────────
 * [SetText]     – Replace the editor document and change language mode.
 * [SetLanguage] – Change the language without replacing text.
 * [SetReadOnly] – Toggle read-only mode (used for response viewer).
 * [GetText]     – Request the current document text; editor responds with [TextResponse].
 *
 * ── EDITOR → HOST (events) ────────────────────────────────────────────────
 * [TextChanged]  – Document changed (fired on every keystroke).
 * [ErrorCount]   – How many inline diagnostics the linter just reported.
 * [Ready]        – The editor is mounted and ready to accept commands.
 * [TextResponse] – Response to [GetText].
 */
sealed class WebEditorMessage {

    // ── Host → Editor ─────────────────────────────────────────────────────

    data class SetText(
        val text: String,
        val language: String = "text",
    ) : WebEditorMessage() {
        override fun toJson() = """{"type":"SET_TEXT","text":${text.toJsonString()},"language":"$language"}"""
    }

    data class SetLanguage(val language: String) : WebEditorMessage() {
        override fun toJson() = """{"type":"SET_LANGUAGE","language":"$language"}"""
    }

    data class SetReadOnly(val readOnly: Boolean) : WebEditorMessage() {
        override fun toJson() = """{"type":"SET_READONLY","readonly":$readOnly}"""
    }

    object GetText : WebEditorMessage() {
        override fun toJson() = """{"type":"GET_TEXT"}"""
    }

    // ── Editor → Host ─────────────────────────────────────────────────────

    data class TextChanged(val text: String) : WebEditorMessage() {
        override fun toJson() = """{"type":"TEXT_CHANGED","text":${text.toJsonString()}}"""
    }

    data class ErrorCount(val count: Int) : WebEditorMessage() {
        override fun toJson() = """{"type":"ERROR_COUNT","count":$count}"""
    }

    object Ready : WebEditorMessage() {
        override fun toJson() = """{"type":"READY"}"""
    }

    data class TextResponse(val text: String) : WebEditorMessage() {
        override fun toJson() = """{"type":"TEXT_RESPONSE","text":${text.toJsonString()}}"""
    }

    // ── Serialisation helpers ─────────────────────────────────────────────

    /** Serialise this message to a JSON string for postMessage transmission. */
    abstract fun toJson(): String

    protected fun String.toJsonString(): String {
        val escaped = replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    companion object {

        /** Maps a [LanguageMode] to the string identifier used by editor.html. */
        fun languageId(mode: LanguageMode): String = when (mode) {
            LanguageMode.JSON       -> "json"
            LanguageMode.XML        -> "xml"
            LanguageMode.HTML       -> "html"
            LanguageMode.JAVASCRIPT -> "javascript"
            LanguageMode.PLAIN_TEXT -> "text"
        }

        /**
         * Parse a raw JSON string sent by the editor page.
         * Returns null for unknown / malformed messages.
         */
        fun fromJson(raw: String): WebEditorMessage? {
            val type = extractField(raw, "type") ?: return null
            return when (type) {
                "TEXT_CHANGED"  -> TextChanged(extractField(raw, "text") ?: "")
                "ERROR_COUNT"   -> ErrorCount((extractField(raw, "count") ?: "0").toIntOrNull() ?: 0)
                "READY"         -> Ready
                "TEXT_RESPONSE" -> TextResponse(extractField(raw, "text") ?: "")
                else            -> null
            }
        }

        /** Minimal JSON field extractor — avoids pulling in a full JSON library. */
        private fun extractField(json: String, key: String): String? {
            val marker = """"$key":"""
            val start = json.indexOf(marker).takeIf { it >= 0 } ?: return null
            val valueStart = start + marker.length
            return when {
                json[valueStart] == '"' -> {
                    // String value
                    val sb = StringBuilder()
                    var i = valueStart + 1
                    while (i < json.length && json[i] != '"') {
                        if (json[i] == '\\' && i + 1 < json.length) {
                            when (json[i + 1]) {
                                '"'  -> sb.append('"')
                                '\\' -> sb.append('\\')
                                'n'  -> sb.append('\n')
                                'r'  -> sb.append('\r')
                                't'  -> sb.append('\t')
                                else -> sb.append(json[i + 1])
                            }
                            i += 2
                        } else {
                            sb.append(json[i])
                            i++
                        }
                    }
                    sb.toString()
                }
                else -> {
                    // Number / boolean: read until delimiter
                    val end = json.indexOfFirst(valueStart) { it == ',' || it == '}' || it == '\n' }
                    json.substring(valueStart, if (end >= 0) end else json.length).trim()
                }
            }
        }

        private fun String.indexOfFirst(start: Int, predicate: (Char) -> Boolean): Int {
            for (i in start until length) if (predicate(this[i])) return i
            return -1
        }
    }
}
