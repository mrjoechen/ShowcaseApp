package com.alpha.showcase.common.ui.play

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackWindowPolicyTest {

    @Test
    fun opensExternalPlaybackWindowOnlyWhenAutoFullscreenAndHandlerAreAvailable() {
        assertTrue(shouldOpenExternalPlaybackWindow(autoFullscreen = true, hasExternalPlaybackWindow = true))
        assertFalse(shouldOpenExternalPlaybackWindow(autoFullscreen = false, hasExternalPlaybackWindow = true))
        assertFalse(shouldOpenExternalPlaybackWindow(autoFullscreen = true, hasExternalPlaybackWindow = false))
        assertFalse(shouldOpenExternalPlaybackWindow(autoFullscreen = false, hasExternalPlaybackWindow = false))
    }

    @Test
    fun fullscreenPlaybackUsesMainWindowOnlyWhenExternalWindowsAreUnavailable() {
        assertTrue(
            shouldFullscreenMainPlaybackWindow(
                autoFullscreen = true,
                fullscreenRequested = true,
                hasExternalPlaybackWindow = false,
            )
        )
        assertFalse(
            shouldFullscreenMainPlaybackWindow(
                autoFullscreen = true,
                fullscreenRequested = true,
                hasExternalPlaybackWindow = true,
            )
        )
        assertFalse(
            shouldFullscreenMainPlaybackWindow(
                autoFullscreen = false,
                fullscreenRequested = true,
                hasExternalPlaybackWindow = false,
            )
        )
        assertFalse(
            shouldFullscreenMainPlaybackWindow(
                autoFullscreen = true,
                fullscreenRequested = false,
                hasExternalPlaybackWindow = false,
            )
        )
    }
}
