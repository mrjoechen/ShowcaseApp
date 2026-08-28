package com.alpha.showcase.common.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FeedbackSubmissionTest {

    @Test
    fun successfulSubmissionNotifiesOnlySuccess() = runTest {
        val notifications = mutableListOf<String>()

        submitFeedback(
            sendFeedback = { Result.success(Unit) },
            onSuccess = { notifications += "success" },
            onFailure = { notifications += "failure" },
        )

        assertEquals(listOf("success"), notifications)
    }

    @Test
    fun failedSubmissionNotifiesOnlyFailure() = runTest {
        val notifications = mutableListOf<String>()

        submitFeedback(
            sendFeedback = { Result.failure(IllegalStateException("network unavailable")) },
            onSuccess = { notifications += "success" },
            onFailure = { notifications += "failure" },
        )

        assertEquals(listOf("failure"), notifications)
    }

    @Test
    fun dialogClosesOnlyAfterSuccessfulSubmission() = runTest {
        var dialogVisible = true
        var draft = "Please keep this text"

        submitFeedback(
            sendFeedback = { Result.failure(IllegalStateException("network unavailable")) },
            onSuccess = {
                dialogVisible = false
                draft = ""
            },
            onFailure = {},
        )

        assertTrue(dialogVisible)
        assertEquals("Please keep this text", draft)

        submitFeedback(
            sendFeedback = { Result.success(Unit) },
            onSuccess = {
                dialogVisible = false
                draft = ""
            },
            onFailure = {},
        )

        assertFalse(dialogVisible)
        assertEquals("", draft)
    }

    @Test
    fun submissionGateRejectsDuplicateUntilCurrentSubmissionFinishes() {
        val gate = FeedbackSubmissionGate()

        assertTrue(gate.tryStart())
        assertTrue(gate.isSubmitting)
        assertFalse(gate.tryStart())

        gate.finish()

        assertFalse(gate.isSubmitting)
        assertTrue(gate.tryStart())
    }

    @Test
    fun startedSubmissionAlwaysReleasesGateAfterFailure() = runTest {
        val gate = FeedbackSubmissionGate()
        assertTrue(gate.tryStart())

        runStartedFeedbackSubmission(
            gate = gate,
            sendFeedback = { Result.failure(IllegalStateException("network unavailable")) },
            onSuccess = {},
            onFailure = {},
        )

        assertFalse(gate.isSubmitting)
    }

    @Test
    fun thrownSubmissionExceptionIsReportedAsFailure() = runTest {
        val notifications = mutableListOf<String>()

        submitFeedback(
            sendFeedback = { error("unexpected backend failure") },
            onSuccess = { notifications += "success" },
            onFailure = { notifications += "failure" },
        )

        assertEquals(listOf("failure"), notifications)
    }

    @Test
    fun cancellationIsPropagatedWithoutShowingAResult() = runTest {
        val cancellation = CancellationException("leave feedback screen")
        val notifications = mutableListOf<String>()

        val thrown = runCatching {
            submitFeedback(
                sendFeedback = { throw cancellation },
                onSuccess = { notifications += "success" },
                onFailure = { notifications += "failure" },
            )
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(emptyList<String>(), notifications)
    }

    @Test
    fun cancellationReleasesSubmissionGateAndStillPropagates() = runTest {
        val cancellation = CancellationException("consent revoked")
        val gate = FeedbackSubmissionGate()
        assertTrue(gate.tryStart())

        val thrown = runCatching {
            runStartedFeedbackSubmission(
                gate = gate,
                sendFeedback = { throw cancellation },
                onSuccess = {},
                onFailure = {},
            )
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertFalse(gate.isSubmitting)
    }
}
