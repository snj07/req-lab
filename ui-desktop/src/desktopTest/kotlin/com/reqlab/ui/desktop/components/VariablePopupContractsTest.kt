package com.reqlab.ui.desktop.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.reqlab.ui.shared.components.EnvironmentRowTone
import com.reqlab.ui.shared.components.applyPopupDragDelta
import com.reqlab.ui.shared.components.clampDialogOffsetFromCenter
import com.reqlab.ui.shared.components.clampPopupOffsetToViewport
import com.reqlab.ui.shared.components.clampPopupOffsetToViewportF
import com.reqlab.ui.shared.components.environmentRowTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VariablePopupContractsTest {

    @Test
    fun clamp_popup_offset_keeps_popup_partially_visible() {
        // With MIN_VISIBLE_PX = 100, the popup can go partially off-screen
        // as long as at least 100px remain visible.
        val result = clampPopupOffsetToViewport(
            candidate = IntOffset(5000, -100),
            popupSize = IntSize(400, 260),
            viewportSize = IntSize(1200, 800),
        )

        // maxX = 1200 - 8 - 100 = 1092; minY = 8 + 100 - 260 = -152
        // y = -100 is within [-152, 692] so not clamped
        assertEquals(IntOffset(1092, -100), result)
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

        // minX = 8 + 100 - 360 = -252; -160 is within [-252, 892]
        assertEquals(IntOffset(-160, 152), result)
    }

    @Test
    fun environment_row_tone_returns_hovered_when_hovered() {
        assertEquals(EnvironmentRowTone.HOVERED, environmentRowTone(index = 2, isHovered = true))
        assertEquals(EnvironmentRowTone.EVEN, environmentRowTone(index = 2, isHovered = false))
        assertEquals(EnvironmentRowTone.ODD, environmentRowTone(index = 3, isHovered = false))
    }

    @Test
    fun clamp_dialog_offset_from_center_keeps_dialog_partially_visible() {
        // Dialog centred in 1400×900 viewport, card is 700×500.
        // Dragging far to the top-left must clamp so at least 100px remain visible.
        val (cx, cy) = clampDialogOffsetFromCenter(
            offsetX = -5000f,
            offsetY = -5000f,
            cardSize = IntSize(700, 500),
            viewportSize = IntSize(1400, 900),
        )
        val absX = (1400 - 700) / 2f + cx
        val absY = (900 - 500) / 2f + cy
        // At least MIN_VISIBLE_PX (100) of the card must overlap the viewport.
        // Visible width = min(absX + 700, 1400 - 8) - max(absX, 8)
        val visibleW = minOf(absX + 700, 1400f - 8f) - maxOf(absX, 8f)
        val visibleH = minOf(absY + 500, 900f - 8f) - maxOf(absY, 8f)
        assertTrue(visibleW >= 100f, "Less than 100px visible horizontally: $visibleW")
        assertTrue(visibleH >= 100f, "Less than 100px visible vertically: $visibleH")
    }

    @Test
    fun clamp_popup_offset_float_preserves_sub_pixel_precision() {
        // Simulate 30 tiny drag steps of 0.7px each.
        // The Int-based function would lose 0.7px per step (total 21px lost).
        // The Float-based function must preserve full accumulation.
        var x = 40f
        var y = 32f
        val popupSize = IntSize(400, 280)
        val viewportSize = IntSize(1280, 768)

        repeat(30) {
            x += 0.7f
            y += 0.3f
            val (cx, cy) = clampPopupOffsetToViewportF(x, y, popupSize, viewportSize)
            x = cx
            y = cy
        }

        // With float precision: x ≈ 40 + 21 = 61, y ≈ 32 + 9 = 41
        assertTrue(x >= 60f, "Float clamp should preserve sub-pixel: x=$x")
        assertTrue(y >= 40f, "Float clamp should preserve sub-pixel: y=$y")
    }

    @Test
    fun clamp_popup_offset_float_clamps_at_viewport_edges() {
        val (cx, cy) = clampPopupOffsetToViewportF(
            x = -500f, y = 5000f,
            popupSize = IntSize(400, 260),
            viewportSize = IntSize(1200, 800),
        )
        // minX = 8 + 100 - 400 = -292; maxY = 800 - 8 - 100 = 692
        assertEquals(-292f, cx)
        assertEquals(692f, cy)
    }

    @Test
    fun clamp_allows_horizontal_movement_when_card_equals_viewport_width() {
        // When card width == viewport width, the old clamp pinned x to 8.
        // The new clamp allows movement as long as 100px remain visible.
        val cardSize = IntSize(800, 400)
        val viewportSize = IntSize(800, 600)

        // Dialog offset: try dragging 300px to the right
        val (cx, _) = clampDialogOffsetFromCenter(
            offsetX = 300f, offsetY = 0f,
            cardSize = cardSize, viewportSize = viewportSize,
        )
        // centerX = 0; maxAbsX = 800 - 8 - 100 = 692; so 300 is within bounds
        assertTrue(cx > 200f, "Horizontal drag should be allowed: cx=$cx")

        // Popup offset: try placing at x = 300
        val result = clampPopupOffsetToViewport(
            candidate = IntOffset(300, 50),
            popupSize = cardSize, viewportSize = viewportSize,
        )
        assertTrue(result.x > 200, "Popup horizontal movement should be allowed: x=${result.x}")
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
