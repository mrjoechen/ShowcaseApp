package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.ui.vm.UiState
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class PlaybackUiStateBoundaryTest {

    @Test
    fun ordinaryFailureBecomesUiStateError() = runTest {
        val result = playbackUiStateBoundary<String> {
            throw IllegalStateException("S3 signing failed")
        }

        assertEquals("S3 signing failed", assertIs<UiState.Error>(result).msg)
    }

    @Test
    fun cancellationPropagatesWithItsIdentityPreserved() = runTest {
        val cancellation = CancellationException("screen left")

        val thrown = assertFailsWith<CancellationException> {
            playbackUiStateBoundary<String> { throw cancellation }
        }

        assertSame(cancellation, thrown)
    }
}
