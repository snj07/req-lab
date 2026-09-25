package com.reqlab.ui.shared.components

import com.reqlab.ui.shared.state.MutableKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentEditorLogicTest {

    @Test
    fun matches_key_substring() {
        assertTrue(environmentVariableMatches("baseUrl", "http://localhost", "url"))
        assertFalse(environmentVariableMatches("token", "abc", "url"))
    }

    @Test
    fun matches_value_substring() {
        assertTrue(environmentVariableMatches("host", "http://localhost:8080", "8080"))
    }

    @Test
    fun matches_are_case_insensitive() {
        assertTrue(environmentVariableMatches("API_KEY", "SecretValue", "api_key"))
        assertTrue(environmentVariableMatches("API_KEY", "SecretValue", "secretvalue"))
    }

    @Test
    fun trims_leading_and_trailing_search_whitespace() {
        assertTrue(environmentVariableMatches("token", "abc", "  tok  "))
        assertTrue(environmentVariableMatches("token", "abc", "\tabc\n"))
    }

    @Test
    fun blank_query_matches_every_row() {
        assertTrue(environmentVariableMatches("anything", "value", ""))
        assertTrue(environmentVariableMatches("anything", "value", "   "))
    }

    @Test
    fun secret_values_participate_in_matching() {
        assertTrue(environmentVariableMatches("password", "hunter2secret", "hunter2"))
    }

    @Test
    fun disabled_rows_still_match() {
        val disabled = MutableKeyValue("legacy", "old-host", enabled = false)
        val visible = visibleEnvironmentVariables(listOf(disabled), "legacy")
        assertEquals(listOf(disabled), visible)
    }

    @Test
    fun filter_preserves_original_row_order() {
        val first = MutableKeyValue("alpha", "one")
        val second = MutableKeyValue("beta", "shared")
        val third = MutableKeyValue("gamma", "shared")
        val visible = visibleEnvironmentVariables(listOf(first, second, third), "shared")
        assertEquals(listOf(second, third), visible)
    }

    @Test
    fun pinned_row_stays_visible_when_it_stops_matching() {
        val first = MutableKeyValue("keep", "visible")
        val second = MutableKeyValue("other", "row")
        val visible = visibleEnvironmentVariables(
            listOf(first, second),
            query = "zzz",
            pinnedUid = first.uid,
        )
        assertEquals(listOf(first), visible)
    }

    @Test
    fun result_count_label_only_interpolates_counts() {
        val label = environmentVariableResultCountLabel(1, 4, "{shown} of {total} variables")
        assertEquals("1 of 4 variables", label)
        assertFalse(label.contains("hunter2"))
    }

    @Test
    fun right_resize_grows_width_and_keeps_left_edge() {
        val start = EnvironmentDialogGeometry(widthDp = 720f, heightDp = 560f)
        val resized = resizeEnvironmentDialog(
            start,
            EnvironmentResizeHandle.RIGHT,
            deltaXDp = 80f,
            deltaYDp = 40f,
            minWidthDp = 600f,
            minHeightDp = 460f,
            maxWidthDp = 1200f,
            maxHeightDp = 900f,
        )
        assertEquals(800f, resized.widthDp)
        assertEquals(560f, resized.heightDp)
        assertEquals(leftEdge(start), leftEdge(resized))
        assertEquals(start.offsetYDp, resized.offsetYDp)
    }

    @Test
    fun bottom_resize_grows_height_and_keeps_top_edge() {
        val start = EnvironmentDialogGeometry(widthDp = 720f, heightDp = 560f)
        val resized = resizeEnvironmentDialog(
            start,
            EnvironmentResizeHandle.BOTTOM,
            deltaXDp = 80f,
            deltaYDp = 40f,
            minWidthDp = 600f,
            minHeightDp = 460f,
            maxWidthDp = 1200f,
            maxHeightDp = 900f,
        )
        assertEquals(720f, resized.widthDp)
        assertEquals(600f, resized.heightDp)
        assertEquals(topEdge(start), topEdge(resized))
        assertEquals(start.offsetXDp, resized.offsetXDp)
    }

    @Test
    fun corner_resize_grows_width_and_height_while_keeping_left_and_top() {
        val start = EnvironmentDialogGeometry(widthDp = 720f, heightDp = 560f, offsetXDp = 12f, offsetYDp = -8f)
        val resized = resizeEnvironmentDialog(
            start,
            EnvironmentResizeHandle.BOTTOM_RIGHT,
            deltaXDp = 50f,
            deltaYDp = 30f,
            minWidthDp = 600f,
            minHeightDp = 460f,
            maxWidthDp = 1200f,
            maxHeightDp = 900f,
        )
        assertEquals(770f, resized.widthDp)
        assertEquals(590f, resized.heightDp)
        assertEquals(leftEdge(start), leftEdge(resized))
        assertEquals(topEdge(start), topEdge(resized))
    }

    @Test
    fun resize_clamps_to_minimum_and_viewport_maximum() {
        val start = EnvironmentDialogGeometry(widthDp = 720f, heightDp = 560f)
        val shrunk = resizeEnvironmentDialog(
            start,
            EnvironmentResizeHandle.RIGHT,
            deltaXDp = -400f,
            deltaYDp = 0f,
            minWidthDp = 600f,
            minHeightDp = 460f,
            maxWidthDp = 1000f,
            maxHeightDp = 800f,
        )
        assertEquals(600f, shrunk.widthDp)
        assertEquals(leftEdge(start), leftEdge(shrunk))

        val grown = resizeEnvironmentDialog(
            start,
            EnvironmentResizeHandle.BOTTOM_RIGHT,
            deltaXDp = 5000f,
            deltaYDp = 5000f,
            minWidthDp = 600f,
            minHeightDp = 460f,
            maxWidthDp = 900f,
            maxHeightDp = 700f,
        )
        assertEquals(900f, grown.widthDp)
        assertEquals(700f, grown.heightDp)
    }

    @Test
    fun clamp_fits_dialog_into_small_windows_by_reducing_minimum() {
        val clamped = clampEnvironmentDialog(
            EnvironmentDialogGeometry(720f, 560f),
            viewportWidthDp = 500f,
            viewportHeightDp = 400f,
        )
        assertEquals(468f, clamped.widthDp)
        assertEquals(368f, clamped.heightDp)
        assertTrue(clamped.widthDp <= 500f - 32f)
        assertTrue(clamped.heightDp <= 400f - 32f)
    }

    @Test
    fun clamp_limits_restored_size_to_current_viewport() {
        val clamped = clampEnvironmentDialog(
            EnvironmentDialogGeometry(4000f, 3000f),
            viewportWidthDp = 1280f,
            viewportHeightDp = 800f,
        )
        assertEquals(1248f, clamped.widthDp)
        assertEquals(768f, clamped.heightDp)
    }

    @Test
    fun clamp_keeps_default_size_in_a_large_viewport() {
        val clamped = clampEnvironmentDialog(
            EnvironmentDialogGeometry(720f, 560f),
            viewportWidthDp = 1440f,
            viewportHeightDp = 900f,
        )
        assertEquals(720f, clamped.widthDp)
        assertEquals(560f, clamped.heightDp)
    }

    private fun leftEdge(geometry: EnvironmentDialogGeometry): Float =
        geometry.offsetXDp - geometry.widthDp / 2f

    private fun topEdge(geometry: EnvironmentDialogGeometry): Float =
        geometry.offsetYDp - geometry.heightDp / 2f
}
