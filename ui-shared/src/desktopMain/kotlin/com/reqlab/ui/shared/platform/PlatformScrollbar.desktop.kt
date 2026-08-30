package com.reqlab.ui.shared.platform

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.reqlab.ui.shared.theme.ReqLabColors

@Composable
actual fun PlatformLazyVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier,
    testTag: String,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        style = reqlabScrollbarStyle(),
        modifier = modifier.then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
    )
}

@Composable
actual fun PlatformColumnVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier,
    testTag: String,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        style = reqlabScrollbarStyle(),
        modifier = modifier.then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
    )
}

@Composable
private fun reqlabScrollbarStyle() = ScrollbarStyle(
    minimalHeight = 28.dp,
    thickness = 4.dp,
    shape = RoundedCornerShape(50),
    hoverDurationMillis = 300,
    unhoverColor = ReqLabColors.OnSurface.copy(alpha = 0.18f),
    hoverColor = ReqLabColors.OnSurface.copy(alpha = 0.40f),
)
