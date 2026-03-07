package com.reqlab.ui.desktop.persistence

import com.reqlab.ui.desktop.state.AppSettings
import com.reqlab.ui.desktop.state.AppTheme
import java.util.prefs.Preferences

/**
 * Persists [AppSettings] to the platform's user-preferences store (on macOS this is
 * ~/Library/Preferences/com.reqlab.ui.desktop.SettingsRepository.plist).
 *
 * Call [load] on startup and [save] after any settings mutation.
 */
object SettingsRepository {

    private val prefs: Preferences =
        Preferences.userNodeForPackage(SettingsRepository::class.java)

    // ── Load ───────────────────────────────────────────────────────────────

    /** Applies persisted values into [settings]. Missing keys fall back to field defaults. */
    fun load(settings: AppSettings) {
        settings.autoSaveRequests    = prefs.getBoolean("autoSaveRequests",   settings.autoSaveRequests)
        settings.confirmBeforeDelete = prefs.getBoolean("confirmBeforeDelete", settings.confirmBeforeDelete)
        settings.defaultTimeoutSec   = prefs.getInt("defaultTimeoutSec",      settings.defaultTimeoutSec)

        settings.theme = safeEnumOf<AppTheme>(
            prefs.get("theme", settings.theme.name),
            settings.theme
        )

        settings.requestTimeoutSec = prefs.getInt("requestTimeoutSec", settings.requestTimeoutSec)
        settings.followRedirects    = prefs.getBoolean("followRedirects", settings.followRedirects)

        settings.proxyEnabled = prefs.getBoolean("proxyEnabled", settings.proxyEnabled)
        settings.httpProxy    = prefs.get("httpProxy",  settings.httpProxy)
        settings.httpsProxy   = prefs.get("httpsProxy", settings.httpsProxy)
    }

    // ── Save ───────────────────────────────────────────────────────────────

    /** Persists all [settings] fields. Flushed immediately. */
    fun save(settings: AppSettings) {
        prefs.putBoolean("autoSaveRequests",   settings.autoSaveRequests)
        prefs.putBoolean("confirmBeforeDelete", settings.confirmBeforeDelete)
        prefs.putInt("defaultTimeoutSec",       settings.defaultTimeoutSec)
        prefs.put("theme",                      settings.theme.name)
        prefs.putInt("requestTimeoutSec",       settings.requestTimeoutSec)
        prefs.putBoolean("followRedirects",     settings.followRedirects)
        prefs.putBoolean("proxyEnabled",        settings.proxyEnabled)
        prefs.put("httpProxy",                  settings.httpProxy)
        prefs.put("httpsProxy",                 settings.httpsProxy)
        prefs.flush()
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private inline fun <reified T : Enum<T>> safeEnumOf(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}
