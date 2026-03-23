package com.alpha.showcase.common.utils

import com.alpha.showcase.common.storage.objectStoreOf
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CachedAuthSession(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("is_anonymous")
    val isAnonymous: Boolean = true
)

object SupabaseAuth {

    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionStore = objectStoreOf<CachedAuthSession>("supabase_auth_session")

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var cachedSession: CachedAuthSession? = null

    fun initialize() {
        val client = Supabase.supabase ?: return

        authScope.launch {
            // Load cached session
            cachedSession = sessionStore.get()

            // Listen to session status changes
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val session = status.session
                        val user = session.user
                        if (user != null) {
                            val isAnon = user.identities.isNullOrEmpty()
                            val newCached = CachedAuthSession(
                                accessToken = session.accessToken,
                                refreshToken = session.refreshToken,
                                userId = user.id,
                                isAnonymous = isAnon
                            )
                            cachedSession = newCached
                            sessionStore.set(newCached)
                            _authState.value = AuthState.Authenticated(user.id)
                            // Link auth userId with Analytics
                            try {
                                Analytics.getInstance().setUserId(user.id)
                            } catch (_: Exception) {}
                            Log.d("SupabaseAuth", "Authenticated: userId=${user.id}, anonymous=$isAnon")
                        }
                    }

                    is SessionStatus.NotAuthenticated -> {
                        // Try to sign in anonymously
                        signInAnonymously()
                    }

                    is SessionStatus.Initializing -> {
                        _authState.value = AuthState.Initializing
                    }

                    else -> {}
                }
            }
        }
    }

    private suspend fun signInAnonymously() {
        val client = Supabase.supabase ?: return
        try {
            Log.d("SupabaseAuth", "Signing in anonymously...")
            client.auth.signInAnonymously()
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Anonymous sign-in failed: ${e.message}")
            _authState.value = AuthState.Error(e.message ?: "Unknown error")
        }
    }

    fun getUserId(): String? {
        return when (val state = _authState.value) {
            is AuthState.Authenticated -> state.userId
            else -> cachedSession?.userId
        }
    }

    fun getCachedSession(): CachedAuthSession? = cachedSession
}

sealed class AuthState {
    data object Initializing : AuthState()
    data class Authenticated(val userId: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
