package com.alpha.showcase.common.ui.play

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackScreenPolicyTest {

    @Test
    fun mobilePlaybackKeepsScreenOnWhenAutoFullscreenIsDisabled() {
        assertTrue(
            shouldKeepScreenOnDuringPlayback(
                isDesktop = false,
                autoFullscreen = false,
            ),
        )
    }

    @Test
    fun webPlaybackKeepsScreenOnOnlyWhenAutoFullscreenIsEnabled() {
        // Some browser user agents also match the desktop OS-name heuristic.
        for (isDesktop in listOf(false, true)) {
            assertFalse(
                shouldKeepScreenOnDuringPlayback(
                    isDesktop = isDesktop,
                    autoFullscreen = false,
                    isWeb = true,
                ),
            )
            assertTrue(
                shouldKeepScreenOnDuringPlayback(
                    isDesktop = isDesktop,
                    autoFullscreen = true,
                    isWeb = true,
                ),
            )
        }
    }

    @Test
    fun desktopPlaybackKeepsScreenOnOnlyWhenAutoFullscreenIsEnabled() {
        assertFalse(
            shouldKeepScreenOnDuringPlayback(
                isDesktop = true,
                autoFullscreen = false,
            ),
        )
        assertTrue(
            shouldKeepScreenOnDuringPlayback(
                isDesktop = true,
                autoFullscreen = true,
            ),
        )
    }
}
