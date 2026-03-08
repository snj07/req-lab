package com.reqlab.ui.shared.persistence

import com.reqlab.ui.shared.platform.PlatformStorage
import com.reqlab.ui.shared.state.AppSettings
import com.reqlab.ui.shared.state.AppTheme

/**
 * Persists [AppSettings] using [PlatformStorage].
 * Call [load] on startup and [save] after any settings mutation.
 */
object SettingsRepository {

    private const val PREFIX = "settings."

    // ── Load ───────────────────────────────────────────────────────────────

    /** Applies persisted values into [settings]. Missing keys fall back to field defaults. */
    fun load(settings: AppSettings) {
        settings.autoSaveRequests    = getBool("autoSaveRequests", settings.autoSaveRequests)
        settings.confirmBeforeDelete = getBool("confirmBeforeDelete", settings.confirmBeforeDelete)
        settings.defaultTimeoutSec   = getInt("defaultTimeoutSec", settings.defaultTimeoutSec)

        settings.theme = safeEnumOf(
            PlatformStorage.getString(PREFIX + "theme") ?: settings.theme.name,
            settings.theme,
        )

        settings.requestTimeoutSec = getInt("requestTimeoutSec", settings.requestTimeoutSec)
        settings.followRedirects   = getBool("followRedirects", settings.followRedirects)

        settings.proxyEnabled = getBool("proxyEnabled", settings.proxyEnabled)
        settings.httpProxy    = PlatformStorage.getString(PREFIX + "httpProxy") ?: settings.httpProxy
        settings.httpsProxy   = PlatformStorage.getString(PREFIX + "httpsProxy") ?: settings.httpsProxy
    }

    // ── Save ───────────────────────────────────────────────────────────────

    /** Persists all [settings] fields. */
    fun save(settings: AppSettings) {
        putBool("autoSaveRequests", settings.autoSaveRequests)
        putBool("confirmBeforeDelete", settings.confirmBeforeDelete)
        putInt("defaultTimeoutSec", settings.defaultTimeoutSec)
        PlatformStorage.putString(PREFIX + "theme", settings.theme.name)
        putInt("requestTimeoutSec", settings.requestTimeoutSec)
        putBool("followRedirects", settings.followRedirects)
        putBool("proxyEnabled", settings.proxyEnabled)
        PlatformStorage.putString(PREFIX + "httpProxy", settings.httpProxy)
        PlatformStorage.putString(PREFIX + "httpsProxy", settings.httpsProxy)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun getBool(key: String, default: Boolean): Boolean =
        PlatformStorage.getString(PREFIX + key)?.toBooleanStrictOrNull() ?: default

    private fun getInt(key: String, default: Int): Int =
        PlatformStorage.getString(PREFIX + key)?.toIntOrNull() ?: default

    private fun putBool(key: String, value: Boolean) =
        PlatformStorage.putString(PREFIX + key, value.toString())

    private fun putInt(key: String, value: Int) =
        PlatformStorage.putString(PREFIX + key, value.toString())

    private inline fun <reified T : Enum<T>> safeEnumOf(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}
