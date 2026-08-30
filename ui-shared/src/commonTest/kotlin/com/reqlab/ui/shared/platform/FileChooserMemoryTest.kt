package com.reqlab.ui.shared.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileChooserMemoryTest {
    @Test
    fun parent_unix_file() {
        assertEquals("/Users/me/collections", FileChooserMemory.parentPath("/Users/me/collections/api.json"))
    }

    @Test
    fun parent_unix_root_child() {
        assertEquals("/", FileChooserMemory.parentPath("/api.json"))
    }

    @Test
    fun parent_windows_file() {
        assertEquals("C:\\Users\\me\\collections", FileChooserMemory.parentPath("C:\\Users\\me\\collections\\api.json"))
    }

    @Test
    fun parent_windows_drive_root_child() {
        assertEquals("C:\\", FileChooserMemory.parentPath("C:\\api.json"))
    }

    @Test
    fun opens_stored_directory_when_it_still_exists() {
        assertEquals(
            "/Users/me/imports",
            FileChooserMemory.directoryToOpen("/Users/me/imports") { it == "/Users/me/imports" },
        )
    }

    @Test
    fun opens_parent_when_stored_path_was_a_file() {
        val dirs = setOf("/Users/me/imports")
        assertEquals(
            "/Users/me/imports",
            FileChooserMemory.directoryToOpen("/Users/me/imports/gone.json") { it in dirs },
        )
    }

    @Test
    fun ignores_missing_directory_so_os_default_is_used() {
        assertNull(FileChooserMemory.directoryToOpen("D:\\removed") { false })
        assertNull(FileChooserMemory.directoryToOpen(null) { true })
        assertNull(FileChooserMemory.directoryToOpen("  ") { true })
    }

    @Test
    fun remembers_parent_of_selected_file() {
        val dirs = setOf("/home/me/qa-tests/fixtures")
        assertEquals(
            "/home/me/qa-tests/fixtures",
            FileChooserMemory.directoryToRemember("/home/me/qa-tests/fixtures/reqlab-test-collection.json") { it in dirs },
        )
    }

    @Test
    fun remembers_windows_directory_of_selected_file() {
        val dirs = setOf("C:\\Users\\me\\Docs")
        assertEquals(
            "C:\\Users\\me\\Docs",
            FileChooserMemory.directoryToRemember("C:\\Users\\me\\Docs\\workspace.json") { it in dirs },
        )
    }
}
