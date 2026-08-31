package com.alpha.showcase.common.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopKeepAwakeControllerTest {

    @Test
    fun desktopScreenFeatureDelegatesPlaybackLifecycleToKeepAwakeController() {
        val inhibitor = StrictInhibitor()
        val screenFeature = DesktopScreenFeatureDelegate(
            keepAwakeController = DesktopKeepAwakeController(inhibitor),
        )

        screenFeature.keepScreenOn(true)
        assertTrue(inhibitor.isInhibiting)

        screenFeature.keepScreenOn(false)
        assertFalse(inhibitor.isInhibiting)
    }

    @Test
    fun oneDisableRequestRetriesATransientFailureAndReleasesTheInhibitor() {
        val inhibitor = FailsFirstDisableInhibitor()
        val controller = DesktopKeepAwakeController(inhibitor)
        controller.setEnabled(true)

        val failure = runCatching { controller.setEnabled(false) }.exceptionOrNull()

        assertNull(failure)
        assertFalse(controller.isEnabled)
        assertFalse(inhibitor.isInhibiting)
    }

    @Test
    fun failedEnableReleasesPartialInhibitionAndLeavesControllerDisabled() {
        val inhibitor = PartiallyFailingEnableInhibitor()
        val controller = DesktopKeepAwakeController(inhibitor)

        val failure = runCatching { controller.setEnabled(true) }.exceptionOrNull()

        assertNull(failure)
        assertFalse(controller.isEnabled)
        assertFalse(inhibitor.isInhibiting)
    }

    @Test
    fun failedEnableRetainsCleanupStateWhenInitialReleaseRetriesAreExhausted() {
        val inhibitor = PartiallyFailingEnableAndReleaseInhibitor()
        val controller = DesktopKeepAwakeController(inhibitor)

        controller.setEnabled(true)

        assertTrue(controller.isEnabled)
        assertTrue(inhibitor.isInhibiting)

        controller.setEnabled(false)
        assertFalse(controller.isEnabled)
        assertFalse(inhibitor.isInhibiting)
    }

    @Test
    fun repeatedRequestsAreIdempotentAndLeaveTheOsInTheRequestedState() {
        val inhibitor = StrictInhibitor()
        val controller = DesktopKeepAwakeController(inhibitor)

        controller.setEnabled(true)
        controller.setEnabled(true)
        assertTrue(controller.isEnabled)
        assertTrue(inhibitor.isInhibiting)

        controller.setEnabled(false)
        controller.setEnabled(false)
        assertFalse(controller.isEnabled)
        assertFalse(inhibitor.isInhibiting)
    }

    @Test
    fun enableThenDisableChangesTheObservableInhibitionState() {
        val inhibitor = StrictInhibitor()
        val controller = DesktopKeepAwakeController(inhibitor)

        controller.setEnabled(true)
        assertTrue(controller.isEnabled)
        assertTrue(inhibitor.isInhibiting)

        controller.setEnabled(false)
        assertFalse(controller.isEnabled)
        assertFalse(inhibitor.isInhibiting)
    }

    private class StrictInhibitor : DesktopSleepInhibitor {
        var isInhibiting = false
            private set

        override fun enable() {
            check(!isInhibiting) { "duplicate native enable" }
            isInhibiting = true
        }

        override fun disable() {
            check(isInhibiting) { "duplicate native disable" }
            isInhibiting = false
        }
    }

    private class PartiallyFailingEnableInhibitor : DesktopSleepInhibitor {
        var isInhibiting = false
            private set

        override fun enable() {
            isInhibiting = true
            error("native enable failed")
        }

        override fun disable() {
            isInhibiting = false
        }
    }

    private class FailsFirstDisableInhibitor : DesktopSleepInhibitor {
        var isInhibiting = false
            private set
        private var failNextDisable = true

        override fun enable() {
            isInhibiting = true
        }

        override fun disable() {
            if (failNextDisable) {
                failNextDisable = false
                error("native disable failed")
            }
            isInhibiting = false
        }
    }

    private class PartiallyFailingEnableAndReleaseInhibitor : DesktopSleepInhibitor {
        var isInhibiting = false
            private set
        private var releaseFailuresRemaining = 3

        override fun enable() {
            isInhibiting = true
            error("native enable failed after partial acquisition")
        }

        override fun disable() {
            if (releaseFailuresRemaining > 0) {
                releaseFailuresRemaining -= 1
                error("transient native release failure")
            }
            isInhibiting = false
        }
    }
}
