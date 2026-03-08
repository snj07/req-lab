package com.reqlab.ui.desktop.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.reqlab.ui.shared.components.EnvironmentRowTone
import com.reqlab.ui.shared.components.applyPopupDragDelta
import com.reqlab.ui.shared.components.clampDialogOffsetFromCenter
import com.reqlab.ui.shared.components.clampPopupOffsetToViewport
import com.reqlab.ui.shared.components.environmentRowTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VariablePopupContractsTest {

    @Test
    fun clamp_popup_offset_keeps_popup_inside_viewport() {
        val result = clampPopupOffsetToViewport(
            candidate = IntOffset(5000, -100),
            popupSize = IntSize(400, 260),
            viewportSize = IntSize(1200, 800),
        )

        assertEquals(IntOffset(792, 8), result)
    }

    @Test
    fun apply_drag_delta_moves_and_clamps_popup() {
        val result = applyPopupDragDelta(
            currentOffset = IntOffset(40, 32),
            dragDx = -200f,
            dragDy = 120f,
            popupSize = IntSize(360, 260),
            viewportSize = IntSize(1000, 700),
        )

        assertEquals(IntOffset(8, 152), result)
    }

    @Test
    fun environment_row_tone_returns_hovered_when_hovered() {
        assertEquals(EnvironmentRowTone.HOVERED, environmentRowTone(index = 2, isHovered = true))
        assertEquals(EnvironmentRowTone.EVEN, environmentRowTone(index = 2, isHovered = false))
        assertEquals(EnvironmentRowTone.ODD, environmentRowTone(index = 3, isHovered = false))
    }

    @Test
    fun clamp_dialog_offset_from_center_keeps_dialog_visible() {
        // Dialog centred in 1400×900 viewport, card is 700×500.
        // Dragging far to the top-left must clamp to keep card inside viewport.
        val (cx, cy) = clampDialogOffsetFromCenter(
            offsetX = -5000f,
            offsetY = -5000f,
            cardSize = IntSize(700, 500),
            viewportSize = IntSize(1400, 900),
        )
        // After clamping the card's absolute left should be >= margin (8px).
        val absX = (1400 - 700) / 2f + cx
        val absY = (900 - 500) / 2f + cy
        assertTrue(absX >= 8f, "Card left edge went off-screen: absX=$absX")
        assertTrue(absY >= 8f, "Card top edge went off-screen: absY=$absY")
    }

    @Test
    fun clamp_dialog_offset_from_center_allows_positive_offset() {
        // Dragging down-right within bounds must not be clamped.
        val (cx, cy) = clampDialogOffsetFromCenter(
            offsetX = 100f,
            offsetY = 50f,
            cardSize = IntSize(600, 400),
            viewportSize = IntSize(1400, 900),
        )
        // 100px right from center with 400px slack — should not be clamped.
        assertEquals(100f, cx)
        assertEquals(50f, cy)
    }
}
