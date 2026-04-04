package com.reqlab.ui.shared.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Supported application languages. */
enum class AppLanguage(val code: String, val displayName: String) {
    EN("en", "English"),
    ES("es", "Español"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
}

object Strings {
    val appName: String @Composable @ReadOnlyComposable get() = t("app_name")
    val send: String @Composable @ReadOnlyComposable get() = t("send")
    val cancel: String @Composable @ReadOnlyComposable get() = t("cancel")
    val save: String @Composable @ReadOnlyComposable get() = t("save")
    val delete: String @Composable @ReadOnlyComposable get() = t("delete")
    val close: String @Composable @ReadOnlyComposable get() = t("close")
    val confirm: String @Composable @ReadOnlyComposable get() = t("confirm")
    val add: String @Composable @ReadOnlyComposable get() = t("add")
    val edit: String @Composable @ReadOnlyComposable get() = t("edit")
    val search: String @Composable @ReadOnlyComposable get() = t("search")
    val copy: String @Composable @ReadOnlyComposable get() = t("copy")
    val download: String @Composable @ReadOnlyComposable get() = t("download")
    val loading: String @Composable @ReadOnlyComposable get() = t("loading")
    val error: String @Composable @ReadOnlyComposable get() = t("error")
    val success: String @Composable @ReadOnlyComposable get() = t("success")
    val retry: String @Composable @ReadOnlyComposable get() = t("retry")

    val history: String @Composable @ReadOnlyComposable get() = t("history")
    val collections: String @Composable @ReadOnlyComposable get() = t("collections")
    val environments: String @Composable @ReadOnlyComposable get() = t("environments")
    val searchRequests: String @Composable @ReadOnlyComposable get() = t("search_requests")
    val noHistory: String @Composable @ReadOnlyComposable get() = t("no_history")
    val clearHistory: String @Composable @ReadOnlyComposable get() = t("clear_history")
    val newRequest: String @Composable @ReadOnlyComposable get() = t("new_request")
    val newFolder: String @Composable @ReadOnlyComposable get() = t("new_folder")
    val collapseAll: String @Composable @ReadOnlyComposable get() = t("collapse_all")
    val expandAll: String @Composable @ReadOnlyComposable get() = t("expand_all")
    val noEnvironmentsConfigured: String @Composable @ReadOnlyComposable get() = t("no_environments_configured")
    val createEnvironment: String @Composable @ReadOnlyComposable get() = t("create_environment")
    val noRequestSelected: String @Composable @ReadOnlyComposable get() = t("no_request_selected")
    val openRequestToStart: String @Composable @ReadOnlyComposable get() = t("open_request_to_start")

    val params: String @Composable @ReadOnlyComposable get() = t("params")
    val headers: String @Composable @ReadOnlyComposable get() = t("headers")
    val body: String @Composable @ReadOnlyComposable get() = t("body")
    val auth: String @Composable @ReadOnlyComposable get() = t("auth")
    val preRequest: String @Composable @ReadOnlyComposable get() = t("pre_request")
    val tests: String @Composable @ReadOnlyComposable get() = t("tests")
    val sendRequest: String @Composable @ReadOnlyComposable get() = t("send_request")
    val enterUrl: String @Composable @ReadOnlyComposable get() = t("enter_url")
    val urlIsEmpty: String @Composable @ReadOnlyComposable get() = t("url_is_empty")

    val response: String @Composable @ReadOnlyComposable get() = t("response")
    val responseBody: String @Composable @ReadOnlyComposable get() = t("response_body")
    val cookies: String @Composable @ReadOnlyComposable get() = t("cookies")
    val timing: String @Composable @ReadOnlyComposable get() = t("timing")
    val raw: String @Composable @ReadOnlyComposable get() = t("raw")
    val noCookies: String @Composable @ReadOnlyComposable get() = t("no_cookies")
    val sendToSeeResponse: String @Composable @ReadOnlyComposable get() = t("send_to_see_response")
    val sendingRequest: String @Composable @ReadOnlyComposable get() = t("sending_request")
    val requestFailed: String @Composable @ReadOnlyComposable get() = t("request_failed")
    val format: String @Composable @ReadOnlyComposable get() = t("format")
    val wordWrap: String @Composable @ReadOnlyComposable get() = t("word_wrap")
    val searchInResponse: String @Composable @ReadOnlyComposable get() = t("search_in_response")
    val copyBody: String @Composable @ReadOnlyComposable get() = t("copy_body")
    val downloadResponse: String @Composable @ReadOnlyComposable get() = t("download_response")
    val noResults: String @Composable @ReadOnlyComposable get() = t("no_results")

