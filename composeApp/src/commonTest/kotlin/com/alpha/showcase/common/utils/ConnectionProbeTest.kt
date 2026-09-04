package com.alpha.showcase.common.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectionProbeTest {

    @Test
    fun aHungRequestBecomesATypedFailure() = runTest {
        val result = runConnectionProbe<String>(timeoutMillis = 10_000) {
            awaitCancellation()
        }

        assertTrue(result.isFailure)
        assertIs<ConnectionProbeTimeoutException>(result.exceptionOrNull())
        assertEquals(
            "Connection probe timed out after 10 seconds",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun aRepositoryCannotHideTheProbeTimeoutByCatchingCancellation() = runTest {
        val result = runConnectionProbe<String>(timeoutMillis = 10_000) {
            try {
                awaitCancellation()
            } catch (error: CancellationException) {
                Result.failure(error)
            }
        }

        assertIs<ConnectionProbeTimeoutException>(result.exceptionOrNull())
    }

    @Test
    fun callerCancellationStillPropagates() = runTest {
        val cancellation = CancellationException("screen left")

        val thrown = assertFailsWith<CancellationException> {
            runConnectionProbe<String>(timeoutMillis = 10_000) {
                throw cancellation
            }
        }

        assertEquals(cancellation.message, thrown.message)
    }

    @Test
    fun requestFailureIsPreserved() = runTest {
        val failure = IllegalStateException("TypeError: Failed to fetch")

        val result = runConnectionProbe(timeoutMillis = 10_000) {
            Result.failure<String>(failure)
        }

        assertEquals(failure, result.exceptionOrNull())
    }
}
