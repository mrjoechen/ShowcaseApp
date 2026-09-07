package com.alpha.showcase.common.utils

import io.github.jan.supabase.auth.SessionManager

internal expect fun createSupabaseSessionManager(supabaseUrl: String): SessionManager?

internal expect fun clearLegacySupabaseBrowserSession(supabaseUrl: String)

internal fun supabaseNativeSessionStorageKey(supabaseUrl: String): String =
    // SupabaseClientBuilder strips the scheme before SDK default settings derive their key.
    "sb-${supabaseUrl.substringAfterLast("//").removeSuffix("/").replace('/', '-').replace('.', '-')}-session"
