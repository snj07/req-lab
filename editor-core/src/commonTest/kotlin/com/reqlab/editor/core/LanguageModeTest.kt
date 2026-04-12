package com.reqlab.editor.core

import kotlin.test.*

class LanguageModeTest {

    @Test
    fun fromContentTypeJson() {
        assertEquals(LanguageMode.JSON, LanguageMode.fromContentType("application/json"))
        assertEquals(LanguageMode.JSON, LanguageMode.fromContentType("application/vnd.api+json"))
    }

    @Test
    fun fromContentTypeXml() {
        assertEquals(LanguageMode.XML, LanguageMode.fromContentType("application/xml"))
        assertEquals(LanguageMode.XML, LanguageMode.fromContentType("text/xml"))
    }

    @Test
    fun fromContentTypeHtml() {
        assertEquals(LanguageMode.HTML, LanguageMode.fromContentType("text/html"))
    }

    @Test
    fun fromContentTypeJavascript() {
        assertEquals(LanguageMode.JAVASCRIPT, LanguageMode.fromContentType("application/javascript"))
        assertEquals(LanguageMode.JAVASCRIPT, LanguageMode.fromContentType("text/javascript"))
    }

    @Test
    fun fromContentTypePlainText() {
        assertEquals(LanguageMode.PLAIN_TEXT, LanguageMode.fromContentType("text/plain"))
        assertEquals(LanguageMode.PLAIN_TEXT, LanguageMode.fromContentType(null))
        assertEquals(LanguageMode.PLAIN_TEXT, LanguageMode.fromContentType("application/octet-stream"))
    }

    @Test
    fun allModesRegistered() {
        LanguageRegistry.registerBuiltins()
        LanguageMode.entries.forEach { mode ->
            assertNotNull(LanguageRegistry.getProvider(mode), "Provider for $mode not registered")
        }
    }

    @Test
    fun registryDetectFromExtension() {
        LanguageRegistry.registerBuiltins()
        assertEquals(LanguageMode.JSON, LanguageRegistry.detectFromExtension("json"))
        assertEquals(LanguageMode.XML, LanguageRegistry.detectFromExtension("xml"))
        assertEquals(LanguageMode.HTML, LanguageRegistry.detectFromExtension("html"))
        assertEquals(LanguageMode.JAVASCRIPT, LanguageRegistry.detectFromExtension("js"))
    }

    @Test
    fun registryDetectFromMimeType() {
        LanguageRegistry.registerBuiltins()
        assertEquals(LanguageMode.JSON, LanguageRegistry.detectFromMimeType("application/json"))
        assertEquals(LanguageMode.XML, LanguageRegistry.detectFromMimeType("application/xml"))
        assertEquals(LanguageMode.HTML, LanguageRegistry.detectFromMimeType("text/html"))
    }
}
