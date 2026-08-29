package com.reqlab.ui.shared.state

import com.reqlab.core.network.LlmTextAssembler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamTabStateTest {

    @Test
    fun live_stream_text_accumulates_from_chunks() {
        val tab = RequestTabState(name = "stream")
        assertTrue(tab.streamChunks.isEmpty())
        assertEquals("", tab.liveStreamText)

        tab.streamChunks.add("""{"choices":[{"delta":{"content":"Hello"}}]}""")
        tab.liveStreamText = LlmTextAssembler.assemble(tab.streamChunks.toList())
        tab.streamChunks.add("""{"choices":[{"delta":{"content":"!"}}]}""")
        tab.liveStreamText = LlmTextAssembler.assemble(tab.streamChunks.toList())

        assertEquals(2, tab.streamChunks.size)
        assertEquals("Hello!", tab.liveStreamText)

        tab.streamChunks.clear()
        tab.liveStreamText = ""
        assertTrue(tab.streamChunks.isEmpty())
        assertEquals("", tab.liveStreamText)
    }
}
