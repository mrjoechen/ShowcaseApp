package com.alpha.showcase.common.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AnonymousUsageLifecycleControllerTest {

    @Test
    fun disablingUsageKeepsSupabaseInitializedAndDisablesCollectors() = runTest {
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
    fun enablingUsageKeepsSupabaseInitializedAndEnablesCollectors() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls)

        controller.applyConsent(true)

        assertEquals(
            listOf(
                "supabase:initialize",
                "auth:ensure",
                "analytics:prepare",
                "sentry:true",
                "collection:true",
                "analytics:true",
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

    private fun controllerRecordingCallsIn(
        calls: MutableList<String>,
    ) = AnonymousUsageLifecycleController(
        initializeSupabase = { calls += "supabase:initialize" },
        ensureSupabaseAuth = { calls += "auth:ensure" },
        setAnalyticsEnabled = { enabled -> calls += "analytics:$enabled" },
        prepareAnalytics = { calls += "analytics:prepare" },
        setSentryEnabled = { enabled -> calls += "sentry:$enabled" },
        setAnonymousCollectionEnabled = { enabled -> calls += "collection:$enabled" },
    )
}
