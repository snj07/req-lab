package com.reqlab.core.network.mcp

import kotlin.test.Test
import kotlin.test.assertEquals

class McpStdioArgvTest {
    @Test
    fun splits_command_and_flag_when_args_empty() {
        assertEquals(
            listOf("sample-server", "--stdio"),
            mcpStdioArgv("sample-server --stdio", emptyList()),
        )
    }

    @Test
    fun appends_explicit_args_after_tokenized_command() {
        assertEquals(
            listOf("npx", "-y", "@modelcontextprotocol/server-everything"),
            mcpStdioArgv("npx", listOf("-y", "@modelcontextprotocol/server-everything")),
        )
    }

    @Test
    fun respects_quoted_paths_with_spaces() {
        assertEquals(
            listOf("/Applications/My Server/bin/mcp", "--stdio"),
            tokenizeCommandLine("\"/Applications/My Server/bin/mcp\" --stdio"),
        )
    }

    @Test
    fun path_lookup_resolves_command_on_path() {
        val argv = resolveStdioArgv(
            command = "sample-server",
            args = emptyList(),
            workingDir = null,
            userDir = "/repo",
            pathEnv = "/usr/local/bin",
            exists = { it == "/usr/local/bin/sample-server" },
        )
        assertEquals(listOf("/usr/local/bin/sample-server"), argv)
    }

    @Test
    fun bare_command_is_unchanged_when_not_on_path() {
        val argv = resolveStdioArgv(
            command = "sample-server",
            args = emptyList(),
            workingDir = null,
            userDir = "/repo/ui-desktop",
            exists = { false },
        )
        assertEquals(listOf("sample-server"), argv)
    }

    @Test
    fun resolves_relative_path_against_user_dir_when_file_exists() {
        val argv = resolveStdioArgv(
            command = "sample-server/mcp-stdio",
            args = emptyList(),
            workingDir = null,
            userDir = "/repo",
            exists = { it == "/repo/sample-server/mcp-stdio" },
        )
        assertEquals(listOf("/repo/sample-server/mcp-stdio"), argv)
    }

    @Test
    fun does_not_walk_parent_directories_for_relative_paths() {
        val argv = resolveStdioArgv(
            command = "sample-server/mcp-stdio",
            args = emptyList(),
            workingDir = null,
            userDir = "/repo/ui-desktop",
            exists = { it == "/repo/sample-server/mcp-stdio" },
        )
        assertEquals(listOf("sample-server/mcp-stdio"), argv)
    }

    @Test
    fun leaves_path_commands_unchanged_when_missing() {
        assertEquals(
            listOf("reqlab-no-such-binary"),
            resolveStdioArgv("reqlab-no-such-binary", emptyList(), null, "/repo") { _ -> false },
        )
    }

    @Test
    fun which_on_path_finds_first_match() {
        assertEquals(
            "/opt/homebrew/bin/npx",
            whichOnPath("npx", "/opt/homebrew/bin:/usr/bin", ":", { it == "/opt/homebrew/bin/npx" }),
        )
    }

    @Test
    fun merge_path_prefers_login_shell_then_process_path() {
        assertEquals(
            "/opt/homebrew/bin:/usr/bin:/bin",
            mergePath("/opt/homebrew/bin:/usr/bin", "/usr/bin:/bin", ":"),
        )
    }
}
