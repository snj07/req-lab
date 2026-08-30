package com.reqlab.server

import java.io.File

fun main(args: Array<String>) {
    require(args.size >= 2) { "usage: unixLauncher windowsLauncher" }
    val dest = McpCommandShim.install(
        homeDir = System.getProperty("user.home"),
        osName = System.getProperty("os.name"),
        unixLauncher = File(args[0]),
        windowsLauncher = File(args[1]),
    )
    println("Installed MCP stdio command: ${dest.absolutePath}")
    println("Command: sample-server")
}
