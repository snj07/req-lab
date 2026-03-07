package com.reqlab.ui.desktop.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.ui.desktop.state.AppState
import com.reqlab.ui.desktop.state.BottomTab
import com.reqlab.ui.desktop.state.ConsoleEntry
import com.reqlab.ui.desktop.state.LogLevel
import com.reqlab.ui.desktop.state.TestResultEntry
import com.reqlab.ui.desktop.theme.CodeFontFamily
import com.reqlab.ui.desktop.theme.ReqLabColors
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun BottomPanel(state: AppState) {
    Column(modifier = Modifier.fillMaxWidth().testTag("bottom-panel")) {
        // ── Toggle bar ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(ReqLabColors.Surface)
                .clickable { state.bottomPanelExpanded = !state.bottomPanelExpanded }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (state.bottomPanelExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = "Toggle bottom panel",
                tint = ReqLabColors.OnSurfaceDim,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))

            // Tab bar (always visible as header)
            ScrollableTabRow(
                selectedTabIndex = state.selectedBottomTab.ordinal,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = ReqLabColors.OnSurface,
                edgePadding = 0.dp,
                divider = {},
                indicator = {},
                modifier = Modifier.weight(1f),
            ) {
                BottomTab.entries.forEach { tab ->
                    val selected = tab == state.selectedBottomTab
                    Tab(
                        selected = selected,
                        onClick = {
                            state.selectedBottomTab = tab
                            if (!state.bottomPanelExpanded) state.bottomPanelExpanded = true
                        },
                        modifier = Modifier.height(30.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
                            )
                            if (tab == BottomTab.CONSOLE && state.consoleLogs.isNotEmpty()) {
                                Text(
                                    "${state.consoleLogs.size}",
                                    fontSize = 9.sp,
                                    color = ReqLabColors.OnSurfaceDim,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(ReqLabColors.SurfaceHigh)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Clear button
            if (state.bottomPanelExpanded) {
                IconButton(onClick = {
                    when (state.selectedBottomTab) {
                        BottomTab.CONSOLE      -> state.consoleLogs.clear()
                        BottomTab.TEST_RESULTS -> state.testResults.clear()
                        BottomTab.LOGS         -> state.consoleLogs.clear()
                    }
                }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = ReqLabColors.OnSurfaceDim, modifier = Modifier.size(14.dp))
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(ReqLabColors.Border))

        // ── Content ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.bottomPanelExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(state.bottomPanelHeight.dp)
                    .background(ReqLabColors.SurfaceVariant),
            ) {
                when (state.selectedBottomTab) {
                    BottomTab.CONSOLE      -> ConsoleView(state.consoleLogs)
                    BottomTab.TEST_RESULTS -> TestResultsView(state.testResults)
                    BottomTab.LOGS         -> ConsoleView(state.consoleLogs) // shared view
                }
            }
        }
    }
}

// ── Console View ────────────────────────────────────────────────

@Composable
private fun ConsoleView(logs: List<ConsoleEntry>) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No console output", color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("console-output"),
        ) {
            items(logs) { entry ->
                ConsoleRow(entry)
            }
        }
    }
}

@Composable
private fun ConsoleRow(entry: ConsoleEntry) {
    val color = when (entry.level) {
        LogLevel.INFO    -> ReqLabColors.OnSurfaceVariant
        LogLevel.SUCCESS -> ReqLabColors.Secondary
        LogLevel.WARNING -> ReqLabColors.Tertiary
        LogLevel.ERROR   -> ReqLabColors.Error
    }
    val prefix = when (entry.level) {
        LogLevel.INFO    -> "INFO"
        LogLevel.SUCCESS -> " OK "
        LogLevel.WARNING -> "WARN"
        LogLevel.ERROR   -> " ERR"
    }
    val timeStr = SimpleDateFormat("HH:mm:ss.SSS").format(Date(entry.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(timeStr, color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp, fontFamily = CodeFontFamily)
        Text(
            prefix,
            color = color,
            fontSize = 11.sp,
            fontFamily = CodeFontFamily,
            fontWeight = FontWeight.Bold,
        )
        Text(entry.message, color = color, fontSize = 11.sp, fontFamily = CodeFontFamily)
    }
}

// ── Test Results View ───────────────────────────────────────────

@Composable
private fun TestResultsView(results: List<TestResultEntry>) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No test results", color = ReqLabColors.OnSurfaceDim, fontSize = 12.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("test-results"),
        ) {
            items(results) { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (result.passed) ReqLabColors.Secondary.copy(alpha = 0.08f)
                            else ReqLabColors.Error.copy(alpha = 0.08f)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (result.passed) ReqLabColors.Secondary else ReqLabColors.Error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        result.name,
                        color = ReqLabColors.OnSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (result.message.isNotEmpty()) {
                        Text(result.message, color = ReqLabColors.OnSurfaceDim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
