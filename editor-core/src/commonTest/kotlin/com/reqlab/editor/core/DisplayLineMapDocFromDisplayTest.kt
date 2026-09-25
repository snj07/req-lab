package com.reqlab.editor.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayLineMapDocFromDisplayTest {

    @Test
    fun out_of_range_display_line_maps_to_last_visible_doc_line() {
        val map = DisplayLineMap(10)
        map.setFolded(1, 4)
        map.setFolded(7, 9)
        assertEquals(5, map.totalDisplayLines)
        assertEquals(7, map.docFromDisplay(4))
        assertEquals(7, map.docFromDisplay(90))
        assertEquals(7, map.docFromDisplay(map.totalDisplayLines))
    }
}
