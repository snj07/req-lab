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

    @Test
    fun `round trip – repeated query keys remain separate and ordered`() {
        val original = "http://localhost:8080/api/echo-query?x=1&x=2"
        val tab = RequestTabState().apply { url = original }

        syncParamsFromUrl(tab, original)
        syncUrlFromParams(tab)

        assertEquals(listOf("x", "x"), tab.params.map { it.key })
        assertEquals(listOf("1", "2"), tab.params.map { it.value })
        assertEquals(original, tab.url)
    }

    // ─────────────────────────────────────────────────────────────
    // Regression: params duplication in execution (Bug #1)
    // buildUrlWithParams appends tab.params to a URL that already
    // contains those same params (put there by syncUrlFromParams).
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `buildCurlCommand – params are not duplicated when url already has embedded query string`() {
        // After syncUrlFromParams, tab.url contains "?q=test&page=1".
        // buildUrlWithParams (called inside buildCurlCommand) also appends tab.params
        // → each param ends up doubled in the final URL.
        val tab = RequestTabState().apply { url = "http://localhost:8080/api/search" }
        syncParamsFromUrl(tab, "http://localhost:8080/api/search?q=test&page=1")
        syncUrlFromParams(tab)
        // tab.url is now "http://localhost:8080/api/search?q=test&page=1"
        // tab.params still holds [q=test, page=1]

        val curlCmd = buildCurlCommand(tab)

        assertEquals(
            1,
            curlCmd.split("q=test").size - 1,
            "q=test must appear exactly once in the curl command URL; duplication detected:\n$curlCmd",
        )
        assertEquals(
            1,
            curlCmd.split("page=1").size - 1,
            "page=1 must appear exactly once in the curl command URL; duplication detected:\n$curlCmd",
        )
    }

    @Test
    fun `syncParamsFromUrl – fragment is not swallowed into the last query value`() {
        val tab = RequestTabState()
        syncParamsFromUrl(tab, "http://localhost:8080/api/search?q=hello#section")

        assertEquals(1, tab.params.size)
        assertEquals("q", tab.params[0].key)
        assertEquals("hello", tab.params[0].value)
    }

    @Test
    fun `syncUrlFromParams – keeps fragment after rewriting query`() {
        val tab = RequestTabState().apply {
            url = "http://localhost:8080/api/search?q=hello#section"
        }
        syncParamsFromUrl(tab, tab.url)
        tab.params[0].value = "world"
        syncUrlFromParams(tab)

        assertEquals("http://localhost:8080/api/search?q=world#section", tab.url)
    }

    @Test
    fun `buildCurlCommand – keeps fragment and does not put it in the query value`() {
        val tab = RequestTabState().apply {
            url = "http://localhost:8080/api/search?q=hello#section"
        }
        syncParamsFromUrl(tab, tab.url)
        syncUrlFromParams(tab)

        val curlCmd = buildCurlCommand(tab)

        assertTrue(curlCmd.contains("#section"), "Copy-as cURL must keep the fragment:\n$curlCmd")
        assertTrue(curlCmd.contains("q=hello#section"), "Query then fragment, not hash inside a param value:\n$curlCmd")
    }

    @Test
    fun `buildHTTPieCommand – keeps repeated headers`() {
        val tab = RequestTabState()
        tab.headers.add(com.reqlab.ui.shared.state.MutableKeyValue("X-Tag", "kotlin"))
        tab.headers.add(com.reqlab.ui.shared.state.MutableKeyValue("X-Tag", "ktor"))

        val httpie = buildHTTPieCommand(tab)

        assertTrue(httpie.contains("X-Tag:kotlin"), httpie)
        assertTrue(httpie.contains("X-Tag:ktor"), httpie)
    }
}
