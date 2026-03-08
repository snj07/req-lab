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
        val result = clampPopupOffsetToViewport(
            candidate = IntOffset(-250, 9000),
            popupSize = IntSize(420, 280),
            viewportSize = IntSize(1365, 768),
        )

        assertEquals(IntOffset(8, 480), result)
    }

    @Test
    fun apply_drag_delta_moves_and_clamps_popup_on_web() {
        val result = applyPopupDragDelta(
            currentOffset = IntOffset(80, 60),
            dragDx = 5000f,
            dragDy = -5000f,
            popupSize = IntSize(360, 260),
            viewportSize = IntSize(1280, 720),
        )

        assertEquals(IntOffset(912, 8), result)
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
        val (cx, cy) = clampDialogOffsetFromCenter(
            offsetX = 9999f,
            offsetY = 9999f,
            cardSize = IntSize(700, 500),
            viewportSize = IntSize(1400, 900),
        )
        val absX = (1400 - 700) / 2f + cx
        val absY = (900 - 500) / 2f + cy
        assertTrue(absX + 700 <= 1400 - 8, "Card right edge off-screen: absX=$absX")
        assertTrue(absY + 500 <= 900 - 8, "Card bottom edge off-screen: absY=$absY")
    }
}
