package com.reqlab.ui.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.BodyType
import com.reqlab.ui.desktop.state.RequestTabState
import com.reqlab.ui.desktop.theme.CodeFontFamily
import com.reqlab.ui.desktop.theme.ReqLabColors

/**
 * Editor panel for the request body. Shows a body-type selector at the top and
 * a multi-line code field for the body content.
 */
@Composable
internal fun BodyEditor(tab: RequestTabState, onDirty: () -> Unit) {
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

        // Body content editor
        BasicTextField(
            value = tab.bodyContent,
            onValueChange = { tab.bodyContent = it; onDirty() },
            textStyle = TextStyle(color = ReqLabColors.OnSurface, fontSize = 13.sp, fontFamily = CodeFontFamily),
            cursorBrush = SolidColor(ReqLabColors.Primary),
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(ReqLabColors.SurfaceContainer)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .testTag("body-editor"),
            decorationBox = { inner ->
                if (tab.bodyContent.isEmpty()) {
                    Text(
                        text = when (tab.bodyType) {
                            BodyType.JSON    -> "{\n  \n}"
                            BodyType.GRAPHQL -> "query {\n  \n}"
                            else             -> "Enter request body…"
                        },
                        color = ReqLabColors.OnSurfaceDim,
                        fontSize = 13.sp,
                        fontFamily = CodeFontFamily,
                    )
                }
                inner()
            },
        )
    }
}
