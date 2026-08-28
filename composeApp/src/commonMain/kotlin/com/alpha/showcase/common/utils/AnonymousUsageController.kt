package com.alpha.showcase.common.utils

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import setSentryEnabled as setSentryCollectionEnabled

internal class AnonymousUsageLifecycleController(
    private val initializeSupabase: suspend () -> Unit,
    private val ensureSupabaseAuth: suspend () -> Unit,
    private val setAnalyticsEnabled: (Boolean) -> Unit,
    private val prepareAnalytics: () -> Unit = {},
    private val setSentryEnabled: suspend (Boolean) -> Unit,
    private val setAnonymousCollectionEnabled: suspend (Boolean) -> Unit,
) {
    private val lifecycleMutex = Mutex()
    private val consentStateLock = SynchronizedObject()
    private var desiredConsentEnabled = false

    /**
     * Records the newest consent decision without suspending. An opt-out disables analytics
     * while the caller is still on the initiating stack, before lifecycle cleanup can wait.
     */
    fun beginConsentChange(enabled: Boolean) {
        synchronized(consentStateLock) {
            desiredConsentEnabled = enabled
            if (!enabled) {
                setAnalyticsEnabled(false)
            }
        }
    }

    suspend fun completeConsentChange(enabled: Boolean) {
        lifecycleMutex.withLock {
            if (!isCurrentConsent(enabled)) return@withLock

            if (!enabled) {
                // Disabling optional collectors must not depend on initializing or authenticating
                // the configuration service. Both operations can suspend or fail independently.
                setAnonymousCollectionEnabled(false)
                setSentryEnabled(false)
                return@withLock
            }

            initializeSupabase()
            ensureSupabaseAuth()
            if (!isCurrentConsent(true)) return@withLock

            // Device registration waits for Analytics.awaitDeviceId(). Prepare that local
            // value without enabling collection, otherwise first-time opt-in deadlocks here.
            prepareAnalytics()
            setSentryEnabled(true)
            if (!isCurrentConsent(true)) return@withLock
            setAnonymousCollectionEnabled(true)
            synchronized(consentStateLock) {
                if (desiredConsentEnabled) {
                    setAnalyticsEnabled(true)
                }
            }
        }
    }

    suspend fun applyConsent(enabled: Boolean) {
        beginConsentChange(enabled)
        completeConsentChange(enabled)
    }

    private fun isCurrentConsent(enabled: Boolean): Boolean =
        synchronized(consentStateLock) { desiredConsentEnabled == enabled }
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
        prepareAnalytics = {
            Analytics.getInstance().prepareAnonymousUsage()
        },
        setSentryEnabled = { enabled ->
            try {
                setSentryCollectionEnabled(enabled)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val action = if (enabled) "enable" else "disable"
                Log.w("AnonymousUsage", "Failed to $action Sentry: ${error.message}")
            }
        },
        setAnonymousCollectionEnabled = { enabled ->
            SupabaseAuth.setCollectionEnabled(enabled)
        },
    )

    internal fun beginConsentChange(enabled: Boolean) = controller.beginConsentChange(enabled)

    internal suspend fun completeConsentChange(enabled: Boolean) =
        controller.completeConsentChange(enabled)

    suspend fun applyConsent(enabled: Boolean) = controller.applyConsent(enabled)
}
