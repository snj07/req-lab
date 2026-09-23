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
        "settings.selectedEnvName",
        "settings.allowJson5InJsonBodies",
        "settings.showEditorPositionIndicator",
        "settings.environmentDialogWidthDp",
        "settings.environmentDialogHeightDp",
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
        assertTrue(settings.allowJson5InJsonBodies)
        assertFalse(settings.showEditorPositionIndicator)
        assertEquals(720f, settings.environmentDialogWidthDp)
        assertEquals(560f, settings.environmentDialogHeightDp)
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
            allowJson5InJsonBodies = false
            showEditorPositionIndicator = false
            environmentDialogWidthDp = 840f
            environmentDialogHeightDp = 620f
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
        assertFalse(loaded.allowJson5InJsonBodies)
        assertFalse(loaded.showEditorPositionIndicator)
        assertEquals(840f, loaded.environmentDialogWidthDp)
        assertEquals(620f, loaded.environmentDialogHeightDp)
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

    @Test
    fun selected_env_name_defaults_to_empty_when_not_stored() {
        val settings = AppSettings()
        SettingsRepository.load(settings)

        assertEquals("", settings.selectedEnvName)
    }

    @Test
    fun selected_env_name_round_trips() {
        val original = AppSettings().apply { selectedEnvName = "Staging" }
        SettingsRepository.save(original)

        val loaded = AppSettings()
        SettingsRepository.load(loaded)

        assertEquals("Staging", loaded.selectedEnvName)
    }

    @Test
    fun json5_in_json_bodies_defaults_to_true() {
        val settings = AppSettings()
        SettingsRepository.load(settings)
        assertTrue(settings.allowJson5InJsonBodies)
    }

    @Test
    fun json5_in_json_bodies_round_trips_false_and_true() {
        SettingsRepository.save(AppSettings().apply { allowJson5InJsonBodies = false })
        val off = AppSettings()
        SettingsRepository.load(off)
        assertFalse(off.allowJson5InJsonBodies)

        SettingsRepository.save(AppSettings().apply { allowJson5InJsonBodies = true })
        val on = AppSettings()
        SettingsRepository.load(on)
        assertTrue(on.allowJson5InJsonBodies)
    }

    @Test
    fun editor_position_indicator_defaults_off_and_round_trips() {
        val defaults = AppSettings()
        SettingsRepository.load(defaults)
        assertFalse(defaults.showEditorPositionIndicator)

        SettingsRepository.save(AppSettings().apply { showEditorPositionIndicator = true })
        val shown = AppSettings()
        SettingsRepository.load(shown)
        assertTrue(shown.showEditorPositionIndicator)
    }

    @Test
    fun selected_env_name_survives_full_settings_round_trip() {
        val original = AppSettings().apply {
            theme = AppTheme.LIGHT
            scriptPrefix = "api"
            selectedEnvName = "Production"
        }
        SettingsRepository.save(original)

        val loaded = AppSettings()
        SettingsRepository.load(loaded)

        assertEquals(AppTheme.LIGHT, loaded.theme)
        assertEquals("api", loaded.scriptPrefix)
        assertEquals("Production", loaded.selectedEnvName)
    }

    @Test
    fun environment_dialog_size_round_trips() {
        SettingsRepository.save(
            AppSettings().apply {
                environmentDialogWidthDp = 812f
                environmentDialogHeightDp = 533f
            },
        )
        val loaded = AppSettings()
        SettingsRepository.load(loaded)
        assertEquals(812f, loaded.environmentDialogWidthDp)
        assertEquals(533f, loaded.environmentDialogHeightDp)
    }

    @Test
    fun invalid_environment_dialog_size_falls_back_to_defaults() {
        PlatformStorage.putString("settings.environmentDialogWidthDp", "not-a-number")
        PlatformStorage.putString("settings.environmentDialogHeightDp", "-40")
        val settings = AppSettings()
        SettingsRepository.load(settings)
        assertEquals(720f, settings.environmentDialogWidthDp)
        assertEquals(560f, settings.environmentDialogHeightDp)
    }

    @Test
    fun non_positive_or_non_finite_environment_dialog_size_falls_back_to_defaults() {
        PlatformStorage.putString("settings.environmentDialogWidthDp", "0")
        PlatformStorage.putString("settings.environmentDialogHeightDp", "NaN")
        val zeroAndNan = AppSettings()
        SettingsRepository.load(zeroAndNan)
        assertEquals(720f, zeroAndNan.environmentDialogWidthDp)
        assertEquals(560f, zeroAndNan.environmentDialogHeightDp)

        PlatformStorage.putString("settings.environmentDialogWidthDp", "Infinity")
        PlatformStorage.putString("settings.environmentDialogHeightDp", "-Infinity")
        val inf = AppSettings()
        SettingsRepository.load(inf)
        assertEquals(720f, inf.environmentDialogWidthDp)
        assertEquals(560f, inf.environmentDialogHeightDp)
    }
}
