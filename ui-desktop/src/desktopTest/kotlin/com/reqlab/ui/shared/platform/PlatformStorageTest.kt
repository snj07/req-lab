package com.reqlab.ui.shared.platform

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the desktop [PlatformStorage] implementation.
 *
 * Verifies the two-tier storage strategy:
 *  - Values ≤ 64 KB  → stored in Java Preferences (chunked if > 7 KB)
 *  - Values > 64 KB  → written to a file in ~/.reqlab/pstore/
 *
 * Uses isolated test keys so real app data is never touched.
 */
class PlatformStorageTest {

    private val SMALL_KEY  = "test.platform.small"
    private val LARGE_KEY  = "test.platform.large"

    private val pstoreDir = File(System.getProperty("user.home"), ".reqlab/pstore")

    /** Mirrors the internal key → filename mapping in PlatformStorage. */
    private fun backingFile(key: String): File =
        File(pstoreDir, key.replace(".", "_").replace("/", "_"))

    @Before
    fun setUp() = cleanTestKeys()

    @After
    fun tearDown() = cleanTestKeys()

    private fun cleanTestKeys() {
        PlatformStorage.remove(SMALL_KEY)
        PlatformStorage.remove(LARGE_KEY)
    }

    // ── Small values (stay in Preferences) ──────────────────────────────────

    @Test
    fun small_value_roundtrips_through_preferences_without_creating_a_file() {
        val value = "Hello ReqLab"
        PlatformStorage.putString(SMALL_KEY, value)

        assertEquals(value, PlatformStorage.getString(SMALL_KEY))
        assertFalse(
            backingFile(SMALL_KEY).exists(),
            "Values well below the file threshold must NOT create a backing file",
        )
    }

    @Test
    fun getString_returns_null_for_absent_key() {
        assertNull(PlatformStorage.getString("test.platform.definitely.absent.key.xyz"))
    }

    // ── Large values (routed to file) ────────────────────────────────────────

    @Test
    fun large_value_roundtrips_length_and_content_exactly() {
        // 200 KB — well above the 64 KB file-routing threshold
        val value = "x".repeat(200_000)
        PlatformStorage.putString(LARGE_KEY, value)

        val loaded = PlatformStorage.getString(LARGE_KEY)
        assertEquals(value.length, loaded?.length, "Large value length must roundtrip")
        assertEquals(value, loaded, "Large value content must roundtrip exactly")
    }

    @Test
    fun large_value_is_written_to_pstore_backing_file() {
        PlatformStorage.putString(LARGE_KEY, "y".repeat(100_000))

        assertTrue(
            backingFile(LARGE_KEY).exists(),
            "Values > 64 KB must be persisted to ~/.reqlab/pstore/ as a file",
        )
    }

    @Test
    fun overwriting_a_large_value_with_another_large_value_roundtrips_the_second_value() {
        PlatformStorage.putString(LARGE_KEY, "A".repeat(100_000))
        val second = "B".repeat(150_000)
        PlatformStorage.putString(LARGE_KEY, second)

        assertEquals(second, PlatformStorage.getString(LARGE_KEY),
            "Second large write must fully replace the first value")
    }

    // ── Transitions between small and large ──────────────────────────────────

    @Test
    fun switching_from_large_to_small_deletes_the_backing_file_and_returns_new_value() {
        PlatformStorage.putString(LARGE_KEY, "z".repeat(200_000))
        assertTrue(backingFile(LARGE_KEY).exists(), "Precondition: backing file must exist after large write")

        PlatformStorage.putString(LARGE_KEY, "now small")

        assertFalse(
            backingFile(LARGE_KEY).exists(),
            "Backing file must be deleted when the value drops below the file threshold",
        )
        assertEquals("now small", PlatformStorage.getString(LARGE_KEY))
    }

    // ── remove() ────────────────────────────────────────────────────────────

    @Test
    fun remove_deletes_backing_file_and_subsequent_read_returns_null() {
        PlatformStorage.putString(LARGE_KEY, "a".repeat(200_000))
        assertTrue(backingFile(LARGE_KEY).exists(), "Precondition: backing file must exist")

        PlatformStorage.remove(LARGE_KEY)

        assertFalse(
            backingFile(LARGE_KEY).exists(),
            "remove() must delete the backing file for large-value keys",
        )
        assertNull(PlatformStorage.getString(LARGE_KEY),
            "getString after remove must return null")
    }

    @Test
    fun remove_on_small_value_clears_the_entry() {
        PlatformStorage.putString(SMALL_KEY, "to be removed")
        PlatformStorage.remove(SMALL_KEY)

        assertNull(PlatformStorage.getString(SMALL_KEY))
    }
}
