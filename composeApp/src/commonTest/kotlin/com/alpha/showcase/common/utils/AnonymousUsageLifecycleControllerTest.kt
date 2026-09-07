package com.alpha.showcase.common.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AnonymousUsageLifecycleControllerTest {

    @Test
    fun enablingUsageCompletesWhileAuthenticationIsStillPending() = runTest {
        val authenticationStarted = CompletableDeferred<Unit>()
        var analyticsEnabled = false
        var sentryEnabled = false
        var collectionEnabled = false
        val controller = AnonymousUsageLifecycleController(
            authenticationScope = backgroundScope,
            initializeSupabase = {},
            ensureSupabaseAuth = { authenticationStarted.complete(Unit); awaitCancellation() },
            setAnalyticsEnabled = { analyticsEnabled = it },
            setSentryEnabled = { sentryEnabled = it },
            setAnonymousCollectionEnabled = { collectionEnabled = it },
        )
        val activation = backgroundScope.launch { controller.applyConsent(true) }
        runCurrent()

        assertTrue(authenticationStarted.isCompleted)
        assertTrue(activation.isCompleted, "Updating consent must not wait for a network response")
        assertTrue(analyticsEnabled)
        assertTrue(sentryEnabled)
        assertTrue(collectionEnabled)
    }

    @Test
    fun optOutDoesNotWaitForPendingAuthenticationOrAllowLateDeviceReporting() = runTest {
        var analyticsEnabled = false
        var sentryEnabled = false
        var registrations = 0
        var authenticationCancelled = false
        val reporting = SupabaseAuthReportingController(
            setAnalyticsUserId = {}, registerDevice = { registrations++ }, reportingScope = backgroundScope,
        )
        val controller = AnonymousUsageLifecycleController(
            authenticationScope = backgroundScope,
            initializeSupabase = {},
            ensureSupabaseAuth = {
                try {
                    awaitCancellation()
                } finally {
                    authenticationCancelled = true
                }
            },
            setAnalyticsEnabled = { analyticsEnabled = it },
            setSentryEnabled = { sentryEnabled = it },
            setAnonymousCollectionEnabled = reporting::setCollectionEnabled,
        )
        backgroundScope.launch { controller.applyConsent(true) }
        runCurrent()
        val deactivation = backgroundScope.launch { controller.applyConsent(false) }
        runCurrent()

        assertTrue(deactivation.isCompleted, "Opt-out must not queue behind authentication")
        assertFalse(analyticsEnabled)
        assertFalse(sentryEnabled)
        assertTrue(authenticationCancelled)
        reporting.onAuthenticated("late-user")
        runCurrent()
        assertEquals(0, registrations)
    }

    @Test
    fun authenticationRecoveryReportsDeviceWithoutTogglingConsentAgain() = runTest {
        var analyticsEnabled = false
        var registrations = 0
        val initializer = SupabaseAuthInitializer(
            { awaitCancellation() }, { null }, { error("Must not replace a restoring user") }, { false },
        )
        val reporting = SupabaseAuthReportingController(
            setAnalyticsUserId = {}, registerDevice = { registrations++ }, reportingScope = backgroundScope,
        )
        val controller = AnonymousUsageLifecycleController(
            authenticationScope = backgroundScope,
            initializeSupabase = {},
            ensureSupabaseAuth = { initializer.ensureUserId() },
            setAnalyticsEnabled = { analyticsEnabled = it },
            setSentryEnabled = {},
            setAnonymousCollectionEnabled = reporting::setCollectionEnabled,
        )
        val activation = backgroundScope.launch { controller.applyConsent(true) }
        runCurrent()
        assertTrue(activation.isCompleted)
        assertTrue(analyticsEnabled)
        assertEquals(0, registrations)

        // The local auth attempt times out, but optional collection remains enabled.
        advanceTimeBy(60_001)
        runCurrent()
        assertTrue(analyticsEnabled)
        assertEquals(0, registrations)

        // The SDK's independent session observer delivers the recovered identity.
        reporting.onAuthenticated("recovered-user")
        runCurrent()
        assertEquals(1, registrations)
    }

    @Test
    fun repeatedOptInSharesPendingAuthenticationAndCanRestartAfterOptOut() = runTest {
        var authenticationAttempts = 0
        val controller = AnonymousUsageLifecycleController(
            authenticationScope = backgroundScope,
            initializeSupabase = {},
            ensureSupabaseAuth = { authenticationAttempts++; awaitCancellation() },
            setAnalyticsEnabled = {},
            setSentryEnabled = {},
            setAnonymousCollectionEnabled = {},
        )
        controller.applyConsent(true)
        runCurrent()
        controller.applyConsent(true)
        runCurrent()
        assertEquals(1, authenticationAttempts)

        controller.applyConsent(false)
        controller.applyConsent(true)
        runCurrent()
        assertEquals(2, authenticationAttempts)
    }

    @Test
    fun disablingUsageDoesNotInitializeSupabaseAndDisablesCollectors() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls)

        controller.applyConsent(false)

        assertEquals(
            listOf(
                "analytics:false",
                "collection:false",
                "sentry:false",
            ),
            calls,
        )
    }

    @Test
    fun enablingUsageInitializesSupabaseAndEnablesCollectors() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls)

        controller.applyConsent(true)
        runCurrent()

        assertEquals(
            listOf(
                "supabase:initialize",
                "sentry:true",
                "collection:true",
                "analytics:true",
                "auth:ensure",
            ),
            calls,
        )
    }

    @Test
    fun disablingUsageTurnsOffAnalyticsBeforeCollectorCleanupCanSuspend() = runTest {
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanupToFinish = CompletableDeferred<Unit>()
        var analyticsEnabled = true
        val controller = AnonymousUsageLifecycleController(
            authenticationScope = backgroundScope,
            initializeSupabase = { error("opt-out must not initialize Supabase") },
            ensureSupabaseAuth = { error("opt-out must not authenticate") },
            setAnalyticsEnabled = { enabled -> analyticsEnabled = enabled },
            setSentryEnabled = {},
            setAnonymousCollectionEnabled = {
                cleanupStarted.complete(Unit)
                allowCleanupToFinish.await()
            },
        )
        val disabling = launch {
            controller.applyConsent(false)
        }

        cleanupStarted.await()

        assertFalse(analyticsEnabled)

        allowCleanupToFinish.complete(Unit)
        disabling.join()
    }

    @Test
    fun disablingUsageTurnsOffAnalyticsBeforeWaitingForAnInProgressTransition() = runTest {
        val initializationStarted = CompletableDeferred<Unit>()
        val allowInitializationToFinish = CompletableDeferred<Unit>()
        var blockFirstInitialization = true
        var analyticsEnabled = true
        val controller = AnonymousUsageLifecycleController(
            authenticationScope = backgroundScope,
            initializeSupabase = {
                if (blockFirstInitialization) {
                    blockFirstInitialization = false
                    initializationStarted.complete(Unit)
                    allowInitializationToFinish.await()
                }
            },
            ensureSupabaseAuth = {},
            setAnalyticsEnabled = { enabled -> analyticsEnabled = enabled },
            setSentryEnabled = {},
            setAnonymousCollectionEnabled = {},
        )
        val enabling = launch {
            controller.applyConsent(true)
        }
        initializationStarted.await()

        val disabling = launch(start = CoroutineStart.UNDISPATCHED) {
            controller.applyConsent(false)
        }

        assertFalse(analyticsEnabled)
        assertFalse(disabling.isCompleted)

        allowInitializationToFinish.complete(Unit)
        runCurrent()
        enabling.join()
        disabling.join()
        assertFalse(analyticsEnabled)
        assertTrue(disabling.isCompleted)
    }

    private fun TestScope.controllerRecordingCallsIn(
        calls: MutableList<String>,
    ) = AnonymousUsageLifecycleController(
        authenticationScope = backgroundScope,
        initializeSupabase = { calls += "supabase:initialize" },
        ensureSupabaseAuth = { calls += "auth:ensure" },
        setAnalyticsEnabled = { enabled -> calls += "analytics:$enabled" },
        setSentryEnabled = { enabled -> calls += "sentry:$enabled" },
        setAnonymousCollectionEnabled = { enabled -> calls += "collection:$enabled" },
    )
}
