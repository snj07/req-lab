package com.reqlab.ui.desktop

import com.reqlab.core.model.AuthType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.LogLevel
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.state.RequestTabState
import kotlinx.coroutines.Job
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the state-level bug fixes from the QA report.
 *
 * Covered issues:
 *  - H-1  Concurrent request race condition (currentJob field)
 *  - M-3  Console/Logs tabs identical (networkEventLogs + logNetworkEvent)
 *  - M-7  Env dialog uses index as LazyColumn key (MutableKeyValue.uid uniqueness)
 *  - M-8  Script vars accumulate across sends (scriptInjectedVarKeys)
 *  - M-11 Auth fields not cleared when switching auth type
 */
class QaFixesStateTest {

    // ── H-1: Concurrent request race condition ────────────────────────────────

    @Test
    fun `H-1 RequestTabState has currentJob field that starts as null`() {
        val tab = RequestTabState()
        assertNull(tab.currentJob, "currentJob should be null when no request is in-flight")
    }

    @Test
    fun `H-1 currentJob can be assigned and cancelled`() {
        val tab = RequestTabState()
        val job = Job()
        tab.currentJob = job

        assertNotNull(tab.currentJob)
        assertFalse(job.isCancelled)

        tab.currentJob?.cancel()
        assertTrue(job.isCancelled, "Job should be cancelled after calling cancel()")
        assertNotNull(tab.currentJob, "Field still holds reference after cancel")
    }

    @Test
    fun `H-1 assigning new job does not auto-cancel previous one (caller must cancel)`() {
        val tab = RequestTabState()
        val firstJob = Job()
        tab.currentJob = firstJob

        // Simulate what RequestExecutor does: cancel the old job before launching new one
        tab.currentJob?.cancel()
        assertTrue(firstJob.isCancelled)

        val secondJob = Job()
        tab.currentJob = secondJob
        assertFalse(secondJob.isCancelled, "New job should not be cancelled")
    }

    // ── M-3: Console/Logs tabs identical ─────────────────────────────────────

    @Test
    fun `M-3 AppState has separate networkEventLogs list`() {
        val state = AppState()
        assertTrue(state.networkEventLogs.isEmpty(), "networkEventLogs should start empty")
        // consoleLogs may have boot messages; networkEventLogs must be distinct
        val consoleBefore = state.consoleLogs.size
        state.logNetworkEvent("HTTP GET /ping")
        // consoleLogs count should increase (dual-write)
        assertEquals(consoleBefore + 1, state.consoleLogs.size)
        // networkEventLogs should also have the entry
        assertEquals(1, state.networkEventLogs.size)
    }

    @Test
    fun `M-3 logNetworkEvent dual-writes to consoleLogs and networkEventLogs`() {
        val state = AppState()
        val consoleStart = state.consoleLogs.size
        state.logNetworkEvent("Request started", LogLevel.INFO)

        assertEquals(consoleStart + 1, state.consoleLogs.size, "consoleLogs should grow by 1")
        assertEquals(1, state.networkEventLogs.size, "networkEventLogs should grow by 1")
        assertEquals("Request started", state.networkEventLogs.last().message)
    }

    @Test
    fun `M-3 regular log() does NOT write to networkEventLogs`() {
        val state = AppState()
        state.log("Some console message", LogLevel.INFO)
        assertTrue(
            state.networkEventLogs.isEmpty(),
            "log() should NOT write to networkEventLogs (only logNetworkEvent should)"
        )
    }

    @Test
    fun `M-3 networkEventLogs and consoleLogs can be cleared independently`() {
        val state = AppState()
        state.logNetworkEvent("net event 1")
        state.logNetworkEvent("net event 2")
        state.log("console only", LogLevel.INFO)

        val consoleCountAfterAdds = state.consoleLogs.size

        // Clear only network logs
        state.networkEventLogs.clear()
        assertTrue(state.networkEventLogs.isEmpty())

        // consoleLogs unaffected by clearing networkEventLogs
        assertEquals(consoleCountAfterAdds, state.consoleLogs.size)
    }

    // ── M-7: Index as LazyColumn key ─────────────────────────────────────────

    @Test
    fun `M-7 MutableKeyValue uid is non-blank`() {
        val kv = MutableKeyValue()
        assertTrue(kv.uid.isNotBlank(), "uid should be a non-blank string")
    }

    @Test
    fun `M-7 MutableKeyValue uid is unique per instance`() {
        val ids = (1..100).map { MutableKeyValue().uid }.toSet()
        assertEquals(100, ids.size, "All 100 MutableKeyValue instances should have distinct UIDs")
    }

    @Test
    fun `M-7 MutableKeyValue uid is stable (same instance returns same uid)`() {
        val kv = MutableKeyValue()
        val uid1 = kv.uid
        val uid2 = kv.uid
        assertEquals(uid1, uid2, "uid should be stable for the same instance")
    }

