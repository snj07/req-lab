package com.reqlab.ui.shared.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformLazyVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier,
    testTag: String,
) {
}

@Composable
actual fun PlatformColumnVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier,
    testTag: String,
) {
}
