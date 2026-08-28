package com.alpha.showcase.common.utils

import com.alpha.showcase.common.storage.objectStoreOf
import getPlatform
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

internal suspend fun awaitSupabaseAuthentication(
    authStates: Flow<AuthState>,
    timeoutMillis: Long = 15_000,
): Boolean {
    val terminalState = withTimeoutOrNull(timeoutMillis) {
        authStates.first { state ->
            state is AuthState.Authenticated ||
                state is AuthState.Error ||
                state is AuthState.Disabled
        }
    }
    return terminalState is AuthState.Authenticated
}

internal class SupabaseAuthReportingController(
    private val setAnalyticsUserId: (String?) -> Unit,
    private val registerDevice: suspend () -> Unit,
    private val onRegistrationError: (Throwable) -> Unit = {},
) {
    private val reportingMutex = Mutex()
    private var collectionEnabled = false
    private var authenticatedUserId: String? = null
    private var reportedUserId: String? = null

    suspend fun onAuthenticated(userId: String) {
        reportingMutex.withLock {
            authenticatedUserId = userId
            reportDeviceIfNeeded()
        }
    }

    suspend fun setCollectionEnabled(enabled: Boolean) {
        reportingMutex.withLock {
            if (collectionEnabled == enabled) {
                if (enabled) reportDeviceIfNeeded()
                return
            }
            collectionEnabled = enabled

            if (enabled) {
                reportDeviceIfNeeded()
            } else {
                reportedUserId = null
                setAnalyticsUserId(null)
            }
        }
    }

    private suspend fun reportDeviceIfNeeded() {
        val userId = authenticatedUserId ?: return
        if (!collectionEnabled || reportedUserId == userId) return

        setAnalyticsUserId(userId)
        runCatching { registerDevice() }
            .onSuccess { reportedUserId = userId }
            .onFailure(onRegistrationError)
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

    private val reportingController = SupabaseAuthReportingController(
        setAnalyticsUserId = { userId ->
            val analytics = Analytics.getInstance()
            if (userId == null) analytics.clearUserId() else analytics.setUserId(userId)
        },
        registerDevice = {
            Analytics.getInstance().awaitDeviceId()
            Supabase.db?.get("devices")?.upsert(value = getPlatform().getDevice())
        },
        onRegistrationError = { error ->
            Log.w("SupabaseAuth", "Failed to register device: ${error.message}")
        },
    )

    suspend fun enable() {
        val client = Supabase.enable() ?: return
        lifecycleMutex.withLock {
            if (authJob?.isActive == true) return@withLock

            enabled = true
            _authState.value = AuthState.Initializing
            authJob = authScope.launch {
                client.auth.sessionStatus.collect { status ->
                    if (!enabled) return@collect

                    when (status) {
                        is SessionStatus.Authenticated -> handleAuthenticated(status)
                        is SessionStatus.NotAuthenticated -> signInAnonymously(client)
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

        Supabase.clientOrNull?.let { client ->
            runCatching { client.auth.signOut() }
                .onFailure { Log.w("SupabaseAuth", "Failed to sign out: ${it.message}") }
        }

        // Earlier versions wrote access and refresh tokens to this redundant plaintext cache.
        runCatching { legacySessionStore.delete() }
            .onFailure { Log.w("SupabaseAuth", "Failed to delete legacy session: ${it.message}") }

        _authState.value = AuthState.Disabled
    }

    suspend fun setCollectionEnabled(enabled: Boolean) {
        reportingController.setCollectionEnabled(enabled)
    }

    suspend fun ensureAuthenticated(): Boolean {
        enable()
        return awaitSupabaseAuthentication(authState)
    }

    private suspend fun handleAuthenticated(status: SessionStatus.Authenticated) {
        val user = status.session.user ?: return
        if (!enabled) return

        _authState.value = AuthState.Authenticated(user.id)
        Log.d(
            "SupabaseAuth",
            "Authenticated: userId=${user.id}, anonymous=${user.identities.isNullOrEmpty()}"
        )

        reportingController.onAuthenticated(user.id)
    }

    private suspend fun signInAnonymously(client: SupabaseClient) {
        if (!enabled) return
        try {
            Log.d("SupabaseAuth", "Signing in anonymously...")
            client.auth.signInAnonymously()
        } catch (error: Exception) {
            if (!enabled) return
            Log.e("SupabaseAuth", "Anonymous sign-in failed: ${error.message}")
            _authState.value = AuthState.Error(error.message ?: "Unknown error")
        }
    }

    fun getUserId(): String? = (authState.value as? AuthState.Authenticated)?.userId
}

sealed class AuthState {
    data object Disabled : AuthState()
    data object Initializing : AuthState()
    data class Authenticated(val userId: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
