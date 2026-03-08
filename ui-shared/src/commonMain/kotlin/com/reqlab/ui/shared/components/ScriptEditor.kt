package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors

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
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = ReqLabColors.OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        BasicTextField(
            value = script,
            onValueChange = onScriptChanged,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                .padding(12.dp),
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

// ── Script API reference hints ──────────────────────────────────

private val PRE_REQUEST_HINT = """
// Pre-request script — runs BEFORE the HTTP request is sent.
// Use it to compute dynamic values or set environment variables.
//
// Available API:
//   pm.environment.set("key", "value")   // set/override an env variable
//   pm.environment.get("key")            // read an env variable
//   console.log("message", value)        // log to the Console panel
//
// Example:
//   pm.environment.set("timestamp", String.valueOf(System.currentTimeMillis()))
//   pm.environment.set("authHeader", "Bearer " + pm.environment.get("token"))
//   console.log("Sending request to", pm.environment.get("baseUrl"))
""".trim()

private val TEST_HINT = """
// Tests script — runs AFTER the response is received.
// Use it to validate the response and extract values for chaining.
//
// Available API:
//   pm.test("name", function() { ... })              // define a test block
//   pm.expect(pm.response.code).to.equal(200)        // assert status code
//   pm.expect(pm.response.code).to.be.oneOf([200, 201])
//   pm.expect(pm.response.code).to.be.above(199)
//   pm.expect(pm.response.text()).to.include("ok")   // check body text
//   pm.expect(pm.response.json().name).to.equal("Alice")  // check JSON field
//   pm.expect(pm.response.json().users[0].id).to.equal(1)
//   pm.response.to.have.status(200)                  // shorthand status check
//   pm.environment.set("token", pm.response.json().token) // chain to next request
//   console.log("Status:", pm.response.code)
//
// Example:
//   pm.test("Status is 200", function() {
//       pm.expect(pm.response.code).to.equal(200)
//   })
//   pm.test("Response has token", function() {
//       pm.expect(pm.response.json().token).to.exist
//       pm.environment.set("token", pm.response.json().token)
//   })
""".trim()
