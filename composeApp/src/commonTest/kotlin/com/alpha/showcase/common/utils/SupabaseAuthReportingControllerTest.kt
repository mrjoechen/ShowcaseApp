package com.alpha.showcase.common.utils

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SupabaseAuthReportingControllerTest {

    @Test
    fun authenticationDoesNotReportWhileCollectionIsDisabled() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls)

        controller.onAuthenticated("user-1")

        assertEquals(emptyList(), calls)
    }

    @Test
    fun enablingCollectionAfterAuthenticationReportsDeviceOnce() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls)
        controller.onAuthenticated("user-1")

        controller.setCollectionEnabled(true)
        controller.setCollectionEnabled(true)

        assertEquals(listOf("analytics:user-1", "device:register"), calls)
    }

    @Test
    fun disablingCollectionClearsAnalyticsIdentity() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls)
        controller.onAuthenticated("user-1")
        controller.setCollectionEnabled(true)

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
        )
        controller.onAuthenticated("user-1")

        controller.setCollectionEnabled(true)
        controller.setCollectionEnabled(true)

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

    private fun controllerRecordingCallsIn(
        calls: MutableList<String>,
    ) = SupabaseAuthReportingController(
        setAnalyticsUserId = { userId -> calls += "analytics:$userId" },
        registerDevice = { calls += "device:register" },
    )
}
