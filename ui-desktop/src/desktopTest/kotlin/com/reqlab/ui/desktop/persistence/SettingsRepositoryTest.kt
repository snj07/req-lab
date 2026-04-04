package com.reqlab.ui.shared.persistence

import com.reqlab.ui.shared.platform.PlatformStorage
import com.reqlab.ui.shared.i18n.AppLanguage
import com.reqlab.ui.shared.state.AppSettings
import com.reqlab.ui.shared.state.AppTheme
import com.reqlab.ui.shared.state.ResponseLayout
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    private val settingsKeys = listOf(
        "settings.autoSaveRequests",
        "settings.confirmBeforeDelete",
        "settings.defaultTimeoutSec",
        "settings.responseLayout",
        "settings.theme",
        "settings.language",
        "settings.requestTimeoutSec",
        "settings.followRedirects",
        "settings.collectionsExpanded",
        "settings.environmentsExpanded",
        "settings.proxyEnabled",
        "settings.httpProxy",
        "settings.httpsProxy",
        "settings.scriptPrefix",
    )

    @Before
    fun setUp() {
        settingsKeys.forEach { PlatformStorage.remove(it) }
    }

    @After
    fun tearDown() {
        settingsKeys.forEach { PlatformStorage.remove(it) }
    }

    // ── Defaults ────────────────────────────────────────────────────────────

    @Test
    fun defaults_are_returned_when_no_prefs_stored() {
        val settings = AppSettings()
        SettingsRepository.load(settings)

        assertFalse(settings.autoSaveRequests)
        assertTrue(settings.confirmBeforeDelete)
        assertEquals(30, settings.defaultTimeoutSec)
        assertEquals(ResponseLayout.RIGHT, settings.responseLayout)
        assertEquals(AppTheme.DARK, settings.theme)
        assertEquals(AppLanguage.EN, settings.language)
        assertEquals(30, settings.requestTimeoutSec)
        assertTrue(settings.followRedirects)
        assertFalse(settings.collectionsExpanded)
        assertFalse(settings.environmentsExpanded)
        assertFalse(settings.proxyEnabled)
        assertEquals("", settings.httpProxy)
        assertEquals("", settings.httpsProxy)
    }

    // ── Round-trip ──────────────────────────────────────────────────────────

    @Test
    fun save_and_load_roundtrip_all_fields() {
        val original = AppSettings().apply {
            autoSaveRequests    = false
            confirmBeforeDelete = false
            defaultTimeoutSec   = 60
            responseLayout      = ResponseLayout.BOTTOM
            theme               = AppTheme.LIGHT
            language            = AppLanguage.FR
            requestTimeoutSec   = 45
            followRedirects     = false
            collectionsExpanded = true
            environmentsExpanded = true
            proxyEnabled        = true
            httpProxy           = "http://proxy.example.com:8080"
            httpsProxy          = "https://proxy.example.com:8443"
            scriptPrefix        = "api"
        }

        SettingsRepository.save(original)

        val loaded = AppSettings()
        SettingsRepository.load(loaded)

        assertFalse(loaded.autoSaveRequests)
        assertFalse(loaded.confirmBeforeDelete)
        assertEquals(60, loaded.defaultTimeoutSec)
        assertEquals(ResponseLayout.BOTTOM, loaded.responseLayout)
        assertEquals(AppTheme.LIGHT, loaded.theme)
        assertEquals(AppLanguage.FR, loaded.language)
        assertEquals(45, loaded.requestTimeoutSec)
        assertFalse(loaded.followRedirects)
        assertTrue(loaded.collectionsExpanded)
        assertTrue(loaded.environmentsExpanded)
        assertTrue(loaded.proxyEnabled)
        assertEquals("http://proxy.example.com:8080", loaded.httpProxy)
        assertEquals("https://proxy.example.com:8443", loaded.httpsProxy)
        assertEquals("api", loaded.scriptPrefix)
    }

    // ── Theme enum ──────────────────────────────────────────────────────────

    @Test
    fun system_theme_round_trips() {
        val settings = AppSettings().apply { theme = AppTheme.SYSTEM }
        SettingsRepository.save(settings)

        val loaded = AppSettings()
        SettingsRepository.load(loaded)

        assertEquals(AppTheme.SYSTEM, loaded.theme)
    }

    @Test
    fun invalid_theme_value_falls_back_to_default_dark() {
        PlatformStorage.putString("settings.theme", "NOT_A_VALID_THEME")

        // Default field value is DARK
        val settings = AppSettings()
        SettingsRepository.load(settings)

        assertEquals(AppTheme.DARK, settings.theme)
    }

    // ── Partial save ────────────────────────────────────────────────────────

    @Test
    fun saved_boolean_reflects_change_from_default() {
        val original = AppSettings().apply { autoSaveRequests = false }
        SettingsRepository.save(original)

        val loaded = AppSettings() // default is false
        SettingsRepository.load(loaded)

        assertFalse(loaded.autoSaveRequests)
    }

    @Test
    fun saved_int_reflects_change_from_default() {
        val original = AppSettings().apply { requestTimeoutSec = 120 }
        SettingsRepository.save(original)

        val loaded = AppSettings() // default is 30
        SettingsRepository.load(loaded)

        assertEquals(120, loaded.requestTimeoutSec)
    }

    @Test
    fun script_prefix_round_trips_when_only_script_setting_changes() {
        val original = AppSettings().apply { scriptPrefix = "customPrefix" }
        SettingsRepository.save(original)

        val loaded = AppSettings()
        SettingsRepository.load(loaded)

        assertEquals("customPrefix", loaded.scriptPrefix)
    }
}
