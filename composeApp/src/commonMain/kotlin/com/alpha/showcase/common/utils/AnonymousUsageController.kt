package com.alpha.showcase.common.utils

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import setSentryEnabled as setSentryCollectionEnabled

internal class AnonymousUsageLifecycleController(
    private val initializeSupabase: suspend () -> Unit,
    private val ensureSupabaseAuth: suspend () -> Unit,
    private val setAnalyticsEnabled: (Boolean) -> Unit,
    private val setSentryEnabled: suspend (Boolean) -> Unit,
    private val setAnonymousCollectionEnabled: suspend (Boolean) -> Unit,
    private val authenticationScope: CoroutineScope,
) {
    private val lifecycleMutex = Mutex()
    private val consentStateLock = SynchronizedObject()
    private var desiredConsentEnabled = false
    private var authenticationJob: Job? = null

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
                authenticationJob?.cancel()
                authenticationJob = null
                // Disabling optional collectors must not depend on initializing or authenticating
                // the configuration service. Both operations can suspend or fail independently.
                setAnonymousCollectionEnabled(false)
                setSentryEnabled(false)
                return@withLock
            }

            initializeSupabase()
            if (!isCurrentConsent(true)) return@withLock

            setSentryEnabled(true)
            if (!isCurrentConsent(true)) return@withLock
            setAnonymousCollectionEnabled(true)
            synchronized(consentStateLock) {
                if (desiredConsentEnabled) {
                    setAnalyticsEnabled(true)
                }
            }
            // Consent activation must not wait for session restoration or a network response.
            // Reporting remains gated on authentication and can resume via the session observer.
            if (isCurrentConsent(true) && authenticationJob?.isActive != true) {
                authenticationJob = authenticationScope.launch {
                    if (!isCurrentConsent(true)) return@launch
                    try {
                        ensureSupabaseAuth()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Log.w("AnonymousUsage", "Failed to authenticate optional collection")
                    }
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
 * Protected service configuration may keep an authenticated, pseudonymous Supabase session even
 * when optional collection is disabled. Consent controls reporting only; it never broadens table
 * access or determines whether configuration requests are authenticated.
 */
object AnonymousUsageController {
    private val authenticationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val controller = AnonymousUsageLifecycleController(
        authenticationScope = authenticationScope,
        initializeSupabase = {
            Supabase.enableAuthenticated()
        },
        ensureSupabaseAuth = { SupabaseAuth.ensureAuthenticated() },
        setAnalyticsEnabled = { enabled ->
            Analytics.getInstance().setAnonymousUsage(enabled)
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
