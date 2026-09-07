package com.alpha.showcase.common.utils

import com.alpha.showcase.common.storage.objectStoreOf
import getPlatform
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

internal class SupabaseAuthInitializer(
    private val awaitInitialization: suspend () -> Unit,
    private val currentUserId: () -> String?,
    private val signInAnonymously: suspend () -> Unit,
    private val isNotAuthenticated: () -> Boolean,
) {
    private val mutex = Mutex()

    suspend fun ensureUserId(): String = mutex.withLock {
        // A local restoration deadline is an authentication failure, not cancellation of the
        // caller. withTimeoutOrNull still propagates cancellation from an enclosing job/timeout.
        check(withTimeoutOrNull(60_000) {
            awaitInitialization()
            true
        } == true) { "Supabase session restoration timed out" }
        currentUserId()?.let { return@withLock it }
        // RefreshFailure also ends SDK initialization, but its stored identity can recover.
        // Never replace it with a new anonymous account while the SDK is retrying refresh.
        check(isNotAuthenticated()) { "Supabase session restoration is still pending" }
        signInAnonymously()
        checkNotNull(currentUserId()) {
            "Supabase anonymous authentication completed without a user"
        }
    }
}

internal class SupabaseAuthReportingController(
    private val setAnalyticsUserId: (String?) -> Unit,
    private val registerDevice: suspend () -> Unit,
    private val reportingScope: CoroutineScope,
    private val onRegistrationError: (Throwable) -> Unit = {},
) {
    private val reportingMutex = Mutex()
    private var collectionEnabled = false
    private var authenticatedUserId: String? = null
    private var reportedUserId: String? = null
    private var registrationGeneration = 0L
    private var registrationJob: Job? = null

    suspend fun onAuthenticated(userId: String) {
        reportingMutex.withLock {
            if (authenticatedUserId != userId) {
                cancelRegistrationLocked()
                reportedUserId = null
            }
            authenticatedUserId = userId
            scheduleDeviceReportIfNeededLocked()
        }
    }

    suspend fun setCollectionEnabled(enabled: Boolean) {
        reportingMutex.withLock {
            if (collectionEnabled == enabled) {
                if (enabled) scheduleDeviceReportIfNeededLocked()
                return
            }
            collectionEnabled = enabled

            if (enabled) {
                scheduleDeviceReportIfNeededLocked()
            } else {
                cancelRegistrationLocked()
                reportedUserId = null
                setAnalyticsUserId(null)
            }
        }
    }

    /** Must be called while [reportingMutex] is held. */
    private fun scheduleDeviceReportIfNeededLocked() {
        val userId = authenticatedUserId ?: return
        if (!collectionEnabled || reportedUserId == userId) return
        if (registrationJob?.isActive == true) return

        setAnalyticsUserId(userId)
        val generation = ++registrationGeneration
        registrationJob = reportingScope.launch {
            val failure = try {
                registerDevice()
                null
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                error
            }

            val shouldReportFailure = reportingMutex.withLock {
                if (generation != registrationGeneration) return@withLock false

                registrationJob = null
                if (
                    failure == null &&
                    collectionEnabled &&
                    authenticatedUserId == userId
                ) {
                    reportedUserId = userId
                }
                failure != null && collectionEnabled && authenticatedUserId == userId
            }
            if (failure != null && shouldReportFailure) {
                onRegistrationError(failure)
            }
        }
    }

    /** Cancels without joining so an opt-out never waits for a stalled network operation. */
    private fun cancelRegistrationLocked() {
        registrationGeneration += 1
        registrationJob?.cancel()
        registrationJob = null
    }
}

object SupabaseAuth {

    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleMutex = Mutex()
    private val legacySessionStore = objectStoreOf<String>("supabase_auth_session")

    private val _authState = MutableStateFlow<AuthState>(AuthState.Disabled)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var authJob: Job? = null

    @Volatile
    private var enabled = false

    private val initializer = SupabaseAuthInitializer(
        awaitInitialization = {
            // Wait for SDK storage restoration before creating another anonymous account.
            requireClient().auth.awaitInitialization()
        },
        currentUserId = { Supabase.authenticatedClientOrNull?.auth?.currentUserOrNull()?.id },
        signInAnonymously = { requireClient().auth.signInAnonymously() },
        isNotAuthenticated = { requireClient().auth.sessionStatus.value is SessionStatus.NotAuthenticated },
    )

    private fun requireClient(): SupabaseClient =
        checkNotNull(Supabase.authenticatedClientOrNull) { "Supabase client is unavailable" }

    private val reportingController = SupabaseAuthReportingController(
        setAnalyticsUserId = { userId ->
            val analytics = Analytics.getInstance()
            if (userId == null) analytics.clearUserId() else analytics.setUserId(userId)
        },
        registerDevice = {
            Supabase.registerDevice(getPlatform().getDevice())
        },
        reportingScope = authScope,
        onRegistrationError = {
            Log.w("SupabaseAuth", "Failed to register device")
        },
    )

    suspend fun enable() {
        val client = Supabase.enableAuthenticated() ?: return
        lifecycleMutex.withLock {
            if (authJob?.isActive == true) return@withLock

            enabled = true
            _authState.value = AuthState.Initializing
            authJob = authScope.launch {
                client.auth.sessionStatus.collect { status ->
                    if (!enabled) return@collect

                    when (status) {
                        is SessionStatus.Authenticated -> status.session.user?.id?.let {
                            handleAuthenticated(it)
                        }
                        is SessionStatus.NotAuthenticated -> {
                            _authState.value = AuthState.Initializing
                        }
                        is SessionStatus.Initializing -> {
                            _authState.value = AuthState.Initializing
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    suspend fun disable() {
        enabled = false
        reportingController.setCollectionEnabled(false)
        val jobToCancel = lifecycleMutex.withLock {
            authJob.also { authJob = null }
        }
        jobToCancel?.cancelAndJoin()

        Supabase.authenticatedClientOrNull?.let { client ->
            runCatching { client.auth.signOut() }
                .onFailure { Log.w("SupabaseAuth", "Failed to sign out") }
        }
        Supabase.disableAuthenticated()

        // Earlier versions wrote access and refresh tokens to this redundant plaintext cache.
        runCatching { legacySessionStore.delete() }
            .onFailure { Log.w("SupabaseAuth", "Failed to delete legacy session") }

        _authState.value = AuthState.Disabled
    }

    suspend fun setCollectionEnabled(enabled: Boolean) {
        reportingController.setCollectionEnabled(enabled)
    }

    suspend fun ensureAuthenticated(): Boolean {
        return try {
            ensureAuthenticatedUserId()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            false
        }
    }

    suspend fun ensureAuthenticatedUserId(): String {
        enable()
        return try {
            initializer.ensureUserId().also { handleAuthenticated(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _authState.value = AuthState.Error("Supabase authentication failed")
            throw error
        }
    }

    private suspend fun handleAuthenticated(userId: String) {
        if (!enabled) return

        _authState.value = AuthState.Authenticated(userId)
        reportingController.onAuthenticated(userId)
    }

    fun getUserId(): String? = (authState.value as? AuthState.Authenticated)?.userId
}

sealed class AuthState {
    data object Disabled : AuthState()
    data object Initializing : AuthState()
    data class Authenticated(val userId: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
