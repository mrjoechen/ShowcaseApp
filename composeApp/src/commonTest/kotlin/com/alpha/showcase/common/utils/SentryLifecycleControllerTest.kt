package com.alpha.showcase.common.utils

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun optingBackInReinitializesSdk() = runTest {
        val sdkCalls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(sdkCalls)

        controller.setEnabled(true)
        controller.setEnabled(false)
        controller.setEnabled(true)

        assertEquals(listOf("initialize", "close", "initialize"), sdkCalls)
    }

    @Test
    fun failedInitializationCanBeRetried() = runTest {
        var attempts = 0
        val controller = SentryLifecycleController(
            initializeSdk = {
                attempts++
                if (attempts == 1) error("Initialization failed")
            },
            closeSdk = {},
            runOnRequiredThread = { action -> action() },
        )

        assertFailsWith<IllegalStateException> { controller.setEnabled(true) }
        controller.setEnabled(true)
        controller.setEnabled(true)

        assertEquals(2, attempts)
    }

    private fun controllerRecordingCallsIn(
        sdkCalls: MutableList<String>,
    ) = SentryLifecycleController(
        initializeSdk = { sdkCalls += "initialize" },
        closeSdk = { sdkCalls += "close" },
        runOnRequiredThread = { action -> action() },
    )
}