    val requestTimingBreakdown: String @Composable @ReadOnlyComposable get() = t("request_timing_breakdown")
    val dnsLookup: String @Composable @ReadOnlyComposable get() = t("dns_lookup")
    val tcpConnect: String @Composable @ReadOnlyComposable get() = t("tcp_connect")
    val tlsHandshake: String @Composable @ReadOnlyComposable get() = t("tls_handshake")
    val serverProcessing: String @Composable @ReadOnlyComposable get() = t("server_processing")
    val contentDownload: String @Composable @ReadOnlyComposable get() = t("content_download")
    val timingNotAvailable: String @Composable @ReadOnlyComposable get() = t("timing_not_available")

    val settings: String @Composable @ReadOnlyComposable get() = t("settings")
    val general: String @Composable @ReadOnlyComposable get() = t("general")
    val theme: String @Composable @ReadOnlyComposable get() = t("theme")
    val network: String @Composable @ReadOnlyComposable get() = t("network")
    val proxy: String @Composable @ReadOnlyComposable get() = t("proxy")
    val language: String @Composable @ReadOnlyComposable get() = t("language")
    val autoSave: String @Composable @ReadOnlyComposable get() = t("auto_save")
    val confirmBeforeDelete: String @Composable @ReadOnlyComposable get() = t("confirm_before_delete")
    val defaultTimeout: String @Composable @ReadOnlyComposable get() = t("default_timeout")
    val responseLayout: String @Composable @ReadOnlyComposable get() = t("response_layout")
    val followRedirects: String @Composable @ReadOnlyComposable get() = t("follow_redirects")
    val darkMode: String @Composable @ReadOnlyComposable get() = t("dark_mode")
    val lightMode: String @Composable @ReadOnlyComposable get() = t("light_mode")
    val systemTheme: String @Composable @ReadOnlyComposable get() = t("system_theme")

    val globalVariables: String @Composable @ReadOnlyComposable get() = t("global_variables")
    val globalVariablesDesc: String @Composable @ReadOnlyComposable get() = t("global_variables_desc")
    val addVariable: String @Composable @ReadOnlyComposable get() = t("add_variable")
    val noGlobalVariables: String @Composable @ReadOnlyComposable get() = t("no_global_variables")
    val variableName: String @Composable @ReadOnlyComposable get() = t("variable_name")
    val value: String @Composable @ReadOnlyComposable get() = t("value")

    val connect: String @Composable @ReadOnlyComposable get() = t("connect")
    val disconnect: String @Composable @ReadOnlyComposable get() = t("disconnect")
    val connected: String @Composable @ReadOnlyComposable get() = t("connected")
    val disconnected: String @Composable @ReadOnlyComposable get() = t("disconnected")
    val sendMessage: String @Composable @ReadOnlyComposable get() = t("send_message")
    val messageHistory: String @Composable @ReadOnlyComposable get() = t("message_history")
    val protocols: String @Composable @ReadOnlyComposable get() = t("protocols")
    val communication: String @Composable @ReadOnlyComposable get() = t("communication")

    val query: String @Composable @ReadOnlyComposable get() = t("query")
    val variables: String @Composable @ReadOnlyComposable get() = t("variables")
    val schemaExplorer: String @Composable @ReadOnlyComposable get() = t("schema_explorer")
    val runQuery: String @Composable @ReadOnlyComposable get() = t("run_query")
    val introspect: String @Composable @ReadOnlyComposable get() = t("introspect")

    val console: String @Composable @ReadOnlyComposable get() = t("console")
    val testResults: String @Composable @ReadOnlyComposable get() = t("test_results")
    val logs: String @Composable @ReadOnlyComposable get() = t("logs")

    val importCollection: String @Composable @ReadOnlyComposable get() = t("import_collection")
    val exportCollection: String @Composable @ReadOnlyComposable get() = t("export_collection")
    val importSuccess: String @Composable @ReadOnlyComposable get() = t("import_success")
    val exportSuccess: String @Composable @ReadOnlyComposable get() = t("export_success")
    val operationFailed: String @Composable @ReadOnlyComposable get() = t("operation_failed")

    @Composable
    @ReadOnlyComposable
    fun t(key: String): String = LocalI18n.current.get(key)
}

class I18nProvider(private val language: AppLanguage = AppLanguage.EN) {
    private val translations: Map<String, String> = TranslationBundleStore.forLanguage(language)
    private val fallback: Map<String, String> = TranslationBundleStore.forLanguage(AppLanguage.EN)

    fun get(key: String): String = translations[key] ?: fallback[key] ?: key
}

val LocalI18n = compositionLocalOf { I18nProvider() }

private object TranslationBundleStore {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = mutableMapOf<AppLanguage, Map<String, String>>()

    fun forLanguage(language: AppLanguage): Map<String, String> =
        cache.getOrPut(language) { decodeMap(loadTranslationBundle(language.code)) }

    private fun decodeMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.parseToJsonElement(raw)
                .jsonObject
                .mapValues { (_, v) -> v.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
    }
}
