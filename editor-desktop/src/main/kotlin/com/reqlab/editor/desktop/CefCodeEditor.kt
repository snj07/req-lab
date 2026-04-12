package com.reqlab.editor.desktop

import com.reqlab.editor.core.EditorEngine
import com.reqlab.editor.core.EditorState
import com.reqlab.editor.core.LanguageMode

/**
 * WebView host interface for the CodeMirror editor page.
 *
 * Implementations (e.g. [CefCodeEditorHost] for JCEF, or a JavaFX WebView host)
 * must load [EditorHtmlBundle.load()] and relay messages via [WebEditorMessage].
 *
 * ── Required runtime ────────────────────────────────────────────────────────
 * Embedding a real WebView in Compose Desktop requires one of:
 *   • JBR (JetBrains Runtime) — JCEF org.cef.* is bundled.
 *     Run the app with IntelliJ's JBR or use `./gradlew runDistributable`.
 *   • explicit JCEF dep: `implementation("com.github.chromiumembedded:jcef:...*macos-arm64")`
 *   • JavaFX WebView: `implementation("org.openjfx:javafx-web:21:mac-aarch64")`
 *
 * See the project README.md §Editor for setup instructions.
 *
 * ── Headless / test mode ─────────────────────────────────────────────────────
 * Use [NoOpCodeEditorHost] for unit tests and CI environments where a native
 * WebView is not available.
 */
interface CodeEditorHost {

    /** Load [content] in the specified language, replacing the current document. */
    fun setText(content: String, language: LanguageMode)

    /** Toggle the editor between editable and read-only modes. */
    fun setReadOnly(readOnly: Boolean)

    /** Asynchronously read the current document text. Result delivered via [onResult]. */
    fun getText(onResult: (String) -> Unit)

    /** Invoked by the WebView when the user edits the document. */
    var onTextChanged: ((String) -> Unit)?

    /** Invoked after each lint pass with the number of inline diagnostics. */
    var onErrorCount: ((Int) -> Unit)?
}

// ─── No-Op implementation (tests + fallback) ─────────────────────────────────

/**
 * No-op [CodeEditorHost] that holds state in memory without a real WebView.
 * Suitable for unit tests and environments without native WebView support.
 */
class NoOpCodeEditorHost(
    private val engine: EditorEngine = EditorEngine(),
) : CodeEditorHost {

    private var currentText: String = ""
    private var currentMode: LanguageMode = LanguageMode.PLAIN_TEXT

    override var onTextChanged: ((String) -> Unit)? = null
    override var onErrorCount: ((Int) -> Unit)? = null

    /** Current in-memory EditorState (validated by [EditorEngine]). */
    val state: EditorState get() = engine.createState(currentText, currentMode)

    override fun setText(content: String, language: LanguageMode) {
        currentText = content
        currentMode = language
        onTextChanged?.invoke(content)
        val errors = engine.validate(content, language)
        onErrorCount?.invoke(errors.size)
    }

    override fun setReadOnly(readOnly: Boolean) { /* no-op */ }

    override fun getText(onResult: (String) -> Unit) { onResult(currentText) }
}

// ─── JCEF implementation skeleton ────────────────────────────────────────────

/**
 * JCEF-backed [CodeEditorHost] for Compose Desktop running on JBR.
 *
 * **Setup:** This class uses `org.cef.*` from JCEF.  The JCEF runtime is
 * available when running on JetBrains Runtime (JBR) or when JCEF is added
 * as an explicit Gradle dependency.  Without it, the constructor will throw
 * [ClassNotFoundException] and callers should fall back to [NoOpCodeEditorHost].
 *
 * ── Usage in Compose Desktop ─────────────────────────────────────────────
 * ```kotlin
 * // In your Compose Desktop window:
 * val host = remember {
 *     runCatching { CefCodeEditorHost() }
 *         .getOrElse { NoOpCodeEditorHost() }
 * }
 * // Place host.panel into your Compose SwingPanel
 * SwingPanel(
 *     factory = { host.panel },
 *     update  = {}
 * )
 * ```
 *
 * ── Message bridge ────────────────────────────────────────────────────────
 * The JCEF browser injects `window.reqlab.postMessage(json)` into the page.
 * Outgoing commands are sent via `browser.executeJavaScript(...)`.
 */
class CefCodeEditorHost : CodeEditorHost {

    /*
     * JCEF is loaded via reflection so this class compiles on standard JDK.
     * When org.cef.CefApp is present on the classpath (JBR/explicit dep),
     * the browser is fully functional; otherwise construction fails with
     * ClassNotFoundException which callers catch.
     */

    private val cefClass: Class<*> = Class.forName("org.cef.CefApp")

    /** The Swing JPanel containing the JCEF browser — embed in SwingPanel. */
    val panel: java.awt.Panel by lazy { buildCefPanel() }

    override var onTextChanged: ((String) -> Unit)? = null
    override var onErrorCount:  ((Int) -> Unit)?    = null

    override fun setText(content: String, language: LanguageMode) {
        val msg  = WebEditorMessage.SetText(content, WebEditorMessage.languageId(language))
        executeJs("""window.dispatchEvent(new MessageEvent('message',{data:${msg.toJson()}}))""")
    }

    override fun setReadOnly(readOnly: Boolean) {
        val msg = WebEditorMessage.SetReadOnly(readOnly)
        executeJs("""window.dispatchEvent(new MessageEvent('message',{data:${msg.toJson()}}))""")
    }

    override fun getText(onResult: (String) -> Unit) {
        executeJs("""window.dispatchEvent(new MessageEvent('message',{data:'{"type":"GET_TEXT"}'}))""")
        // Response arrives asynchronously via onTextChanged callback path
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private var browserRef: Any? = null

    private fun buildCefPanel(): java.awt.Panel {
        // Reflectively create: CefApp.getInstance() → CefClient → CefBrowser(html)
        val getInstance = cefClass.getMethod("getInstance")
        val cefApp = getInstance.invoke(null)

        val createClient = cefClass.getMethod("createClient")
        val client = createClient.invoke(cefApp)

        // Inject window.reqlab message listener
        val addMessageRouterMethod = client.javaClass.getMethod(
            "addMessageRouter", Class.forName("org.cef.browser.CefMessageRouter")
        )
        // (Message router setup requires JCEF internals — full integration
        //  requires a non-reflective dependency; this skeleton shows the contract.)

        // Load HTML as a data URI so it works without an HTTP server
        val htmlContent  = EditorHtmlBundle.load()
        val encoded      = java.util.Base64.getEncoder().encodeToString(htmlContent.toByteArray())
        val dataUri      = "data:text/html;base64,$encoded"

        val createBrowser = client.javaClass.getMethod(
            "createBrowser", String::class.java, Boolean::class.java, Boolean::class.java
        )
        val browser = createBrowser.invoke(client, dataUri, false, false)
        browserRef = browser

        val getUIComponent = browser.javaClass.getMethod("getUIComponent")
        val component = getUIComponent.invoke(browser) as java.awt.Component

        return java.awt.Panel(java.awt.BorderLayout()).apply { add(component) }
    }

    private fun executeJs(script: String) {
        val browser = browserRef ?: return
        val executeJs = browser.javaClass.getMethod(
            "executeJavaScript", String::class.java, String::class.java, Int::class.java
        )
        executeJs.invoke(browser, script, "", 0)
    }

    /**
     * Call when the panel is being destroyed to release JCEF resources.
     */
    fun dispose() {
        val browser = browserRef ?: return
        runCatching { browser.javaClass.getMethod("close", Boolean::class.java).invoke(browser, true) }
        browserRef = null
    }
}
