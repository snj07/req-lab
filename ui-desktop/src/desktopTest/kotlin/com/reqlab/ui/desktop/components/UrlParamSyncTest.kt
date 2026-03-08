package com.reqlab.ui.shared.components

import com.reqlab.ui.shared.state.RequestTabState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the URL ↔ Params two-way synchronisation logic.
 * syncParamsFromUrl / syncUrlFromParams are internal functions in RequestEditor.kt.
 */
class UrlParamSyncTest {

    // ─────────────────────────────────────────────────────────────
    // syncParamsFromUrl  (URL → params table)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `syncParamsFromUrl – simple key=value pairs are parsed`() {
        val tab = RequestTabState()
        syncParamsFromUrl(tab, "http://localhost:8080/api/search?q=hello&page=2&limit=25")

        assertEquals(3, tab.params.size)
        assertEquals("q",     tab.params[0].key);  assertEquals("hello", tab.params[0].value)
        assertEquals("page",  tab.params[1].key);  assertEquals("2",     tab.params[1].value)
        assertEquals("limit", tab.params[2].key);  assertEquals("25",    tab.params[2].value)
    }

    @Test
    fun `syncParamsFromUrl – url with no query string clears params`() {
        val tab = RequestTabState().apply {
            syncParamsFromUrl(this, "http://localhost:8080/api/users?page=1")
        }
        syncParamsFromUrl(tab, "http://localhost:8080/api/users")

        assertTrue(tab.params.isEmpty())
    }

    @Test
    fun `syncParamsFromUrl – url with trailing question mark clears params`() {
        val tab = RequestTabState()
        syncParamsFromUrl(tab, "http://localhost:8080/api/users?")

        assertTrue(tab.params.isEmpty())
    }

    @Test
    fun `syncParamsFromUrl – param with no value results in empty-string value`() {
        val tab = RequestTabState()
        syncParamsFromUrl(tab, "http://localhost:8080/ping?debug")

        assertEquals(1, tab.params.size)
        assertEquals("debug", tab.params[0].key)
        assertEquals("",      tab.params[0].value)
    }

    @Test
    fun `syncParamsFromUrl – param with equals sign but no value is handled`() {
        val tab = RequestTabState()
        syncParamsFromUrl(tab, "http://localhost:8080/ping?token=")

        assertEquals(1, tab.params.size)
        assertEquals("token", tab.params[0].key)
        assertEquals("",      tab.params[0].value)
    }

    @Test
    fun `syncParamsFromUrl – blank segments between ampersands are ignored`() {
        val tab = RequestTabState()
        syncParamsFromUrl(tab, "http://localhost:8080/api?a=1&&b=2")

        // The blank "" between && is filtered
        assertEquals(2, tab.params.size)
    }

    @Test
    fun `syncParamsFromUrl – replaces params on second call`() {
        val tab = RequestTabState()
        syncParamsFromUrl(tab, "http://localhost:8080/api?x=1")
        syncParamsFromUrl(tab, "http://localhost:8080/api?y=2&z=3")

        assertEquals(2, tab.params.size)
        assertEquals("y", tab.params[0].key)
        assertEquals("z", tab.params[1].key)
    }

    // ─────────────────────────────────────────────────────────────
    // syncUrlFromParams  (params table → URL)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `syncUrlFromParams – enabled params are appended as query string`() {
        val tab = RequestTabState().apply {
            url = "http://localhost:8080/api/users"
        }
        syncParamsFromUrl(tab, "http://localhost:8080/api/users?page=1&limit=10")
        syncUrlFromParams(tab)

        assertEquals("http://localhost:8080/api/users?page=1&limit=10", tab.url)
    }

    @Test
    fun `syncUrlFromParams – disabled params are excluded from url`() {
        val tab = RequestTabState().apply {
            url = "http://localhost:8080/api/users"
        }
        syncParamsFromUrl(tab, "http://localhost:8080/api/users?page=1&limit=10")
        tab.params[1].enabled = false   // disable limit
        syncUrlFromParams(tab)

        assertEquals("http://localhost:8080/api/users?page=1", tab.url)
    }

    @Test
    fun `syncUrlFromParams – empty params list produces bare url`() {
        val tab = RequestTabState().apply {
            url = "http://localhost:8080/api/users?page=1"
        }
        tab.params.clear()
        syncUrlFromParams(tab)

        assertEquals("http://localhost:8080/api/users", tab.url)
    }

    @Test
    fun `syncUrlFromParams – params with blank key are excluded`() {
        val tab = RequestTabState().apply { url = "http://localhost:8080/api" }
        syncParamsFromUrl(tab, "http://localhost:8080/api?key=val")
        tab.params[0].key = ""  // blank key
        syncUrlFromParams(tab)

        assertEquals("http://localhost:8080/api", tab.url)
    }

    // ─────────────────────────────────────────────────────────────
    // Round-trip: URL → params → URL
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `round trip – url parsed to params and back produces same url`() {
        val original = "http://localhost:8080/api/search?q=test&page=1&limit=10"
        val tab = RequestTabState().apply { url = original }
        syncParamsFromUrl(tab, original)
        syncUrlFromParams(tab)

        assertEquals(original, tab.url)
    }

    @Test
    fun `round trip – adding new param to table updates url`() {
        val tab = RequestTabState().apply { url = "http://localhost:8080/api" }
        syncParamsFromUrl(tab, "http://localhost:8080/api?page=1")
        // Simulate user typing a second param
        syncParamsFromUrl(tab, "http://localhost:8080/api?page=1")
        tab.params.add(com.reqlab.ui.shared.state.MutableKeyValue("limit", "20"))
        syncUrlFromParams(tab)

        assertTrue(tab.url.contains("page=1"))
        assertTrue(tab.url.contains("limit=20"))
    }
}
