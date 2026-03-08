package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.BodyType
import com.reqlab.ui.shared.platform.pickBinaryFileForRequest
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.RequestTabState
import com.reqlab.ui.shared.theme.CodeFontFamily
import com.reqlab.ui.shared.theme.ReqLabColors

private const val BINARY_ATTACHMENT_PREFIX = "reqlab-binary:"

/**
 * Editor panel for the request body. Shows a body-type selector at the top and
 * a multi-line code field for the body content.
 */
@Composable
fun BodyEditor(tab: RequestTabState, state: AppState, onDirty: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Body-type selector chips – horizontally scrollable so they never wrap
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            BodyType.entries.forEach { bt ->
                val selected = bt == tab.bodyType
                Text(
                    text = bt.name.replace('_', ' '),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(min = 40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) ReqLabColors.SelectedItem else Color.Transparent)
                        .clickable {
                            tab.bodyType = bt
                            tab.syncSystemHeaders()
                            onDirty()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (tab.bodyType == BodyType.BINARY) {
            val attachedName = attachedBinaryFileName(tab.bodyContent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Attach File",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ReqLabColors.Primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ReqLabColors.SelectedItem)
                        .clickable {
                            pickBinaryFileForRequest { file ->
                                tab.bodyContent = encodeBinaryAttachment(file.name, file.base64Content)
                                onDirty()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                Text(
                    text = attachedName?.let { "Attached: $it" } ?: "No file attached",
                    color = ReqLabColors.OnSurfaceDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Body content editor
        VariableAwareTextField(
            value = tab.bodyContent,
            onValueChange = { tab.bodyContent = it; onDirty() },
            singleLine = false,
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .testTag("body-editor"),
            placeholder = when (tab.bodyType) {
                BodyType.JSON    -> "{\n  \n}"
                BodyType.GRAPHQL -> "query {\n  \n}"
                BodyType.BINARY  -> "Attach a file, or paste raw/base64 content…"
                else             -> "Enter request body…"
            },
        )
    }
}

private fun encodeBinaryAttachment(fileName: String, base64Content: String): String =
    "$BINARY_ATTACHMENT_PREFIX$fileName\n$base64Content"

private fun attachedBinaryFileName(content: String): String? {
    if (!content.startsWith(BINARY_ATTACHMENT_PREFIX)) return null
    val firstNewLine = content.indexOf('\n')
    if (firstNewLine <= BINARY_ATTACHMENT_PREFIX.length) return null
    return content.substring(BINARY_ATTACHMENT_PREFIX.length, firstNewLine)
}
