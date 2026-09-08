package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.asImage
import com.alpha.showcase.common.ui.settings.*
import org.jetbrains.skia.Bitmap
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class KenBurnsMotionTest {
    @Test fun decodedPhotoLoopsWhileItsSiblingOverlayStaysFixed() = runDesktopComposeUiTest {
        val bitmap = Bitmap().apply { allocN32Pixels(80, 40) }
        val state = MediaItemState("motion.png")
        var style by mutableIntStateOf(SHOWCASE_MODE_FADE)
        mainClock.autoAdvance = false
        setContent {
            DisposableEffect(Unit) { onDispose { bitmap.close() } }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center).then(rememberKenBurnsModifier(state, style))
                    .size(200.dp).testTag("media").background(Color.Gray))
                Text("Fixed overlay", Modifier.align(Alignment.TopStart))
            }
        }
        mainClock.advanceTimeBy(1000)
        val image = onNodeWithTag("media")
        val overlay = onNodeWithText("Fixed overlay")
        val start = image.fetchSemanticsNode().boundsInRoot.width
        val overlayStart = overlay.fetchSemanticsNode().boundsInRoot
        mainClock.advanceTimeBy(15_000)
        assertEquals(start, image.fetchSemanticsNode().boundsInRoot.width, 0.1f)
        runOnIdle { state.loaded(bitmap.asImage()) }
        mainClock.advanceTimeBy(15_050)
        assertEquals(start * 1.08f, image.fetchSemanticsNode().boundsInRoot.width, 0.3f)
        assertEquals(overlayStart, overlay.fetchSemanticsNode().boundsInRoot)
        mainClock.advanceTimeBy(15_000)
        assertEquals(start, image.fetchSemanticsNode().boundsInRoot.width, 0.3f)
        runOnIdle { style = SHOWCASE_MODE_SLIDE }
        mainClock.advanceTimeBy(15_000)
        assertEquals(start, image.fetchSemanticsNode().boundsInRoot.width, 0.1f)
        runOnIdle { style = SHOWCASE_MODE_CALENDER }
        mainClock.advanceTimeBy(15_050)
        assertEquals(start * 1.08f, image.fetchSemanticsNode().boundsInRoot.width, 0.3f)
        assertEquals(overlayStart, overlay.fetchSemanticsNode().boundsInRoot)
    }
}
