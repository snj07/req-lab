package com.reqlab.ui.shared.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Tests for [I18nProvider] translation resolution.
 */
class I18nProviderTest {

    @Test
    fun english_resolves_known_keys() {
        val provider = I18nProvider(AppLanguage.EN)
        assertEquals("Send", provider.get("send"))
        assertEquals("Cancel", provider.get("cancel"))
        assertEquals("Save", provider.get("save"))
        assertEquals("Delete", provider.get("delete"))
    }

    @Test
    fun spanish_resolves_known_keys() {
        val provider = I18nProvider(AppLanguage.ES)
        assertEquals("Enviar", provider.get("send"))
        assertEquals("Cancelar", provider.get("cancel"))
        assertEquals("Guardar", provider.get("save"))
        assertEquals("Eliminar", provider.get("delete"))
    }

    @Test
    fun french_resolves_known_keys() {
        val provider = I18nProvider(AppLanguage.FR)
        assertEquals("Envoyer", provider.get("send"))
        assertEquals("Annuler", provider.get("cancel"))
        assertEquals("Sauvegarder", provider.get("save"))
        assertEquals("Supprimer", provider.get("delete"))
    }

    @Test
    fun german_resolves_known_keys() {
        val provider = I18nProvider(AppLanguage.DE)
        assertEquals("Senden", provider.get("send"))
        assertEquals("Abbrechen", provider.get("cancel"))
        assertEquals("Speichern", provider.get("save"))
        assertEquals("Löschen", provider.get("delete"))
    }

    @Test
    fun unknown_key_falls_back_to_key_itself() {
        val provider = I18nProvider(AppLanguage.EN)
        assertEquals("this_key_does_not_exist", provider.get("this_key_does_not_exist"))
    }

    @Test
    fun missing_translation_falls_back_to_english() {
        // All languages should resolve "app_name" to "ReqLab" via fallback
        for (lang in AppLanguage.entries) {
            val provider = I18nProvider(lang)
            assertEquals("ReqLab", provider.get("app_name"))
        }
    }

    @Test
    fun all_languages_have_code_and_displayName() {
        for (lang in AppLanguage.entries) {
            assertTrue(lang.code.isNotBlank(), "Language code should not be blank")
            assertTrue(lang.displayName.isNotBlank(), "Display name should not be blank")
        }
    }

    @Test
    fun different_languages_produce_different_translations() {
        val en = I18nProvider(AppLanguage.EN)
        val es = I18nProvider(AppLanguage.ES)
        assertNotEquals(en.get("send"), es.get("send"))
    }

    private fun assertTrue(condition: Boolean, message: String) {
        kotlin.test.assertTrue(condition, message)
    }
}
