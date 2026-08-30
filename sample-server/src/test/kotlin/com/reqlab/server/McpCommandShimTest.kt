package com.reqlab.server

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpCommandShimTest {

    @Test
    fun unix_shim_execs_repo_launcher() {
        val script = McpCommandShim.unixScript("/repo/sample-server/mcp-stdio")
        assertTrue(script.startsWith("#!/usr/bin/env bash"))
        assertTrue(script.contains("exec \"/repo/sample-server/mcp-stdio\" \"\$@\""))
    }

    @Test
    fun windows_shim_passes_stdio_flag() {
        val cmd = McpCommandShim.windowsCmd("C:\\repo\\sample-server.bat")
        assertTrue(cmd.contains("\"C:\\repo\\sample-server.bat\" --stdio %*"))
    }

    @Test
    fun unix_install_dir_is_local_bin() {
        val dir = McpCommandShim.installDir("/Users/me", "Mac OS X")
        assertTrue(dir.path.replace('\\', '/').endsWith(".local/bin"))
    }

    @Test
    fun windows_install_dir_is_local_reqlab_bin() {
        val dir = McpCommandShim.installDir("C:\\Users\\me", "Windows 11")
        assertTrue(dir.path.replace('\\', '/').endsWith("AppData/Local/ReqLab/bin"))
    }

    @Test
    fun install_writes_executable_shim_into_temp_home() {
        val home = File.createTempFile("reqlab-mcp-home", "").apply {
            delete()
            mkdirs()
        }
        val launcher = File.createTempFile("mcp-stdio", "").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        try {
            val dest = McpCommandShim.install(home.absolutePath, "Mac OS X", launcher, launcher)
            assertEquals(File(home, ".local/bin/sample-server").canonicalFile, dest.canonicalFile)
            assertTrue(dest.canExecute())
            assertTrue(dest.readText().contains(launcher.canonicalPath))
        } finally {
            home.deleteRecursively()
            launcher.delete()
        }
    }
}
