package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reqlab.ui.shared.i18n.Strings
import com.reqlab.ui.shared.theme.ReqLabColors
import kotlin.math.roundToInt

/**
 * Shown when the user tries to close multiple tabs where at least one has unsaved changes.
 * Offers Save All / Discard All / Cancel.
 */
@Composable
fun DirtyMultiCloseDialog(
    dirtyCount: Int,
    onSaveAll: () -> Unit,
    onDiscardAll: () -> Unit,
    onCancel: () -> Unit,
) {
    // M-2: Make the dialog draggable.
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onCancel) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .pointerInput(Unit) { detectTapGestures { onCancel() } },
            contentAlignment = Alignment.Center,
        ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .widthIn(min = 400.dp, max = 540.dp)
                .onSizeChanged { cardSize = it }
                .clip(RoundedCornerShape(12.dp))
                .background(ReqLabColors.Surface)
                .border(1.dp, ReqLabColors.Border, RoundedCornerShape(12.dp))
                .draggableNoSlop { dx, dy ->
                    val (cx, cy) = clampDialogOffsetFromCenter(
                        offsetX = offsetX + dx,
                        offsetY = offsetY + dy,
                        cardSize = cardSize,
                        viewportSize = viewportSize,
                    )
                    offsetX = cx
                    offsetY = cy
                }
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(24.dp)
                .testTag("dirty-multi-close-dialog"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Unsaved Changes",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ReqLabColors.OnSurface,
                )
                Text(
                    text = "$dirtyCount ${Strings.t("requests_unsaved_changes_suffix")}",
                    fontSize = 13.sp,
                    color = ReqLabColors.OnSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    // Cancel
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .border(1.dp, ReqLabColors.Border, RoundedCornerShape(8.dp))
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("dirty-multi-cancel"),
                    ) {
                        Text(Strings.cancel, fontSize = 13.sp, color = ReqLabColors.OnSurfaceVariant)
                    }

                    // Discard All
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.SurfaceContainer)
                            .border(1.dp, ReqLabColors.Error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onDiscardAll)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("dirty-multi-discard"),
                    ) {
                        Text(Strings.t("discard_all"), fontSize = 13.sp, color = ReqLabColors.Error, fontWeight = FontWeight.Medium)
                    }

                    // Save All
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReqLabColors.Primary)
                            .clickable(onClick = onSaveAll)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("dirty-multi-save-all"),
                    ) {
                        Text(Strings.t("save_all"), fontSize = 13.sp, color = ReqLabColors.OnPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }  // closes card Box
        }  // closes backdrop Box
    }
}
