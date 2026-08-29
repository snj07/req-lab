package com.reqlab.core.network.mcp

import com.reqlab.core.model.McpConnectionConfig
import java.security.SecureRandom

actual val mcpStdioSupported: Boolean = false

actual fun createStdioTransport(config: McpConnectionConfig): McpTransport =
    throw UnsupportedOperationException("MCP stdio is not supported on Android")

actual fun mcpSecureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}

actual val mcpInteractiveOAuthSupported: Boolean = false

actual suspend fun mcpOpenAuthorizeUrlAndAwaitCode(authorizeUrl: String, redirectPort: Int): String =
    throw UnsupportedOperationException("Interactive OAuth is not supported on Android in v1")
