package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors

private val ScriptEditorLineHeight = 20.sp

/**
 * A simple multi-line script editor used for Pre-request and Test script tabs.
 *
 * @param script         current script content
 * @param onScriptChanged callback for changes
 * @param title          human-readable label (e.g. "Pre-request Script")
 */
@Composable
fun ScriptEditor(
    script: String,
    onScriptChanged: (String) -> Unit,
    title: String,
) {
    val scriptEditorTextStyle = TextStyle(
        color = ReqLabColors.OnSurface,
        fontSize = 13.sp,
        lineHeight = ScriptEditorLineHeight,
        fontFamily = CodeFontFamily,
    )
    val scriptEditorLineNumberTextStyle = TextStyle(
        color = ReqLabColors.OnSurfaceDim.copy(alpha = 0.55f),
        fontSize = 13.sp,
        lineHeight = ScriptEditorLineHeight,
        fontFamily = CodeFontFamily,
        textAlign = TextAlign.End,
    )

    val lines = (script.ifEmpty { "\n" }).lines()

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = ReqLabColors.OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp)),
        ) {
            BasicTextField(
                value = buildString {
                    lines.forEachIndexed { idx, _ ->
                        append(idx + 1)
                        if (idx < lines.lastIndex) append('\n')
                    }
                },
                onValueChange = {},
                readOnly = true,
                textStyle = scriptEditorLineNumberTextStyle,
                cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                    .testTag("script-line-numbers"),
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(ReqLabColors.Border),
            )

            BasicTextField(
                value = script,
                onValueChange = onScriptChanged,
                textStyle = scriptEditorTextStyle,
                cursorBrush = SolidColor(ReqLabColors.Primary),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .testTag("script-editor"),
                decorationBox = { inner ->
                    if (script.isEmpty()) {
                        val hint = if (title.contains("Pre-request", ignoreCase = true))
                            PRE_REQUEST_HINT else TEST_HINT
                        Text(
                            text = hint,
                            color = ReqLabColors.OnSurfaceDim,
                            fontSize = 12.sp,
                            fontFamily = CodeFontFamily,
                        )
                    }
                    inner()
                },
            )
        }
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
// Test script — runs AFTER the response is received.
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
