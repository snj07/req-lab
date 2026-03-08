package com.reqlab.ui.web.components

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

class VariablePopupContractsWebTest {

    @Test
    fun clamp_popup_offset_keeps_popup_inside_viewport_on_web() {
        // MIN_VISIBLE_PX = 100, margin = 8
        // minX = 8+100-420 = -312, maxX = 1365-8-100 = 1257
        // minY = 8+100-280 = -172, maxY = 768-8-100 = 660
        // candidate.x = -250 → within [-312, 1257] → -250
        // candidate.y = 9000 → clamped to 660
        val result = clampPopupOffsetToViewport(
            candidate = IntOffset(-250, 9000),
            popupSize = IntSize(420, 280),
            viewportSize = IntSize(1365, 768),
        )

        assertEquals(IntOffset(-250, 660), result)
    }

    @Test
    fun apply_drag_delta_moves_and_clamps_popup_on_web() {
        // candidate = (80+5000, 60-5000) = (5080, -4940)
        // minX = 8+100-360 = -252, maxX = 1280-8-100 = 1172
        // minY = 8+100-260 = -152, maxY = 720-8-100 = 612
        // x → 1172 (clamped), y → -152 (clamped)
        val result = applyPopupDragDelta(
            currentOffset = IntOffset(80, 60),
            dragDx = 5000f,
            dragDy = -5000f,
            popupSize = IntSize(360, 260),
            viewportSize = IntSize(1280, 720),
        )

        assertEquals(IntOffset(1172, -152), result)
    }

    @Test
    fun environment_row_tone_contract_matches_desktop_on_web() {
        assertEquals(EnvironmentRowTone.EVEN, environmentRowTone(index = 0, isHovered = false))
        assertEquals(EnvironmentRowTone.ODD, environmentRowTone(index = 1, isHovered = false))
        assertEquals(EnvironmentRowTone.HOVERED, environmentRowTone(index = 1, isHovered = true))
    }

    @Test
    fun clamp_dialog_offset_from_center_contract_on_web() {
        // Dragging far to the right should be clamped to viewport edge.
        // MIN_VISIBLE_PX=100, margin=8: maxAbsX=1400-8-100=1292, maxAbsY=900-8-100=792
        // The card left edge is clamped — at least 100px of the card must remain visible.
        val (cx, cy) = clampDialogOffsetFromCenter(
            offsetX = 9999f,
            offsetY = 9999f,
            cardSize = IntSize(700, 500),
            viewportSize = IntSize(1400, 900),
        )
        val absX = (1400 - 700) / 2f + cx
        val absY = (900 - 500) / 2f + cy
        // absX <= viewportWidth - margin - MIN_VISIBLE_PX = 1292
        assertTrue(absX <= 1400 - 8 - 100, "Card dragged too far right: absX=$absX")
        // absY <= viewportHeight - margin - MIN_VISIBLE_PX = 792
        assertTrue(absY <= 900 - 8 - 100, "Card dragged too far down: absY=$absY")
    }
}
