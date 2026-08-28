package com.alpha.showcase.common.utils

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryLifecycleControllerTest {

    @Test
    fun disablingBeforeInitializationDoesNotTouchSdk() = runTest {
        val sdkCalls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(sdkCalls)

        controller.setEnabled(false)

        assertEquals(emptyList(), sdkCalls)
    }

    @Test
    fun lifecycleTransitionsAreIdempotent() = runTest {
        val sdkCalls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(sdkCalls)

        controller.setEnabled(true)
        controller.setEnabled(true)
        controller.setEnabled(false)
        controller.setEnabled(false)

        assertEquals(listOf("initialize", "close"), sdkCalls)
    }

    private fun controllerRecordingCallsIn(
        sdkCalls: MutableList<String>,
    ) = SentryLifecycleController(
        initializeSdk = { sdkCalls += "initialize" },
        closeSdk = { sdkCalls += "close" },
        runOnRequiredThread = { action -> action() },
    )
}
