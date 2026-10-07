package com.alpha.showcase.common.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformRootForTest
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsDropdownTest {
    @OptIn(InternalComposeUiApi::class)
    @Test fun settingsChoiceHandlesAnUnboundedPopupScene() = runTest {
        val frameRecomposer = FrameRecomposer(coroutineContext)
        val roots = mutableListOf<PlatformRootForTest>()
        val platformContext = object : PlatformContext by PlatformContext.Empty() {
            override val windowInfo = object : WindowInfo {
                override val isWindowFocused = true
                override val containerSize = IntSize(400, 800)
            }
            override val rootForTestListener = object : PlatformContext.RootForTestListener {
                override fun onRootForTestCreated(root: PlatformRootForTest) { roots += root }
                override fun onRootForTestDisposed(root: PlatformRootForTest) { roots -= root }
            }
        }
        // Keep scene.size null to replay the unbounded popup measurement from the iOS trace.
        // Only the settings page has a size; its popup lives in a separate layout root.
        val scene = CanvasLayersComposeScene(frameRecomposer, platformContext = platformContext)
        try {
            scene.setContent {
                MaterialTheme {
                    Column(Modifier.requiredSize(400.dp, 800.dp)) {
                        CheckItem(Icons.Outlined.Style, 0 to "Slide", "Style",
                            listOf(0 to "Slide", 1 to "Calendar")) {}
                    }
                }
            }
            fun frame(time: Long) {
                frameRecomposer.performFrame(time)
                scene.measureAndLayout()
            }
            frame(0)
            assertEquals(1, roots.size)
            scene.sendPointerEvent(PointerEventType.Press, Offset(200f, 40f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(200f, 40f))
            frame(16_000_000)
            frame(32_000_000)
            assertEquals(2, roots.size, "The test must actually open the settings popup")
            val popupHeight = roots.last().semanticsOwner.rootSemanticsNode.size.height
            assertTrue(popupHeight in 1..800, "Popup must have a finite, visible height")
        } finally {
            scene.close()
            frameRecomposer.close()
        }
    }

    @Test fun longSettingsMenuCanScrollToAndSelectItsLastChoice() = runDesktopComposeUiTest(width = 400, height = 320) {
        val choices = List(30) { it to "Choice $it" }
        var selected = choices.first()
        setContent {
            var value by remember { mutableStateOf(selected) }
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    CheckItem(Icons.Outlined.Style, value, "Style", choices) {
                        selected = it
                        value = it
                    }
                }
            }
        }
        onNodeWithText("Style").performClick()
        onNodeWithText("Choice 29").performScrollTo().assertIsDisplayed().performClick()
        runOnIdle { assertEquals(choices.last(), selected) }
        onAllNodes(isPopup()).assertCountEquals(0)
        onNodeWithText("Style").performClick()
        onAllNodes(isPopup()).assertCountEquals(1)
    }

    @Test fun settingsChoiceCanOpenAndSelectInsideScrollingPage() = runDesktopComposeUiTest {
        val choices = listOf(0 to "Slide", 1 to "Calendar")
        var selected = choices.first()
        setContent {
            var value by remember { mutableStateOf(selected) }
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    CheckItem(Icons.Outlined.Style, value, "Style", choices) {
                        selected = it
                        value = it
                    }
                }
            }
        }
        onNodeWithText("Style").performClick()
        onNodeWithText("Calendar").assertIsDisplayed().performClick()
        runOnIdle { assertEquals(choices[1], selected) }
        onAllNodes(isPopup()).assertCountEquals(0)
    }
}
