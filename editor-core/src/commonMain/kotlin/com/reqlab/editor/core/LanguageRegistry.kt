package com.reqlab.editor.core

object LanguageRegistry {
    private val providers = mutableMapOf<LanguageMode, LanguageModeProvider>()

    fun register(provider: LanguageModeProvider) { providers[provider.mode] = provider }

    fun getProvider(mode: LanguageMode): LanguageModeProvider =
        providers[mode] ?: providers[LanguageMode.PLAIN_TEXT] ?: PlainTextMode

    fun allProviders(): List<LanguageModeProvider> = providers.values.toList()
    fun hasProvider(mode: LanguageMode): Boolean = mode in providers

    fun detectFromExtension(extension: String): LanguageMode {
        val ext = extension.lowercase().removePrefix(".")
        for ((mode, provider) in providers) {
            if (ext in provider.fileExtensions) return mode
        }
        return LanguageMode.PLAIN_TEXT
    }

    fun detectFromMimeType(mimeType: String): LanguageMode {
        val type = mimeType.lowercase()
        for ((mode, provider) in providers) {
            if (provider.mimeTypes.any { it in type }) return mode
        }
        return LanguageMode.PLAIN_TEXT
    }

    fun registerBuiltins() {
        register(PlainTextMode)
        register(JsonMode)
        register(XmlMode)
        register(HtmlMode)
        register(JavaScriptMode)
    }

    fun clear() { providers.clear() }
}
