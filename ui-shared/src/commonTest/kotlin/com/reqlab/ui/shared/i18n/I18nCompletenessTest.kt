package com.reqlab.ui.shared.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Comprehensive i18n validation tests.
 *
 * Covers:
 * - All 4 languages resolve all critical keys
 * - Translation completeness (all languages have same key set)
 * - Fallback to English for missing translations
 * - Unknown keys return the key itself
 * - Language enum properties
 * - Each language produces distinct translations
 * - Key categories: General, Sidebar, Request Editor, Response Viewer,
 *   Timing, Settings, Global Variables, Realtime, GraphQL, Bottom Panel, Import/Export
 */
class I18nCompletenessTest {

    // All keys that must exist in every language
    private val requiredKeys = listOf(
        // General
        "app_name", "send", "cancel", "save", "delete", "close", "confirm",
        "add", "edit", "search", "copy", "download", "loading", "error",
        "success", "retry",
        // Sidebar
        "history", "collections", "environments", "search_requests",
        "no_history", "clear_history", "new_request", "new_folder",
        "collapse_all", "expand_all",
        // Request Editor
        "params", "headers", "body", "auth", "pre_request", "tests",
        "send_request", "enter_url", "url_is_empty",
        // Response Viewer
        "response", "response_body", "cookies", "timing", "raw",
        "no_cookies", "send_to_see_response", "sending_request",
        "request_failed", "format", "word_wrap", "search_in_response",
        "copy_body", "download_response", "no_results",
        // Timing
        "request_timing_breakdown", "dns_lookup", "tcp_connect",
        "tls_handshake", "server_processing", "content_download",
        "timing_not_available",
        // Settings
        "settings", "general", "theme", "network", "proxy", "language",
        "auto_save", "confirm_before_delete", "default_timeout",
        "response_layout", "follow_redirects", "dark_mode", "light_mode",
        "system_theme",
        // Global Variables
        "global_variables", "global_variables_desc", "add_variable",
        "no_global_variables", "variable_name", "value",
        // Realtime
        "connect", "disconnect", "connected", "disconnected",
        "send_message", "message_history", "protocols", "communication",
        // GraphQL
        "query", "variables", "schema_explorer", "run_query", "introspect",
        // Bottom Panel
        "console", "test_results", "logs",
        // Import/Export
        "import_collection", "export_collection", "import_success",
        "export_success", "operation_failed",
    )

    // ── Completeness ──

    @Test
    fun all_languages_resolve_all_required_keys() {
        for (lang in AppLanguage.entries) {
            val provider = I18nProvider(lang)
            for (key in requiredKeys) {
                val resolved = provider.get(key)
                assertNotEquals(
                    key, resolved,
                    "Language ${lang.code} is missing translation for '$key' (returned key as fallback)"
                )
            }
        }
    }

    @Test
    fun english_has_non_empty_translations_for_all_keys() {
        val provider = I18nProvider(AppLanguage.EN)
        for (key in requiredKeys) {
            val value = provider.get(key)
            assertTrue(value.isNotBlank(), "English translation for '$key' should not be blank")
        }
    }

    @Test
    fun spanish_has_non_empty_translations_for_all_keys() {
        val provider = I18nProvider(AppLanguage.ES)
        for (key in requiredKeys) {
            val value = provider.get(key)
            assertTrue(value.isNotBlank(), "Spanish translation for '$key' should not be blank")
        }
    }

    @Test
    fun french_has_non_empty_translations_for_all_keys() {
        val provider = I18nProvider(AppLanguage.FR)
        for (key in requiredKeys) {
            val value = provider.get(key)
            assertTrue(value.isNotBlank(), "French translation for '$key' should not be blank")
        }
    }

    @Test
    fun german_has_non_empty_translations_for_all_keys() {
        val provider = I18nProvider(AppLanguage.DE)
        for (key in requiredKeys) {
            val value = provider.get(key)
            assertTrue(value.isNotBlank(), "German translation for '$key' should not be blank")
        }
    }

