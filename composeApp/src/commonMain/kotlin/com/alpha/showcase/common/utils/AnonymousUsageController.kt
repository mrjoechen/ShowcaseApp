package com.alpha.showcase.common.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import setSentryEnabled as setSentryCollectionEnabled

internal class AnonymousUsageLifecycleController(
    private val initializeSupabase: suspend () -> Unit,
    private val ensureSupabaseAuth: suspend () -> Unit,
    private val setAnalyticsEnabled: (Boolean) -> Unit,
    private val setSentryEnabled: suspend (Boolean) -> Unit,
    private val setAnonymousCollectionEnabled: suspend (Boolean) -> Unit,
) {
    private val lifecycleMutex = Mutex()

    suspend fun applyConsent(enabled: Boolean) {
        lifecycleMutex.withLock {
            initializeSupabase()
            ensureSupabaseAuth()
            if (enabled) {
                setAnalyticsEnabled(true)
                setSentryEnabled(true)
                setAnonymousCollectionEnabled(true)
            } else {
                setAnonymousCollectionEnabled(false)
                setAnalyticsEnabled(false)
                setSentryEnabled(false)
            }
        }
    }
}

/**
 * Keeps Supabase available for required configuration reads while applying the user's
 * optional anonymous-usage consent only to collection and reporting services.
 */
object AnonymousUsageController {
    private val controller = AnonymousUsageLifecycleController(
        initializeSupabase = {
            Supabase.enable()
        },
        ensureSupabaseAuth = SupabaseAuth::enable,
        setAnalyticsEnabled = { enabled ->
            Analytics.getInstance().setAnonymousUsage(enabled)
        },
        setSentryEnabled = { enabled ->
            runCatching { setSentryCollectionEnabled(enabled) }
                .onFailure {
                    val action = if (enabled) "enable" else "disable"
                    Log.w("AnonymousUsage", "Failed to $action Sentry: ${it.message}")
                }
        },
        setAnonymousCollectionEnabled = { enabled ->
            SupabaseAuth.setCollectionEnabled(enabled)
        },
    )

    suspend fun applyConsent(enabled: Boolean) = controller.applyConsent(enabled)
}
