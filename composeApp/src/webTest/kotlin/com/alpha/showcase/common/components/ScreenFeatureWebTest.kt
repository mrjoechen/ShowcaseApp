package com.alpha.showcase.common.components

import getScreenFeature
import kotlin.test.Test
import kotlin.test.assertNull

class ScreenFeatureWebTest {
    @Test
    fun exitingFullscreenWhenTheDocumentIsInactiveIsSafe() {
        installInactiveDocumentExitFullscreen()

        val failure = try {
            runCatching { getScreenFeature().exitFullScreen() }.exceptionOrNull()
        } finally {
            restoreExitFullscreen()
        }

        assertNull(failure)
    }
}

private fun installInactiveDocumentExitFullscreen(): Unit = js(
    "(() => { " +
        "globalThis.__showcaseOriginalExitFullscreen = document.exitFullscreen; " +
        "document.exitFullscreen = () => { throw new TypeError('Document not active'); }; " +
        "})()"
)

private fun restoreExitFullscreen(): Unit = js(
    "(() => { " +
        "document.exitFullscreen = globalThis.__showcaseOriginalExitFullscreen; " +
        "delete globalThis.__showcaseOriginalExitFullscreen; " +
        "})()"
)
