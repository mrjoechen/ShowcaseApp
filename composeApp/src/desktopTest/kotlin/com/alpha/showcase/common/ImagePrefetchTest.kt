package com.alpha.showcase.common

import com.alpha.showcase.common.ui.play.ImagePrefetchWindow
import com.alpha.showcase.common.ui.play.prefetchImages
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ImagePrefetchTest {
    @Test fun movingOntoPrefetchedImageKeepsTransferAndStartsNextAfterCompletion() = runTest {
        val window = MutableStateFlow(ImagePrefetchWindow("A", true, "B"))
        val finishB = CompletableDeferred<Unit>()
        val starts = mutableListOf<Any>()
        var cancelledB = false
        val worker = launch {
            prefetchImages(window) {
                starts += it
                if (it == "B") {
                    try { finishB.await() }
                    catch (cancelled: CancellationException) { cancelledB = true; throw cancelled }
                }
            }
        }
        runCurrent()
        assertEquals(listOf<Any>("B"), starts)
        window.value = ImagePrefetchWindow("B", false, "C")
        runCurrent()
        assertFalse(cancelledB)
        // Readiness and prefetch completion can arrive in either order.
        window.value = ImagePrefetchWindow("B", true, "C")
        runCurrent()
        assertEquals(listOf<Any>("B"), starts)
        finishB.complete(Unit)
        runCurrent()
        assertEquals(listOf<Any>("B", "C"), starts)
        worker.cancelAndJoin()
        assertFalse(cancelledB)
    }

    @Test fun replacingPlaceholderAndRenderStateUsesLatestReadiness() = runTest {
        val window = MutableStateFlow(ImagePrefetchWindow("placeholder", false, "B"))
        val starts = mutableListOf<Any>()
        val worker = launch { prefetchImages(window) { starts += it } }
        runCurrent()
        window.value = ImagePrefetchWindow("loaded A", false, "B")
        runCurrent()
        assertTrue(starts.isEmpty())
        window.value = ImagePrefetchWindow("loaded A", true, "B")
        runCurrent()
        assertEquals(listOf<Any>("B"), starts)
        worker.cancelAndJoin()
    }

    @Test fun leavingDemandWindowAndLeavingPlaybackCancelSpeculativeWork() = runTest {
        val window = MutableStateFlow(ImagePrefetchWindow("A", true, "B"))
        val starts = mutableListOf<Any>()
        val cancelled = mutableListOf<Any>()
        val worker = launch {
            prefetchImages(window) {
                starts += it
                try { awaitCancellation() } finally { cancelled += it }
            }
        }
        runCurrent()
        window.value = ImagePrefetchWindow("X", false, "Y")
        runCurrent()
        assertEquals(listOf<Any>("B"), cancelled)
        assertEquals(listOf<Any>("B"), starts)
        window.value = ImagePrefetchWindow("X", true, "Y")
        runCurrent()
        assertEquals(listOf<Any>("B", "Y"), starts)
        worker.cancelAndJoin()
        assertEquals(listOf<Any>("B", "Y"), cancelled)
    }
}
