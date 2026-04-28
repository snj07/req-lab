package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reqlab.ui.shared.build.GeneratedBuildInfo
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.theme.ReqLabColors

@Composable
fun HelpAboutDialog(state: AppState) {
    if (!state.showHelpDialog) return

    Dialog(onDismissRequest = { state.showHelpDialog = false }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { state.showHelpDialog = false } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 720.dp, max = 920.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ReqLabColors.Surface)
                    .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                    .padding(20.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .testTag("help-about-dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Help & About",
                        color = ReqLabColors.OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Close",
                        color = ReqLabColors.Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("help-about-close")
                            .pointerInput(Unit) { detectTapGestures { state.showHelpDialog = false } },
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    HelpSection("About ${Strings.appName}") {
                        HelpText("ReqLab is a lightweight API client for HTTP workflows with local-first request editing, scripting, environments, and test assertions.")
                    }

                    HelpSection("Feature Overview") {
                        HelpText("• Request builder for methods, URL, params, headers, auth, and body")
                        HelpText("• Pre-request and post-request scripts")
                        HelpText("• Collections, history, environments, and global variables")
                        HelpText("• Multi-tab editing with save and tab management shortcuts")
                        HelpText("• Response viewer plus network/test logs")
                    }

                    HelpSection("How to Use") {
                        HelpText("1) Create or open a request tab.")
                        HelpText("2) Configure URL, method, auth, headers, and body.")
                        HelpText("3) Choose an environment for variable values.")
                        HelpText("4) Add optional pre-request/post-request scripts.")
                        HelpText("5) Send request and inspect response, logs, and tests.")
                    }

                    HelpSection("Shortcuts") {
                        ShortcutRow("⌘ + Enter / Ctrl + Enter", "Send request (or cancel if in progress)")
                        ShortcutRow("⌘ + Shift + [ / Ctrl + Shift + [", "Move active tab left")
                        ShortcutRow("⌘ + Shift + ] / Ctrl + Shift + ]", "Move active tab right")
                        ShortcutRow("⌘ + S / Ctrl + S", "Save active request")
                        ShortcutRow("⌘ + W / Ctrl + W", "Close active tab")
                        ShortcutRow("⌘ + N / Ctrl + N", "Create new request tab")
                        ShortcutRow("⌘ + , / Ctrl + ,", "Open settings")
                    }

                    HelpSection("Scripting Overview") {
                        HelpText("Use pre-request scripts to prepare headers, query values, body, and variables before dispatch.")
                        HelpText("Use post-request scripts to assert response behavior and persist extracted values into variables.")
                        HelpText("The default scripting namespace is reqlab and can be changed in Settings → Scripts.")
                    }

                    HelpSection("Version & Build Info") {
                        HelpText("Version: ${GeneratedBuildInfo.APP_VERSION}")
                        HelpText("Build: Kotlin Multiplatform + Compose Multiplatform")
                        HelpText("Supported platforms: macOS, Windows, Linux, and Web (Wasm)")
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = ReqLabColors.OnSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        content()
    }
}

@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        color = ReqLabColors.OnSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun ShortcutRow(shortcut: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ReqLabColors.SurfaceContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shortcut,
            color = ReqLabColors.OnSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(220.dp),
        )
        Text(
            text = action,
            color = ReqLabColors.OnSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}
