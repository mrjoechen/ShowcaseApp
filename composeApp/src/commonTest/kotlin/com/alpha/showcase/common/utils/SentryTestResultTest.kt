package com.alpha.showcase.common.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SentryTestResultTest {
    @Test
    fun optOutNeverCallsSdk() {
        assertEquals(SentryTestResult.ConsentRequired, captureSentryTestEvent(
            hasConsent = false,
            isSupported = true,
            isSdkEnabled = { error("Must not inspect SDK after opt-out") },
            capture = { error("Must not collect after opt-out") },
        ))
    }

    @Test
    fun unsupportedPlatformDoesNotPretendToInitializeOrCapture() {
        assertEquals(SentryTestResult.Unsupported, captureSentryTestEvent(
            hasConsent = true,
            isSupported = false,
            isSdkEnabled = { error("Must not call a stub SDK") },
            capture = { error("Must not call a stub SDK") },
        ))
    }

    @Test
    fun disabledSdkIsReportedWithoutTryingToRepairInitialization() {
        assertEquals(SentryTestResult.NotInitialized, captureSentryTestEvent(
            hasConsent = true,
            isSupported = true,
            isSdkEnabled = { false },
            capture = { error("Must not capture before initialization") },
        ))
    }

    @Test
    fun emptyEventIdIsNotReportedAsQueued() {
        for (eventId in listOf("", "00000000000000000000000000000000")) {
            assertEquals(SentryTestResult.Rejected, captureSentryTestEvent(
                hasConsent = true,
                isSupported = true,
                isSdkEnabled = { true },
                capture = { eventId },
            ))
        }
    }

    @Test
    fun capturesOneRealExceptionAndReturnsItsEventId() {
        var captureCount = 0
        val eventId = "1234567890abcdef1234567890abcdef"
        val result = captureSentryTestEvent(
            hasConsent = true,
            isSupported = true,
            isSdkEnabled = { true },
            capture = { error ->
                captureCount++
                assertIs<IllegalStateException>(error)
                assertEquals("Showcase Sentry manual test (handled exception)", error.message)
                assertTrue(error.stackTraceToString().contains("captureSentryTestEvent"))
                eventId
            },
        )
        assertEquals(1, captureCount)
        assertEquals(SentryTestResult.Queued(eventId), result)
    }
}
