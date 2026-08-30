package com.reqlab.core.network.mcp

import com.reqlab.core.model.McpConnectionConfig
import kotlin.random.Random

actual val mcpStdioSupported: Boolean = false

actual fun createStdioTransport(config: McpConnectionConfig): McpTransport =
    throw UnsupportedOperationException("MCP stdio is not supported on iOS")

actual fun mcpSecureRandomBytes(size: Int): ByteArray = Random.Default.nextBytes(size)

actual val mcpInteractiveOAuthSupported: Boolean = false

actual suspend fun mcpOpenAuthorizeUrlAndAwaitCode(authorizeUrl: String, redirectPort: Int): String =
    throw UnsupportedOperationException("Interactive OAuth is not supported on iOS in v1")
