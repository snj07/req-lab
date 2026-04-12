package com.reqlab.editor.desktop

/**
 * Loads the bundled `editor.html` from the module jar resources.
 *
 * The HTML file is a self-contained CodeMirror 6 editor page that:
 *  - Loads CodeMirror packages from esm.sh CDN (pinned versions).
 *  - Supports JSON, XML, HTML, and JavaScript language modes with:
 *      • Syntax highlighting
 *      • Code folding (objects, arrays, tags, blocks)
 *      • Inline error diagnostics (red/amber underline markers)
 *      • Indentation guides (dotted vertical lines for nested blocks)
 *      • Pixel-perfect line numbers
 *  - Communicates with the host application via [WebEditorMessage] / postMessage.
 *
 * Typical use:
 * ```kotlin
 * val htmlContent = EditorHtmlBundle.load()
 * // Pass htmlContent to your WebView implementation
 * ```
 */
object EditorHtmlBundle {

    private const val RESOURCE_PATH = "/editor.html"

    /**
     * Returns the full HTML content of the bundled CodeMirror editor page.
     *
     * @throws IllegalStateException if the resource cannot be found in the classpath.
     */
    fun load(): String =
        EditorHtmlBundle::class.java.getResourceAsStream(RESOURCE_PATH)
            ?.bufferedReader()
            ?.readText()
            ?: error("Could not load $RESOURCE_PATH from classpath")

    /**
     * Returns true if the resource is present on the classpath.
     * Useful for conditional WebView materialisation.
     */
    fun isAvailable(): Boolean =
        EditorHtmlBundle::class.java.getResourceAsStream(RESOURCE_PATH) != null
}
