package com.reqlab.ui.shared.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reqlab.ui.shared.theme.ReqLabColors

/**
 * A multi-line script editor used for Pre-request and Post-request script tabs.
 * Now powered by the shared [CodeEditor] with JavaScript syntax highlighting,
 * search, formatting, word wrap, and copy-to-clipboard.
 *
 * @param script         current script content
 * @param onScriptChanged callback for changes
 * @param title          human-readable label (e.g. "Pre-request Script")
 */
@kotlinx.serialization.ExperimentalSerializationApi
@Composable
fun ScriptEditor(
    script: String,
    onScriptChanged: (String) -> Unit,
    title: String,
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = ReqLabColors.OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        CodeEditor(
            text = script,
            onTextChange = onScriptChanged,
            language = SyntaxLanguage.JAVASCRIPT,
            modifier = Modifier.fillMaxSize(),
            showToolbar = true,
            enableFolding = false,
            enableSearch = true,
            enableFormat = false,  // JS formatting not implemented
            enableWordWrap = true,
            enableCopy = true,
            enableDownload = false,
            placeholder = if (title.contains("Pre-request", ignoreCase = true))
                PRE_REQUEST_HINT else TEST_HINT,
            testTagPrefix = "script-editor",
        )
    }
}

// ── Script API reference hints ──────────────────────────────────

private val PRE_REQUEST_HINT = """
// Pre-request script — runs BEFORE the HTTP request is sent.
// Default namespace prefix: "reqlab" (change in Settings › Scripts).
//
// Variable scopes:
//   reqlab.environment.set("key", "value")     // environment variable
//   reqlab.environment.get("key")
//   reqlab.globals.set("key", "value")          // global variable
//   reqlab.collectionVariables.set("key", "v")  // collection variable
//
// Mutate the outgoing request:
//   reqlab.request.headers.add("X-Trace", "id")
//   reqlab.request.headers.upsert("X-Key", reqlab.environment.get("key"))
//   request.setQueryParam("debug", "true")      // low-level aliases also work
//   request.setMethod("POST")
//   request.setUrl("https://other-host.example.com")
//
// Logging:
//   console.log("msg", value)
//   reqlab.console.log("msg")
//
// Example:
//   reqlab.environment.set("ts", Date.now().toString())
//   reqlab.request.headers.add("X-Timestamp", reqlab.environment.get("ts"))
//   console.log("Sending", reqlab.request.method, reqlab.request.url)
""".trim()

private val TEST_HINT = """
// Post-request script — runs AFTER the response is received.
// Default namespace prefix: "reqlab" (change in Settings › Scripts).
//
// Test blocks:
//   reqlab.test("name", () => {
//     reqlab.expect(reqlab.response.code).to.equal(200)
//   })
//
// Response accessors:
//   reqlab.response.code          // HTTP status int
//   reqlab.response.responseTime  // ms
//   reqlab.response.size          // bytes
//   reqlab.response.text()        // body as string
//   reqlab.response.json()        // parsed JSON (use .field for paths)
//   reqlab.response.headers.get("Content-Type")
//
// Assertions:
//   .to.equal(v)  .to.notEqual(v)  .to.include("s")  .to.match("regex")
//   .to.be.above(n)  .to.be.below(n)  .to.be.oneOf([a,b])
//   .to.exist  .to.be.ok  .to.be.null  .to.be.empty
//
// Chain to next request:
//   reqlab.environment.set("token", reqlab.response.json().token)
//
// Example:
//   reqlab.test("Status is 200", () => {
//     reqlab.expect(reqlab.response.code).to.equal(200)
//   })
//   reqlab.test("Has token", () => {
//     reqlab.expect(reqlab.response.json().token).to.exist
//     reqlab.environment.set("token", reqlab.response.json().token)
//   })
//   reqlab.test("Fast response", () => {
//     reqlab.expect(reqlab.response.responseTime).to.be.below(500)
//   })
""".trim()
