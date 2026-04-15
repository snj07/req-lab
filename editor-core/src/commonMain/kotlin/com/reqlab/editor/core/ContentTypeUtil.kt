package com.reqlab.editor.core

/**
 * Utilities for mapping content-type headers to language modes and file extensions.
 */
object ContentTypeUtil {

    /**
     * Returns a file extension (without the leading dot) for the given [contentType].
     * Falls back to "txt" for unrecognised types.
     */
    fun fileExtensionForContentType(contentType: String?): String = when {
        contentType == null -> "txt"
        contentType.contains("json", ignoreCase = true) -> "json"
        contentType.contains("graphql", ignoreCase = true) -> "graphql"
        contentType.contains("xml", ignoreCase = true) -> "xml"
        contentType.contains("html", ignoreCase = true) -> "html"
        contentType.contains("javascript", ignoreCase = true) -> "js"
        contentType.contains("css", ignoreCase = true) -> "css"
        contentType.contains("csv", ignoreCase = true) -> "csv"
        contentType.contains("yaml", ignoreCase = true) || contentType.contains("yml", ignoreCase = true) -> "yaml"
        contentType.contains("svg", ignoreCase = true) -> "svg"
        contentType.contains("pdf", ignoreCase = true) -> "pdf"
        contentType.contains("png", ignoreCase = true) -> "png"
        contentType.contains("jpeg", ignoreCase = true) || contentType.contains("jpg", ignoreCase = true) -> "jpg"
        contentType.contains("gif", ignoreCase = true) -> "gif"
        contentType.contains("text/plain", ignoreCase = true) -> "txt"
        else -> "txt"
    }
}
