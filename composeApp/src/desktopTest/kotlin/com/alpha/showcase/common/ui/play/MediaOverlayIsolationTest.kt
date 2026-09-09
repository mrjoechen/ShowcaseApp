package com.alpha.showcase.common.ui.play

import LocalImageLoader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.alpha.showcase.common.ui.ai.AiPlaybackContext
import com.alpha.showcase.common.ui.settings.*
import coil3.asImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.compose.resources.getString
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ai_generate_action
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class MediaOverlayIsolationTest {
    @Test fun overlayCrossfadeKeepsEachEntryBoundUntilItLeavesAndThenReleasesPixels() = runDesktopComposeUiTest {
        val bitmap = Bitmap().apply { allocN32Pixels(80, 40) }
        val first = MediaItemState("first.png").apply {
            loaded(bitmap.asImage(), MediaMetadata(listOf(MediaMetadataEntry(MediaMetadataKind.Description, "Outgoing info"))))
        }
        val second = MediaItemState("second.png").apply {
            loaded(bitmap.asImage(), MediaMetadata(listOf(MediaMetadataEntry(MediaMetadataKind.Description, "Incoming info"))))
        }
        var current by mutableStateOf(first)
        mainClock.autoAdvance = false
        setContent {
            DisposableEffect(Unit) { onDispose { bitmap.close() } }
            MediaOverlayTransition(current, SHOWCASE_MODE_FADE, showMetadata = true,
                config = MediaOverlayConfig(metadata = true))
        }
        mainClock.advanceTimeBy(500)
        onNodeWithText("Outgoing info").assertIsDisplayed()
        runOnIdle { current = second }
        mainClock.advanceTimeBy(200)
        onNodeWithText("Outgoing info").assertExists()
        onNodeWithText("Incoming info").assertExists()
        mainClock.advanceTimeBy(500)
        onNodeWithText("Outgoing info").assertDoesNotExist()
        onNodeWithText("Incoming info").assertIsDisplayed()
        assertNull(first.displayedImage)
        assertNotNull(second.displayedImage)
    }

    @Test fun mediaEffectCannotScaleMetadataOrGenerationButtonAndConfigCanRemoveBoth() = runDesktopComposeUiTest {
        val data = DataWithType(metadataFixture("Stationary metadata"), "png")
        var scale by mutableFloatStateOf(1f)
        var config by mutableStateOf(MediaOverlayConfig.forStyle(SHOWCASE_MODE_FADE))
        var loaded = false
        mainClock.autoAdvance = false
        setContent {
            WithMetadataLoader { AiPlaybackContext(Settings(showcaseMode = SHOWCASE_MODE_FADE), active = true) {
                MediaPresentation(
                    modifier = Modifier.fillMaxSize().testTag("viewport"), data = data,
                    parentType = SHOWCASE_MODE_FADE, overlayConfig = config,
                    mediaEffect = { media ->
                        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }) { media() }
                    },
                    onImageDimensionsAvailable = { _, _ -> loaded = true },
                )
            } }
        }
        waitUntil(timeoutMillis = 15_000) { mainClock.advanceTimeByFrame(); loaded }
        mainClock.advanceTimeBy(800)
        onNodeWithTag("viewport").performTouchInput { click(center) }
        mainClock.advanceTimeBy(500)
        val label = onNodeWithText("Stationary metadata")
        val button = onNodeWithContentDescription(getString(Res.string.ai_generate_action))
        label.assertIsDisplayed()
        button.assertIsDisplayed().assertHasClickAction()
        val labelBounds = label.fetchSemanticsNode().boundsInRoot
        val buttonBounds = button.fetchSemanticsNode().boundsInRoot
        runOnIdle { scale = 1.3f }
        mainClock.advanceTimeBy(100)
        assertEquals(labelBounds, label.fetchSemanticsNode().boundsInRoot)
        assertEquals(buttonBounds, button.fetchSemanticsNode().boundsInRoot)
        runOnIdle { config = MediaOverlayConfig.None }
        mainClock.advanceTimeBy(500)
        label.assertDoesNotExist()
        button.assertDoesNotExist()
        runOnIdle { config = MediaOverlayConfig.forStyle(SHOWCASE_MODE_FADE) }
        mainClock.advanceTimeBy(500)
        label.assertDoesNotExist()
        button.assertDoesNotExist()
    }

    @Test fun newImageNeverInheritsOldMetadataOrInteractionAndActionsAutoHide() = runDesktopComposeUiTest {
        var data by mutableStateOf(DataWithType(metadataFixture("First entry"), "png"))
        var loaded = 0
        mainClock.autoAdvance = false
        setContent {
            WithMetadataLoader { AiPlaybackContext(Settings(showcaseMode = SHOWCASE_MODE_FADE), active = true) {
                MediaPresentation(Modifier.fillMaxSize().testTag("viewport"), data,
                    parentType = SHOWCASE_MODE_FADE, onImageDimensionsAvailable = { _, _ -> loaded++ })
            } }
        }
        waitUntil(timeoutMillis = 15_000) { mainClock.advanceTimeByFrame(); loaded == 1 }
        mainClock.advanceTimeBy(800)
        onNodeWithTag("viewport").performTouchInput { click(center) }
        mainClock.advanceTimeBy(500)
        onNodeWithText("First entry").assertIsDisplayed()
        runOnIdle { data = DataWithType(metadataFixture("Second entry"), "png") }
        waitUntil(timeoutMillis = 15_000) { mainClock.advanceTimeByFrame(); loaded == 2 }
        mainClock.advanceTimeBy(800)
        onNodeWithText("First entry").assertDoesNotExist()
        onNodeWithText("Second entry").assertDoesNotExist()
        val button = onNodeWithContentDescription(getString(Res.string.ai_generate_action))
        button.assertDoesNotExist()
        onNodeWithTag("viewport").performTouchInput { click(center) }
        mainClock.advanceTimeBy(500)
        onNodeWithText("Second entry").assertIsDisplayed()
        button.assertIsDisplayed()
        mainClock.advanceTimeBy(5500)
        onNodeWithText("Second entry").assertDoesNotExist()
        button.assertDoesNotExist()
    }

    @Test fun multiPhotoStyleSuppressesEvenExplicitlyEnabledOverlays() = runDesktopComposeUiTest {
        val data = DataWithType(metadataFixture("No overlays"), "png")
        var loaded = false
        setContent {
            WithMetadataLoader { AiPlaybackContext(Settings(showcaseMode = SHOWCASE_MODE_SQUARE), active = true) {
                MediaPresentation(Modifier.fillMaxSize().testTag("viewport"), data,
                    parentType = SHOWCASE_MODE_SQUARE,
                    overlayConfig = MediaOverlayConfig(true, true, true),
                    onImageDimensionsAvailable = { _, _ -> loaded = true })
            } }
        }
        waitUntil(timeoutMillis = 15_000) { loaded }
        onNodeWithTag("viewport").performTouchInput { click(center) }
        mainClock.advanceTimeBy(1000)
        onNodeWithText("No overlays").assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_generate_action)).assertDoesNotExist()
    }

    @Test fun activityOverSiblingGenerationButtonRestartsTheAutoHideTimer() = runDesktopComposeUiTest {
        val data = DataWithType(metadataFixture("Keep visible during activity"), "png")
        var loaded = false
        mainClock.autoAdvance = false
        setContent {
            WithMetadataLoader {
                AiPlaybackContext(Settings(showcaseMode = SHOWCASE_MODE_FADE), active = true) {
                    MediaPresentation(
                        modifier = Modifier.fillMaxSize().testTag("viewport"),
                        data = data,
                        parentType = SHOWCASE_MODE_FADE,
                        onImageDimensionsAvailable = { _, _ -> loaded = true },
                    )
                }
            }
        }
        waitUntil(timeoutMillis = 15_000) { mainClock.advanceTimeByFrame(); loaded }
        mainClock.advanceTimeBy(800)
        onNodeWithTag("viewport").performTouchInput { click(center) }
        mainClock.advanceTimeBy(4500)
        val button = onNodeWithContentDescription(getString(Res.string.ai_generate_action))
        val metadata = onNodeWithText("Keep visible during activity")
        button.assertIsDisplayed()
        button.performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(1000)
        button.assertIsDisplayed()
        metadata.assertIsDisplayed()
        mainClock.advanceTimeBy(4500)
        button.assertDoesNotExist()
        metadata.assertDoesNotExist()
        // Keep the cursor at the removed button's position: layout-driven hover
        // updates must not count as a new movement and reopen the controls.
        mainClock.advanceTimeBy(1000)
        button.assertDoesNotExist()
        metadata.assertDoesNotExist()
    }
}

@Composable
private fun WithMetadataLoader(content: @Composable () -> Unit) {
    val loader = remember { metadataImageLoader() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    CompositionLocalProvider(LocalImageLoader provides loader, content = content)
}
