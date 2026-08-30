package com.reqlab.server

import java.io.File

/**
 * Puts `sample-server` on the login-shell PATH as an MCP stdio process.
 * The Gradle HTTP start script and stdio mock share a binary; this shim
 * always launches stdio.
 */
object McpCommandShim {
    fun unixScript(launcherPath: String): String =
        "#!/usr/bin/env bash\nexec \"$launcherPath\" \"\$@\"\n"

    fun windowsCmd(launcherPath: String): String =
        "@echo off\r\n\"$launcherPath\" --stdio %*\r\n"

    fun installDir(homeDir: String, osName: String): File {
        val windows = osName.lowercase().contains("win")
        return if (windows) File(homeDir, "AppData/Local/ReqLab/bin")
        else File(homeDir, ".local/bin")
    }

    fun install(
        homeDir: String,
        osName: String,
        unixLauncher: File,
        windowsLauncher: File,
    ): File {
        val windows = osName.lowercase().contains("win")
        val dir = installDir(homeDir, osName)
        dir.mkdirs()
        val dest = File(dir, if (windows) "sample-server.cmd" else "sample-server")
        dest.writeText(
            if (windows) windowsCmd(windowsLauncher.canonicalPath)
            else unixScript(unixLauncher.canonicalPath),
        )
        dest.setExecutable(true, false)
        return dest
    }
}
