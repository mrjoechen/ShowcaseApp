package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.cache.GalleryMediaRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GalleryMediaGridTest {
    private val media = List(60) {
        GalleryMediaRecord("album", "file:///photo-$it.jpg", "Photo $it", "image/jpeg", 0)
    }

    @Test
    fun longPressAndDragSelectsAcrossRowsAndKeepsSelectionAfterRelease() = runComposeUiTest {
        var selected by mutableStateOf<Set<String>>(emptySet())
        setContent {
            MaterialTheme {
                GalleryMediaGrid(media, selected, { selected = it }, Modifier.size(328.dp, 340.dp).testTag("grid"))
            }
        }

        onNodeWithTag("grid").performTouchInput {
            down(Offset(52f, 52f))
            advanceEventTime(600)
            moveTo(Offset(276f, 164f), delayMillis = 100)
            up()
        }

        runOnIdle { assertEquals(media.take(6).map { it.mediaUri }.toSet(), selected) }
    }

    @Test
    fun longPressWithoutMovingTogglesOnlyOnceAndTapStillToggles() = runComposeUiTest {
        var selected by mutableStateOf<Set<String>>(emptySet())
        setContent {
            MaterialTheme {
                GalleryMediaGrid(media, selected, { selected = it }, Modifier.size(328.dp, 340.dp).testTag("grid"))
            }
        }
        val grid = onNodeWithTag("grid")
        grid.performTouchInput { longClick(Offset(52f, 52f)) }
        runOnIdle { assertEquals(setOf(media[0].mediaUri), selected) }
        grid.performTouchInput { click(Offset(164f, 52f)) }
        runOnIdle { assertEquals(media.take(2).map { it.mediaUri }.toSet(), selected) }
        grid.performTouchInput { click(Offset(52f, 52f)) }
        runOnIdle { assertEquals(setOf(media[1].mediaUri), selected) }
    }

    @Test
    fun ordinaryVerticalSwipeScrollsWithoutChangingTheSelection() = runComposeUiTest {
        val initial = setOf(media[0].mediaUri)
        var selected by mutableStateOf(initial)
        setContent {
            MaterialTheme {
                GalleryMediaGrid(media, selected, { selected = it }, Modifier.size(328.dp, 340.dp).testTag("grid"))
            }
        }

        onNodeWithTag("grid").performTouchInput { swipeUp() }

        runOnIdle { assertEquals(initial, selected) }
        val scroll = onNodeWithTag("grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scroll.value() > 0f, "A normal swipe must still scroll the grid")
    }

    @Test
    fun draggingFromASelectedPhotoCanClearTheWholeSelection() = runComposeUiTest {
        var selected by mutableStateOf(media.take(6).map { it.mediaUri }.toSet())
        setContent {
            MaterialTheme {
                GalleryMediaGrid(media, selected, { selected = it }, Modifier.size(328.dp, 340.dp).testTag("grid"))
            }
        }

        onNodeWithTag("grid").performTouchInput {
            down(Offset(52f, 52f))
            advanceEventTime(600)
            moveTo(Offset(276f, 164f), delayMillis = 100)
            up()
        }

        runOnIdle { assertTrue(selected.isEmpty()) }
    }

    @Test
    fun tappingTheGapClearsSelection() = runComposeUiTest {
        var selected by mutableStateOf(setOf(media[0].mediaUri))
        setContent {
            MaterialTheme {
                GalleryMediaGrid(media, selected, { selected = it }, Modifier.size(328.dp, 340.dp).testTag("grid"))
            }
        }

        onNodeWithTag("grid").performTouchInput { click(Offset(108f, 52f)) }

        runOnIdle { assertTrue(selected.isEmpty()) }
    }

    @Test
    fun holdingNearTheBottomScrollsAndSelectsPhotosBeyondTheViewport() = runComposeUiTest {
        var selected by mutableStateOf<Set<String>>(emptySet())
        mainClock.autoAdvance = false
        setContent {
            MaterialTheme {
                GalleryMediaGrid(media, selected, { selected = it }, Modifier.size(328.dp, 340.dp).testTag("grid"))
            }
        }

        val grid = onNodeWithTag("grid")
        grid.performTouchInput {
            down(Offset(52f, 52f))
            advanceEventTime(600)
            moveTo(Offset(276f, 330f), delayMillis = 100)
        }
        mainClock.advanceTimeBy(800)
        grid.performTouchInput { up() }
        mainClock.advanceTimeByFrame()

        runOnIdle {
            assertTrue(selected.size > 9, "Selection should continue past the first three rows: ${selected.size}")
            assertEquals(media.take(selected.size).map { it.mediaUri }.toSet(), selected)
        }
        val afterRelease = selected
        mainClock.advanceTimeBy(800)
        runOnIdle { assertEquals(afterRelease, selected, "Lifting the finger must stop auto-selection") }
    }
}
