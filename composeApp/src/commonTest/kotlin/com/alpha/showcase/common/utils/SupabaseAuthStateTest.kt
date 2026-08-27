package com.alpha.showcase.common.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseAuthStateTest {

    @Test
    fun waitsForAuthenticationBeforeAllowingProtectedConfigReads() = runTest {
        val state = MutableStateFlow<AuthState>(AuthState.Initializing)
        backgroundScope.launch {
            yield()
            state.value = AuthState.Authenticated("user-1")
        }

        assertTrue(awaitSupabaseAuthentication(state, timeoutMillis = 1_000))
    }

    @Test
    fun stopsWaitingWhenAuthenticationFails() = runTest {
        val state = MutableStateFlow<AuthState>(AuthState.Error("offline"))

        assertFalse(awaitSupabaseAuthentication(state, timeoutMillis = 1_000))
    }
}
