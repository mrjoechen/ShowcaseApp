package com.alpha.showcase.common.ui.play

import kotlin.test.*

class ImagePlaybackTimerTest {
    private fun timer(duration: Long = 500, timeout: Long = 30_000) =
        ImagePlaybackTimer(duration, ImagePlaybackConfig(timeout))

    @Test fun shortIntervalStartsOnlyAfterSuccessAndProvidesFullDisplayTime() {
        val timer = timer()
        val loading = ImagePlaybackFrame("A", false)
        assertFalse(timer.update(loading, 0))
        assertFalse(timer.update(loading, 10_000))
        assertEquals(0f, timer.progress)
        assertFalse(timer.update(loading.copy(ready = true), 12_000))
        assertFalse(timer.update(loading.copy(ready = true), 12_499))
        assertTrue(timer.update(loading.copy(ready = true), 12_500))
        assertEquals(1f, timer.progress)
        assertFalse(timer.update(loading.copy(ready = true), 13_000), "Advance only once per displayed entry")
    }

    @Test fun failedOrHungImageSkipsAtSharedTimeoutWithoutAnotherDisplayInterval() {
        val timer = timer(duration = 60_000, timeout = 1200)
        val failed = ImagePlaybackFrame("failed", false)
        timer.update(failed, 100)
        assertFalse(timer.update(failed, 1299))
        assertTrue(timer.update(failed, 1300))
        assertEquals(0f, timer.progress)
        assertFalse(timer.update(failed.copy(ready = true), 1400), "A late result cannot advance twice")
    }

    @Test fun preloadedImageStartsCountingWhenItBecomesCurrent() {
        val timer = timer()
        timer.update(ImagePlaybackFrame("A", true), 0)
        timer.update(ImagePlaybackFrame("A", true), 400)
        assertFalse(timer.update(ImagePlaybackFrame("B", true), 450))
        assertEquals(0f, timer.progress)
        assertFalse(timer.update(ImagePlaybackFrame("B", true), 949))
        assertTrue(timer.update(ImagePlaybackFrame("B", true), 950))
    }

    @Test fun manualPageChangeAndModelReplacementResetBothTimers() {
        val timer = timer(timeout = 1000)
        timer.update(ImagePlaybackFrame("placeholder", false), 0)
        timer.update(ImagePlaybackFrame("placeholder", false), 900)
        assertFalse(timer.update(ImagePlaybackFrame("real image", false), 950))
        assertFalse(timer.update(ImagePlaybackFrame("real image", false), 1949))
        assertTrue(timer.update(ImagePlaybackFrame("real image", false), 1950))
    }

    @Test fun scrollingPausesBothClocksAndSingleImageDoesNotAutoAdvance() {
        val timer = timer(timeout = 1000)
        val frame = ImagePlaybackFrame("A", false)
        timer.update(frame, 0)
        timer.update(frame, 500)
        timer.update(frame.copy(paused = true), 600)
        assertFalse(timer.update(frame.copy(paused = true), 9000))
        assertFalse(timer.update(frame, 10_000))
        assertTrue(timer.update(frame, 10_500))
        timer.update(frame.copy(enabled = false), 11_000)
        assertFalse(timer.update(frame.copy(enabled = false), 100_000))
    }

    @Test fun reloadDoesNotReusePreviouslyAccumulatedDisplayTime() {
        val timer = timer()
        val frame = ImagePlaybackFrame("A", true)
        timer.update(frame, 0)
        timer.update(frame, 400)
        timer.update(frame.copy(ready = false), 450)
        assertEquals(0f, timer.progress)
        timer.update(frame, 600)
        assertFalse(timer.update(frame, 1099))
        assertTrue(timer.update(frame, 1100))
    }

    @Test fun invalidDisplayIntervalUsesDefaultAndTimeoutMustBePositive() {
        val timer = timer(duration = 0)
        val frame = ImagePlaybackFrame("A", true)
        timer.update(frame, 0)
        assertFalse(timer.update(frame, DEFAULT_PERIOD - 1))
        assertTrue(timer.update(frame, DEFAULT_PERIOD))
        assertFailsWith<IllegalArgumentException> { ImagePlaybackConfig(0) }
    }

    @Test fun reloadObservedDuringPauseAlsoStartsANewFullDisplayInterval() {
        val timer = timer()
        val ready = ImagePlaybackFrame("A", true)
        timer.update(ready, 0)
        timer.update(ready, 400)
        timer.update(ready.copy(paused = true, ready = false), 450)
        timer.update(ready.copy(paused = true), 600)
        assertFalse(timer.update(ready, 1000))
        assertEquals(0f, timer.progress)
        assertFalse(timer.update(ready, 1499))
        assertTrue(timer.update(ready, 1500))
    }
}