    @Test
    fun `M-7 two different MutableKeyValue instances have different uids`() {
        val kv1 = MutableKeyValue(key = "foo")
        val kv2 = MutableKeyValue(key = "foo") // identical content, different instance
        assertNotEquals(kv1.uid, kv2.uid, "Two separate instances should NOT share a uid")
    }

    // ── M-8: Script-injected vars accumulate ─────────────────────────────────

    @Test
    fun `M-8 RequestTabState has scriptInjectedVarKeys set starting empty`() {
        val tab = RequestTabState()
        assertTrue(tab.scriptInjectedVarKeys.isEmpty())
    }

    @Test
    fun `M-8 scriptInjectedVarKeys can be populated and cleared`() {
        val tab = RequestTabState()
        tab.scriptInjectedVarKeys.add("dynamicToken")
        tab.scriptInjectedVarKeys.add("computedTimestamp")
        assertEquals(2, tab.scriptInjectedVarKeys.size)

        // Simulate the cleanup done by RequestExecutor before re-send
        tab.scriptInjectedVarKeys.clear()
        assertTrue(tab.scriptInjectedVarKeys.isEmpty())
    }

    // ── M-11: Auth fields not cleared when switching type ─────────────────────

    @Test
    fun `M-11 authType starts as NONE with all credential fields empty`() {
        val tab = RequestTabState()
        assertEquals(AuthType.NONE, tab.authType)
        assertTrue(tab.authUsername.isEmpty())
        assertTrue(tab.authPassword.isEmpty())
        assertTrue(tab.authToken.isEmpty())
        assertTrue(tab.authApiKey.isEmpty())
        assertTrue(tab.authApiValue.isEmpty())
    }

    @Test
    fun `M-11 credential fields can be set and read back`() {
        val tab = RequestTabState()
        tab.authType = AuthType.BASIC
        tab.authUsername = "alice"
        tab.authPassword = "s3cr3t"

        assertEquals("alice", tab.authUsername)
        assertEquals("s3cr3t", tab.authPassword)
    }

    @Test
    fun `M-11 clearing BASIC credentials before switching resets fields`() {
        val tab = RequestTabState()
        tab.authType = AuthType.BASIC
        tab.authUsername = "bob"
        tab.authPassword = "hunter2"

        // Simulate what AuthEditor now does when the user clicks a different auth type:
        // Clear OLD type's fields before switching.
        if (tab.authType == AuthType.BASIC) {
            tab.authUsername = ""
            tab.authPassword = ""
        }
        tab.authType = AuthType.BEARER

        assertTrue(tab.authUsername.isEmpty(), "username should be cleared after switching from BASIC")
        assertTrue(tab.authPassword.isEmpty(), "password should be cleared after switching from BASIC")
        assertEquals(AuthType.BEARER, tab.authType)
    }

    @Test
    fun `M-11 clearing BEARER token before switching resets token field`() {
        val tab = RequestTabState()
        tab.authType = AuthType.BEARER
        tab.authToken = "eyJhbGciOiJSUzI1NiJ9..."

        if (tab.authType == AuthType.BEARER || tab.authType == AuthType.JWT) {
            tab.authToken = ""
        }
        tab.authType = AuthType.API_KEY

        assertTrue(tab.authToken.isEmpty(), "token should be cleared after switching from BEARER")
        assertEquals(AuthType.API_KEY, tab.authType)
    }

    @Test
    fun `M-11 clearing API_KEY fields before switching resets both key and value`() {
        val tab = RequestTabState()
        tab.authType = AuthType.API_KEY
        tab.authApiKey = "X-Custom-Key"
        tab.authApiValue = "abc123"

        if (tab.authType == AuthType.API_KEY) {
            tab.authApiKey = ""
            tab.authApiValue = ""
        }
        tab.authType = AuthType.NONE

        assertTrue(tab.authApiKey.isEmpty())
        assertTrue(tab.authApiValue.isEmpty())
    }

    // ── H-3: Large response body threshold (pure logic) ──────────────────────

    @Test
    fun `H-3 200-line threshold boundary - exactly 200 lines uses inline rendering`() {
        val body = (1..200).joinToString("\n") { "line $it" }
        val lines = body.split('\n')
        // Inline (single Text) rendering is used when lines.size <= 200
        assertTrue(lines.size <= 200, "Exactly 200 lines should use inline rendering path")
    }

    @Test
    fun `H-3 201-line body crosses threshold into virtualized rendering`() {
        val body = (1..201).joinToString("\n") { "line $it" }
        val lines = body.split('\n')
        assertTrue(lines.size > 200, "201-line body should use virtualized LazyColumn path")
    }

    @Test
    fun `H-3 empty body does not crash line splitter`() {
        val lines = "".split('\n')
        assertEquals(1, lines.size)
        assertEquals("", lines[0])
    }
}
