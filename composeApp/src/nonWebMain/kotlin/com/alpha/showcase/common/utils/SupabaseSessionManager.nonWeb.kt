package com.alpha.showcase.common.utils

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.MemorySessionManager

internal actual fun createSupabaseSessionManager(supabaseUrl: String): SessionManager? =
    runCatching {
        // Retain this app's previous SDK-default key, rather than copying another app's key.
        SettingsSessionManager(key = supabaseNativeSessionStorageKey(supabaseUrl))
    }.getOrElse {
        Log.w("Supabase", "Auth storage unavailable; using an in-memory session")
        MemorySessionManager()
    }

internal actual fun clearLegacySupabaseBrowserSession(supabaseUrl: String) = Unit
