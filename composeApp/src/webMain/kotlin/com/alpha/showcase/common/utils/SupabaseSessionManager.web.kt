package com.alpha.showcase.common.utils

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.serialization.json.Json

private const val LEGACY_APP_SESSION_KEY = "supabase_auth_session"
private const val LEGACY_DEVICE_PREFERENCES_KEY = "device_prefs"

private val sessionJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * Browser bearer tokens live only for the current tab. JavaScript running in the same origin can
 * still access sessionStorage, so CSP remains an essential second boundary.
 */
private class BrowserSessionManager(
    private val sessionStorageKey: String,
) : SessionManager {
    private var memorySession: UserSession? = null

    override suspend fun saveSession(session: UserSession) {
        memorySession = session
        runCatching {
            sessionStorage.setItem(sessionStorageKey, sessionJson.encodeToString(session))
        }
    }

    override suspend fun loadSession(): UserSession? {
        val encoded = runCatching {
            sessionStorage.getItem(sessionStorageKey)
        }.getOrNull() ?: return memorySession

        return runCatching {
            sessionJson.decodeFromString<UserSession>(encoded)
        }.onSuccess {
            memorySession = it
        }.onFailure {
            runCatching { sessionStorage.removeItem(sessionStorageKey) }
        }.getOrNull() ?: memorySession
    }

    override suspend fun deleteSession() {
        memorySession = null
        runCatching { sessionStorage.removeItem(sessionStorageKey) }
    }
}

internal actual fun createSupabaseSessionManager(supabaseUrl: String): SessionManager? =
    BrowserSessionManager("showcase_${supabaseHostKey(supabaseUrl)}_session_v2")

internal actual fun clearLegacySupabaseBrowserSession(supabaseUrl: String) {
    val defaultSupabaseKey = "sb-${supabaseHostKey(supabaseUrl)}-session"
    listOf(
        defaultSupabaseKey,
        // Also clear the full-URL key used by the reference app's custom session storage.
        "sb-${supabaseUrl.removeSuffix("/").replace('/', '-').replace('.', '-')}-session",
        "session",
        LEGACY_APP_SESSION_KEY,
        LEGACY_DEVICE_PREFERENCES_KEY,
    ).forEach { key ->
        runCatching { localStorage.removeItem(key) }
    }
}

private fun supabaseHostKey(supabaseUrl: String): String =
    supabaseUrl
        .substringAfter("//")
        .substringBefore('/')
        .replace('.', '-')
