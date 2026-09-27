package com.alpha.showcase.common.utils

sealed interface SentryTestResult {
    data object ConsentRequired : SentryTestResult
    data object Unsupported : SentryTestResult
    data object NotInitialized : SentryTestResult
    data object Rejected : SentryTestResult
    data object Failed : SentryTestResult
    /** A local event ID is not a server delivery acknowledgment. */
    data class Queued(val eventId: String) : SentryTestResult
}

internal fun captureSentryTestEvent(
    hasConsent: Boolean,
    isSupported: Boolean,
    isSdkEnabled: () -> Boolean,
    capture: (Throwable) -> String,
): SentryTestResult {
    if (!isSupported) return SentryTestResult.Unsupported
    if (!hasConsent) return SentryTestResult.ConsentRequired
    if (!isSdkEnabled()) return SentryTestResult.NotInitialized

    // Throw and catch so the event contains an actual test stack on all native targets.
    val eventId = try {
        throw IllegalStateException("Showcase Sentry manual test (handled exception)")
    } catch (error: IllegalStateException) {
        capture(error)
    }
    return if (eventId.isBlank() || eventId == "00000000000000000000000000000000") {
        SentryTestResult.Rejected
    } else {
        SentryTestResult.Queued(eventId)
    }
}