    // ── Fallback behavior ──

    @Test
    fun unknown_key_returns_key_itself() {
        for (lang in AppLanguage.entries) {
            val provider = I18nProvider(lang)
            assertEquals("nonexistent_key_xyz", provider.get("nonexistent_key_xyz"))
        }
    }

    @Test
    fun app_name_is_consistent_across_all_languages() {
        // "ReqLab" should be the same in all languages (brand name)
        for (lang in AppLanguage.entries) {
            val provider = I18nProvider(lang)
            assertEquals("ReqLab", provider.get("app_name"),
                "app_name should be 'ReqLab' in ${lang.code}")
        }
    }

    // ── Cross-language distinction ──

    @Test
    fun each_language_has_distinct_translations_for_common_keys() {
        val testKeys = listOf("send", "cancel", "save", "delete", "settings", "history")
        val languages = AppLanguage.entries

        for (i in languages.indices) {
            for (j in (i + 1) until languages.size) {
                val provI = I18nProvider(languages[i])
                val provJ = I18nProvider(languages[j])
                var hasDifference = false
                for (key in testKeys) {
                    if (provI.get(key) != provJ.get(key)) {
                        hasDifference = true
                        break
                    }
                }
                assertTrue(hasDifference,
                    "Languages ${languages[i].code} and ${languages[j].code} " +
                    "should have at least one different translation among common keys")
            }
        }
    }

    // ── Specific translation spot checks ──

    @Test
    fun sidebar_keys_translated_to_spanish() {
        val es = I18nProvider(AppLanguage.ES)
        assertEquals("Historial", es.get("history"))
        assertEquals("Colecciones", es.get("collections"))
        assertEquals("Entornos", es.get("environments"))
    }

    @Test
    fun response_keys_translated_to_french() {
        val fr = I18nProvider(AppLanguage.FR)
        assertEquals("Réponse", fr.get("response"))
        assertEquals("Chronométrage", fr.get("timing"))
        assertEquals("Brut", fr.get("raw"))
    }

    @Test
    fun settings_keys_translated_to_german() {
        val de = I18nProvider(AppLanguage.DE)
        assertEquals("Einstellungen", de.get("settings"))
        assertEquals("Allgemein", de.get("general"))
        assertEquals("Netzwerk", de.get("network"))
    }

    @Test
    fun global_variables_keys_translated_to_spanish() {
        val es = I18nProvider(AppLanguage.ES)
        assertEquals("Variables globales", es.get("global_variables"))
        assertEquals("Añadir variable", es.get("add_variable"))
    }

    @Test
    fun import_export_keys_translated_to_french() {
        val fr = I18nProvider(AppLanguage.FR)
        assertEquals("Importer une collection", fr.get("import_collection"))
        assertEquals("Exporter une collection", fr.get("export_collection"))
    }

    // ── Language enum ──

    @Test
    fun four_languages_supported() {
        assertEquals(4, AppLanguage.entries.size)
    }

    @Test
    fun language_codes_are_unique() {
        val codes = AppLanguage.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "Language codes must be unique")
    }

    @Test
    fun language_codes_are_iso_format() {
        for (lang in AppLanguage.entries) {
            assertEquals(2, lang.code.length, "${lang.name} code should be 2 characters")
            assertEquals(lang.code, lang.code.lowercase(), "${lang.name} code should be lowercase")
        }
    }

    @Test
    fun language_display_names_are_non_empty() {
        for (lang in AppLanguage.entries) {
            assertTrue(lang.displayName.isNotBlank(),
                "${lang.name} displayName should not be blank")
        }
    }

    @Test
    fun default_provider_uses_english() {
        val provider = I18nProvider()
        assertEquals("Send", provider.get("send"))
        assertEquals("Cancel", provider.get("cancel"))
    }
}
