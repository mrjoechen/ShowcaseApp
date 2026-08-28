package com.alpha.showcase.common.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseAuthReportingControllerTest {

    @Test
    fun authenticationDoesNotReportWhileCollectionIsDisabled() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls, backgroundScope)

        controller.onAuthenticated("user-1")

        assertEquals(emptyList(), calls)
    }

    @Test
    fun enablingCollectionAfterAuthenticationReportsDeviceOnce() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls, backgroundScope)
        controller.onAuthenticated("user-1")

        controller.setCollectionEnabled(true)
        controller.setCollectionEnabled(true)
        runCurrent()

        assertEquals(listOf("analytics:user-1", "device:register"), calls)
    }

    @Test
    fun disablingCollectionClearsAnalyticsIdentity() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls, backgroundScope)
        controller.onAuthenticated("user-1")
        controller.setCollectionEnabled(true)
        runCurrent()

        controller.setCollectionEnabled(false)

        assertEquals(
            listOf("analytics:user-1", "device:register", "analytics:null"),
            calls,
        )
    }

    @Test
    fun repeatedEnableRetriesADeviceRegistrationThatPreviouslyFailed() = runTest {
        val calls = mutableListOf<String>()
        var registrationAttempts = 0
        val controller = SupabaseAuthReportingController(
            setAnalyticsUserId = { userId -> calls += "analytics:$userId" },
            registerDevice = {
                registrationAttempts += 1
                calls += "device:$registrationAttempts"
                if (registrationAttempts == 1) error("temporary failure")
            },
            reportingScope = backgroundScope,
        )
        controller.onAuthenticated("user-1")

        controller.setCollectionEnabled(true)
        runCurrent()
        controller.setCollectionEnabled(true)
        runCurrent()

        assertEquals(
            listOf(
                "analytics:user-1",
                "device:1",
                "analytics:user-1",
                "device:2",
            ),
            calls,
        )
    }

    @Test
    fun disablingCollectionCancelsRegistrationWithoutWaitingForIt() = runTest {
        val calls = mutableListOf<String>()
        val registrationStarted = CompletableDeferred<Unit>()
        val registrationCancelled = CompletableDeferred<Unit>()
        val controller = SupabaseAuthReportingController(
            setAnalyticsUserId = { userId -> calls += "analytics:$userId" },
            registerDevice = {
                registrationStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    registrationCancelled.complete(Unit)
                }
            },
            reportingScope = backgroundScope,
        )
        controller.onAuthenticated("user-1")
        controller.setCollectionEnabled(true)
        runCurrent()
        registrationStarted.await()

        controller.setCollectionEnabled(false)
        runCurrent()

        assertEquals(listOf("analytics:user-1", "analytics:null"), calls)
        assertTrue(registrationCancelled.isCompleted)
    }

    @Test
    fun staleRegistrationCompletionAfterOptOutIsNotRemembered() = runTest {
        val calls = mutableListOf<String>()
        val firstRegistrationStarted = CompletableDeferred<Unit>()
        val releaseFirstRegistration = CompletableDeferred<Unit>()
        var registrationAttempts = 0
        val controller = SupabaseAuthReportingController(
            setAnalyticsUserId = { userId -> calls += "analytics:$userId" },
            registerDevice = {
                registrationAttempts += 1
                if (registrationAttempts == 1) {
                    firstRegistrationStarted.complete(Unit)
                    withContext(NonCancellable) {
                        releaseFirstRegistration.await()
                    }
                }
            },
            reportingScope = backgroundScope,
        )
        controller.onAuthenticated("user-1")
        controller.setCollectionEnabled(true)
        runCurrent()
        firstRegistrationStarted.await()

        controller.setCollectionEnabled(false)
        releaseFirstRegistration.complete(Unit)
        runCurrent()
        controller.setCollectionEnabled(true)
        runCurrent()

        assertEquals(2, registrationAttempts)
        assertEquals(
            listOf("analytics:user-1", "analytics:null", "analytics:user-1"),
            calls,
        )
    }

    private fun controllerRecordingCallsIn(
        calls: MutableList<String>,
        reportingScope: CoroutineScope,
    ) = SupabaseAuthReportingController(
        setAnalyticsUserId = { userId -> calls += "analytics:$userId" },
        registerDevice = { calls += "device:register" },
        reportingScope = reportingScope,
    )
}
