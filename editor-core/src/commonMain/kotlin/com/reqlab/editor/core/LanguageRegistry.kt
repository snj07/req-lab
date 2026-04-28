package com.reqlab.editor.core

object LanguageRegistry {
    private val builtins: Map<LanguageMode, LanguageModeProvider> = mapOf(
        LanguageMode.PLAIN_TEXT to PlainTextMode,
        LanguageMode.JSON to JsonMode,
        LanguageMode.XML to XmlMode,
        LanguageMode.HTML to HtmlMode,
        LanguageMode.JAVASCRIPT to JavaScriptMode,
        LanguageMode.GRAPHQL to GraphQLMode,
    )

    @kotlin.concurrent.Volatile
    private var providers: Map<LanguageMode, LanguageModeProvider> = builtins

    fun register(provider: LanguageModeProvider) {
        providers = providers + (provider.mode to provider)
    }

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
        providers = providers + builtins
    }

    fun clear() {
        providers = emptyMap()
    }
}
