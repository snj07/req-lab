package com.reqlab.core.network.mcp

import kotlin.test.Test
import kotlin.test.assertEquals

class PkceTest {
    @Test
    fun sha256_empty_string() {
        val digest = sha256(ByteArray(0)).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", digest)
    }

    @Test
    fun rfc7636_s256_challenge() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val challenge = sha256(verifier.encodeToByteArray()).mcpBase64(urlSafe = true, padding = false)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }
}
