package com.reqlab.ui.shared.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Desktop draws a vertical bar; other targets are a no-op (wheel/trackpad still scroll). */
@Composable
expect fun PlatformLazyVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    testTag: String = "",
)

@Composable
expect fun PlatformColumnVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    testTag: String = "",
)

/** Right-side inset only so the thumb can travel the full list height. */
fun Modifier.insetScrollbar(): Modifier =
    fillMaxHeight().padding(end = 4.dp)
