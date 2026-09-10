package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.*
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class PlaybackActivityTest {
    @Test fun coveringPageOrBackgroundingCancelsPlaybackAndReturningResumes() = runDesktopComposeUiTest {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        var visible by mutableStateOf(true)
        var running = false
        var starts = 0
        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        setContent {
            CompositionLocalProvider(LocalPlaybackActive provides visible, LocalLifecycleOwner provides owner) {
                PlaybackEffect(Unit) {
                    starts++
                    running = true
                    try { awaitCancellation() } finally { running = false }
                }
            }
        }
        runOnIdle { assertTrue(running); assertEquals(1, starts); visible = false }
        waitForIdle()
        runOnIdle { assertFalse(running); visible = true }
        waitForIdle()
        runOnIdle { assertTrue(running); assertEquals(2, starts); owner.lifecycle.currentState = Lifecycle.State.STARTED }
        waitForIdle()
        runOnIdle { assertFalse(running); owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        waitForIdle()
        runOnIdle { assertTrue(running); assertEquals(3, starts) }
    }
}
