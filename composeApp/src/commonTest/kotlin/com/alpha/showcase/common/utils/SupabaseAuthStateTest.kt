package com.alpha.showcase.common.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseAuthStateTest {

    @Test
    fun localRestorationDeadlineFailsNormallyAndNextRequestReusesRecoveredIdentity() = runTest {
        var restoring = true
        var userId: String? = null
        var signIns = 0
        val initializer = SupabaseAuthInitializer(
            awaitInitialization = { if (restoring) awaitCancellation() },
            currentUserId = { userId },
            signInAnonymously = { signIns++ },
            isNotAuthenticated = { false },
        )
        val attempt = backgroundScope.async { runCatching { initializer.ensureUserId() } }
        runCurrent()
        advanceTimeBy(60_001)
        runCurrent()

        assertTrue(attempt.isCompleted, "A stalled restoration must have a bounded local deadline")
        assertIs<IllegalStateException>(attempt.await().exceptionOrNull())
        assertEquals(0, signIns)

        restoring = false
        userId = "original-restored-user"
        assertEquals("original-restored-user", initializer.ensureUserId())
        assertEquals(0, signIns)
    }

    @Test
    fun callerDeadlineStillCancelsAuthentication() = runTest {
        var signIns = 0
        val initializer = SupabaseAuthInitializer(
            { awaitCancellation() }, { null }, { signIns++ }, { true },
        )
        assertFailsWith<CancellationException> {
            withTimeout(100) { initializer.ensureUserId() }
        }
        assertEquals(0, signIns)
    }

    @Test
    fun restoresPersistedUserBeforeDecidingToSignIn() = runTest {
        var userId: String? = null
        val initializer = SupabaseAuthInitializer(
            awaitInitialization = { delay(10); userId = "restored-user" },
            currentUserId = { userId },
            signInAnonymously = { error("Must not replace a restored user") },
            isNotAuthenticated = { userId == null },
        )
        assertEquals("restored-user", initializer.ensureUserId())
    }

    @Test
    fun concurrentRequestsCreateOnlyOneAnonymousUser() = runTest {
        var userId: String? = null
        var signIns = 0
        val initializer = SupabaseAuthInitializer(
            awaitInitialization = { delay(1) },
            currentUserId = { userId },
            signInAnonymously = { signIns++; delay(10); userId = "anonymous-user" },
            isNotAuthenticated = { userId == null },
        )
        val users = List(8) { async { initializer.ensureUserId() } }.awaitAll()
        assertEquals(List(8) { "anonymous-user" }, users)
        assertEquals(1, signIns)
    }

    @Test
    fun failedSignInCanRetryOnTheNextRequest() = runTest {
        var userId: String? = null
        var attempts = 0
        val initializer = SupabaseAuthInitializer(
            awaitInitialization = {},
            currentUserId = { userId },
            signInAnonymously = {
                if (++attempts == 1) error("offline")
                userId = "recovered-user"
            },
            isNotAuthenticated = { userId == null },
        )
        assertFailsWith<IllegalStateException> { initializer.ensureUserId() }
        assertEquals("recovered-user", initializer.ensureUserId())
        assertEquals(2, attempts)
    }

    @Test
    fun cancellationDuringRestorationDoesNotStartSignIn() = runTest {
        val initializer = SupabaseAuthInitializer(
            awaitInitialization = { throw CancellationException() },
            currentUserId = { null },
            signInAnonymously = { error("Cancelled request must not sign in") },
            isNotAuthenticated = { true },
        )
        assertFailsWith<CancellationException> { initializer.ensureUserId() }
    }

    @Test
    fun signInWithoutUserCannotAuthorizeARequest() = runTest {
        val initializer = SupabaseAuthInitializer({}, { null }, {}, { true })
        assertFailsWith<IllegalStateException> { initializer.ensureUserId() }
    }

    @Test
    fun refreshFailureMustNotReplaceARecoverableIdentity() = runTest {
        var userId: String? = null
        var signIns = 0
        val initializer = SupabaseAuthInitializer(
            awaitInitialization = {},
            currentUserId = { userId },
            signInAnonymously = { signIns++; userId = "replacement-user" },
            // SDK RefreshFailure has no current user but is not NotAuthenticated.
            isNotAuthenticated = { false },
        )
        assertFailsWith<IllegalStateException> { initializer.ensureUserId() }
        assertEquals(0, signIns)
        userId = "recovered-original-user"
        assertEquals("recovered-original-user", initializer.ensureUserId())
    }
}
