package com.reqlab.core.network.mcp

/**
 * Builds the argv passed to the stdio process.
 *
 * The command field is a full command line (parsed with quoting);
 * the first token is the executable, the rest plus [args] are arguments.
 */
internal fun mcpStdioArgv(command: String, args: List<String>): List<String> {
    return tokenizeCommandLine(command) + args
}

/**
 * Resolves the executable: login-shell PATH first, then an explicit relative
 * path against [workingDir] / [userDir] if that file exists.
 */
internal fun resolveStdioArgv(
    command: String,
    args: List<String>,
    workingDir: String?,
    userDir: String,
    pathEnv: String = "",
    pathSeparator: String = ":",
    extraExtensions: List<String> = emptyList(),
    exists: (String) -> Boolean,
): List<String> {
    val argv = mcpStdioArgv(command, args)
    if (argv.isEmpty()) return argv
    val exe = resolveStdioExecutable(
        exe = argv.first(),
        workingDir = workingDir,
        userDir = userDir,
        pathEnv = pathEnv,
        pathSeparator = pathSeparator,
        extraExtensions = extraExtensions,
        exists = exists,
    )
    return listOf(exe) + argv.drop(1)
}

internal fun tokenizeCommandLine(command: String): List<String> {
    val out = mutableListOf<String>()
    val buf = StringBuilder()
    var quote: Char? = null
    var i = 0
    val line = command.trim()
    while (i < line.length) {
        val c = line[i]
        when {
            quote != null -> when {
                c == quote -> quote = null
                c == '\\' && quote == '"' && i + 1 < line.length -> {
                    buf.append(line[i + 1])
                    i++
                }
                else -> buf.append(c)
            }
            c == '\'' || c == '"' -> quote = c
            c.isWhitespace() -> {
                if (buf.isNotEmpty()) {
                    out += buf.toString()
                    buf.clear()
                }
            }
            else -> buf.append(c)
        }
        i++
    }
    if (buf.isNotEmpty()) out += buf.toString()
    return out
}

internal fun resolveStdioExecutable(
    exe: String,
    workingDir: String?,
    userDir: String,
    pathEnv: String = "",
    pathSeparator: String = ":",
    extraExtensions: List<String> = emptyList(),
    exists: (String) -> Boolean,
): String {
    if (exe.isEmpty()) return exe
    val isPath = exe.contains('/') || exe.contains('\\')
    val base = workingDir?.takeIf { it.isNotBlank() } ?: userDir
    if (!isPath) {
        whichOnPath(exe, pathEnv, pathSeparator, exists, extraExtensions)?.let { return it }
        return exe
    }
    if (exists(exe)) return exe
    val againstBase = joinStdioPath(base, exe)
    if (exists(againstBase)) return againstBase
    return exe
}

internal fun whichOnPath(
    exe: String,
    pathEnv: String,
    pathSeparator: String,
    exists: (String) -> Boolean,
    extraExtensions: List<String> = emptyList(),
): String? {
    if (exe.isEmpty() || pathEnv.isEmpty()) return null
    val names = listOf(exe) + extraExtensions.map { exe + it }
    for (dir in pathEnv.split(pathSeparator)) {
        if (dir.isBlank()) continue
        for (name in names) {
            val candidate = joinStdioPath(dir, name)
            if (exists(candidate)) return candidate
        }
    }
    return null
}

internal fun joinStdioPath(base: String, relative: String): String {
    val b = base.trimEnd('/', '\\')
    val r = relative.trimStart('/', '\\')
    if (b.isEmpty()) return r
    return "$b/$r"
}

internal fun mergePath(preferred: String, fallback: String, separator: String): String {
    if (preferred.isBlank()) return fallback
    if (fallback.isBlank()) return preferred
    val seen = linkedSetOf<String>()
    (preferred.split(separator) + fallback.split(separator)).forEach { part ->
        if (part.isNotBlank()) seen += part
    }
    return seen.joinToString(separator)
}
