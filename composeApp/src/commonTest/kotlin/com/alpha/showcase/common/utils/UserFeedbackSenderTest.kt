package com.alpha.showcase.common.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedbackSenderTest {

    @Test
    fun disabledConsentReturnsFailureWithoutPreparingOrSendingFeedback() = runTest {
        var deviceIdRequested = false
        var insertCalled = false
        val sender = UserFeedbackSender(
            isAnonymousUsageEnabled = { false },
            awaitDeviceId = {
                deviceIdRequested = true
                "device-id"
            },
            insertFeedback = { insertCalled = true },
        )

        val result = sender.send("feedback", "person@example.com")

        assertTrue(result.isFailure)
        assertFalse(deviceIdRequested)
        assertFalse(insertCalled)
    }

    @Test
    fun successfulSendReturnsSuccessWithCompletePayload() = runTest {
        var insertedFeedback: UserFeedback? = null
        val sender = UserFeedbackSender(
            isAnonymousUsageEnabled = { true },
            awaitDeviceId = { "stable-device-id" },
            insertFeedback = { insertedFeedback = it },
        )

        val result = sender.send("The slideshow looks great", "person@example.com")

        assertTrue(result.isSuccess)
        assertEquals(
            UserFeedback(
                deviceId = "stable-device-id",
                feedbackType = "user_feedback",
                content = "The slideshow looks great",
                contactEmail = "person@example.com",
            ),
            insertedFeedback,
        )
    }

    @Test
    fun networkFailureIsReturnedToCaller() = runTest {
        val networkFailure = IllegalStateException("network unavailable")
        val sender = UserFeedbackSender(
            isAnonymousUsageEnabled = { true },
            awaitDeviceId = { "stable-device-id" },
            insertFeedback = { throw networkFailure },
        )

        val result = sender.send("feedback", "")

        assertEquals(networkFailure, result.exceptionOrNull())
    }

    @Test
    fun consentRevokedWhilePreparingFeedbackPreventsInsert() = runTest {
        var consentEnabled = true
        var insertCalled = false
        val sender = UserFeedbackSender(
            isAnonymousUsageEnabled = { consentEnabled },
            awaitDeviceId = {
                consentEnabled = false
                "stable-device-id"
            },
            insertFeedback = { insertCalled = true },
        )

        val result = sender.send("feedback", "")

        assertTrue(result.isFailure)
        assertFalse(insertCalled)
    }

    @Test
    fun consentRevokedWhileAuthenticatingPreventsUpload() = runTest {
        var consentEnabled = true
        var uploadCalled = false
        val sender = UserFeedbackSender(
            isAnonymousUsageEnabled = { consentEnabled },
            awaitDeviceId = { "stable-device-id" },
            prepareFeedbackInsert = {
                consentEnabled = false
            },
            insertFeedback = { uploadCalled = true },
        )

        val result = sender.send("feedback", "")

        assertTrue(result.isFailure)
        assertFalse(uploadCalled)
    }

    @Test
    fun revokingConsentCancelsAnUploadAlreadyInProgress() = runTest {
        val feedbackScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var uploadStarted = false
        var uploadCompleted = false
        var uploadCancelled = false
        var observedCancellation: Throwable? = null
        val sender = UserFeedbackSender(
            isAnonymousUsageEnabled = { true },
            awaitDeviceId = { "stable-device-id" },
            insertFeedback = {
                uploadStarted = true
                try {
                    awaitCancellation()
                } finally {
                    uploadCancelled = true
                }
                uploadCompleted = true
            },
        )
        val taskManager = UserFeedbackTaskManager(feedbackScope, sender)
        val caller = launch {
            try {
                taskManager.send("feedback", "")
            } catch (error: CancellationException) {
                observedCancellation = error
            }
        }
        runCurrent()
        assertTrue(uploadStarted)

        taskManager.cancelInFlight()
        runCurrent()

        caller.join()
        assertTrue(uploadCancelled)
        assertFalse(uploadCompleted)
        assertIs<CancellationException>(observedCancellation)
    }

    @Test
    fun cancellingCallerAlsoCancelsManagedUpload() = runTest {
        val feedbackScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var uploadStarted = false
        var uploadCancelled = false
        val sender = UserFeedbackSender(
            isAnonymousUsageEnabled = { true },
            awaitDeviceId = { "stable-device-id" },
            insertFeedback = {
                uploadStarted = true
                try {
                    awaitCancellation()
                } finally {
                    uploadCancelled = true
                }
            },
        )
        val taskManager = UserFeedbackTaskManager(feedbackScope, sender)
        val caller = launch {
            taskManager.send("feedback", "")
        }
        runCurrent()
        assertTrue(uploadStarted)

        caller.cancel()
        caller.join()

        assertTrue(uploadCancelled)
        assertTrue(caller.isCancelled)
    }

    @Test
    fun totalDeadlineReturnsFailureAfterCancellingAndJoiningUpload() = runTest {
        val feedbackScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var uploadStarted = false
        var uploadCancelled = false
        val sender = UserFeedbackSender(
            isAnonymousUsageEnabled = { true },
            awaitDeviceId = { "stable-device-id" },
            insertFeedback = {
                uploadStarted = true
                try {
                    awaitCancellation()
                } finally {
                    uploadCancelled = true
                }
            },
        )
        val taskManager = UserFeedbackTaskManager(
            scope = feedbackScope,
            sender = sender,
            timeoutMillis = 15_000,
        )

        val result = taskManager.send("feedback", "")

        assertTrue(uploadStarted)
        assertTrue(result.isFailure)
        assertIs<UserFeedbackTimeoutException>(result.exceptionOrNull())
        assertTrue(uploadCancelled)
        assertEquals(15_000L, currentTime)
    }

    @Test
    fun cancellationIgnoringCleanupCannotLockTheCallerForever() = runTest {
        val feedbackScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val releaseCleanup = CompletableDeferred<Unit>()
        var cleanupStarted = false
        val sender = UserFeedbackSender(
            isAnonymousUsageEnabled = { true },
            awaitDeviceId = { "stable-device-id" },
            insertFeedback = {
                try {
                    awaitCancellation()
                } finally {
                    cleanupStarted = true
                    withContext(NonCancellable) {
                        releaseCleanup.await()
                    }
                }
            },
        )
        val taskManager = UserFeedbackTaskManager(
            scope = feedbackScope,
            sender = sender,
            timeoutMillis = 15_000,
            cleanupJoinTimeoutMillis = 500,
        )

        val result = taskManager.send("feedback", "")

        assertTrue(result.isFailure)
        assertIs<UserFeedbackTimeoutException>(result.exceptionOrNull())
        assertTrue(cleanupStarted)
        assertEquals(15_500L, currentTime)

        releaseCleanup.complete(Unit)
        runCurrent()
    }

    @Test
    fun cancellationIsPropagatedToCaller() = runTest {
        val cancellation = CancellationException("leave feedback screen")
        val sender = UserFeedbackSender(
            isAnonymousUsageEnabled = { true },
            awaitDeviceId = { throw cancellation },
            insertFeedback = { error("cancelled feedback must not be inserted") },
        )

        val thrown = runCatching { sender.send("feedback", "") }.exceptionOrNull()

        assertSame(cancellation, thrown)
    }
}
