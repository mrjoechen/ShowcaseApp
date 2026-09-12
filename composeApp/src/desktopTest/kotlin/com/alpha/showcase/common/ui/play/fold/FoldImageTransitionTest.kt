package com.alpha.showcase.common.ui.play.fold

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.runDesktopComposeUiTest
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.intercept.Interceptor
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FoldImageTransitionTest {
    @Test fun retreatKeepsTheEntireFoldInsideTheViewport() {
        for (direction in FoldDirection.entries) for (step in 0..100) {
            val p = step / 100f
            val frame = foldFrame(p, direction, 400f, 300f)
            val scale = foldRetreatScale(p)
            val scaledTop = 150f + (frame.outerTop - 150f) * scale
            val scaledBottom = 150f + (150f - frame.outerTop) * scale
            assertTrue(scaledTop >= -.001f && scaledBottom <= 300.001f,
                "Fold must stay in the viewport throughout the gesture: p=$p, direction=$direction")
        }
        assertEquals(1f, foldRetreatScale(0f))
        assertEquals(1f, foldRetreatScale(1f))
        assertEquals(1f, foldRetreatScale(Float.NaN))
    }

    @Test fun fullscreenRetreatSwitchControlsScalingAndReturnsAfterGestures() {
        for ((width, height) in listOf(360 to 640, 640 to 360)) {
            for (enabled in listOf(true, false)) runDesktopComposeUiTest(width = width, height = height) {
                setContent { FoldImageDemoContent(List(8) { ColorPainter(Color.White) }) }
                if (!enabled) onNodeWithTag("fold-retreat").performScrollTo().performClick()
                onNodeWithTag("fold-fullscreen").performScrollTo().performClick()
                val background = Color(0xFF141716)
                fun edgeColor() = onRoot().captureToImage().toPixelMap()[width / 2, 2]
                assertColor(Color.White, edgeColor())
                for (sign in listOf(-1f, 1f)) {
                    onNodeWithTag("fold-image").performTouchInput {
                        down(Offset(width * .5f, height * .5f))
                        moveBy(Offset(sign * width * .3f, 0f), delayMillis = 120)
                    }
                    assertColor(if (enabled) background else Color.White, edgeColor())
                    if (enabled && sign < 0f) screenshot("fullscreen-retreat-$width", onRoot().captureToImage())
                    onNodeWithTag("fold-image").performTouchInput { up() }
                    mainClock.advanceTimeBy(2000)
                    waitForIdle()
                    assertColor(Color.White, edgeColor())
                }
                onNodeWithTag("fold-image").assertContentDescriptionEquals("山脊 · Alpine ridge")
                // A short drag rewinds instead of changing the image, restoring full size too.
                onNodeWithTag("fold-image").performTouchInput {
                    down(Offset(width * .5f, height * .5f))
                    moveBy(Offset(-width * .1f, 0f), delayMillis = 120)
                    up()
                }
                mainClock.advanceTimeBy(500)
                waitForIdle()
                assertColor(Color.White, edgeColor())
                onNodeWithTag("fold-image").assertContentDescriptionEquals("山脊 · Alpine ridge")
                onNodeWithTag("fold-controls").assertDoesNotExist()
            }
        }
    }

    @Test fun imageCoverageSoftensAcrossBothEdgesInsideTheFold() {
        for (direction in FoldDirection.entries) runDesktopComposeUiTest(width = 480, height = 380) {
            val progress = mutableFloatStateOf(.28f)
            val blur = mutableStateOf(true)
            val white = ColorPainter(Color.White)
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Magenta).padding(40.dp)) {
                    FoldImageTransition(white, white, { progress.floatValue },
                        Modifier.fillMaxSize(), direction = direction, blurEnabled = blur.value,
                        cornerRadius = 24.dp)
                }
            }
            for (p in listOf(.28f, .72f)) {
                runOnIdle { progress.floatValue = p; blur.value = true }
                val frame = foldFrame(p, direction, 400f, 300f)
                val x = 40 + if (frame.movingLeft) 80 else 320
                val softened = onRoot().captureToImage().toPixelMap()
                runOnIdle { blur.value = false }
                val sharp = onRoot().captureToImage().toPixelMap()
                for ((outside, inside) in listOf(37 to 43, 343 to 337)) {
                    assertTrue(softened[x, outside].green > sharp[x, outside].green + .02f,
                        "Photo color must spread beyond its original edge: p=$p, y=$outside, direction=$direction")
                    assertTrue(softened[x, inside].green < sharp[x, inside].green - .02f,
                        "Coverage must soften inward too, without a sharp underlying image edge")
                }
                assertColor(Color.Magenta, softened[x, 0])
            }
            for (p in listOf(0f, 1f)) {
                runOnIdle { progress.floatValue = p; blur.value = true }
                val pixels = onRoot().captureToImage().toPixelMap()
                assertColor(Color.Magenta, pixels[120, 37])
                assertColor(Color.White, pixels[120, 43])
            }
        }
    }

    @Test fun urlLoadingWaitsForPhotosAndRetriesOnlyFailures() = runDesktopComposeUiTest(width = 360, height = 640) {
        val release = CompletableDeferred<Unit>()
        val calls = ConcurrentHashMap<String, Int>()
        val bytes = Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(80, 60)
            bitmap.erase(0xff46676c.toInt())
            org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }
            }
        }
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).components {
            add(Interceptor { chain ->
                val url = chain.request.data as String
                val attempt = calls.merge(url, 1) { a, b -> a + b }!!
                release.await()
                if (url == FoldDemoPhotoUrls.last() && attempt == 1) throw IOException("Simulated offline image")
                chain.withRequest(chain.request.newBuilder().data(bytes).build()).proceed()
            })
        }.build()
        try {
            setContent { FoldImageNetworkDemo(loader) }
            onNodeWithTag("fold-load-status").assertTextEquals("已加载 0 / 8 张图片")
            onNodeWithTag("fold-image").assertDoesNotExist()
            release.complete(Unit)
            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(androidx.compose.ui.test.hasText("已加载 7 / 8 张图片"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("fold-load-error").assertIsDisplayed()
            onNodeWithTag("fold-image").assertDoesNotExist()
            onNodeWithTag("fold-load-retry").performClick()
            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(androidx.compose.ui.test.hasTestTag("fold-image")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("fold-image").assertContentDescriptionEquals("山脊 · Alpine ridge")
            onNodeWithTag("fold-next").performClick()
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            onNodeWithTag("fold-image").assertContentDescriptionEquals("湖畔 · Still water")
            onNodeWithTag("fold-fullscreen").performClick()
            onNodeWithTag("fold-image").performTouchInput { swipeRight() }
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            onNodeWithTag("fold-image").assertContentDescriptionEquals("山脊 · Alpine ridge")
            FoldDemoPhotoUrls.forEach { url ->
                assertEquals(if (url == FoldDemoPhotoUrls.last()) 2 else 1, calls[url],
                    "Successful images must stay loaded across animation and fullscreen changes")
            }
        } finally {
            release.complete(Unit)
            loader.shutdown()
        }
    }

    @Test fun hingeDoesNotDarkenAnOtherwiseContinuousImage() {
        // An odd width puts the hinge between pixels, exercising fractional clip coverage.
        for (direction in FoldDirection.entries) runDesktopComposeUiTest(width = 401, height = 301) {
            val progress = mutableFloatStateOf(.28f)
            val white = ColorPainter(Color.White)
            setContent {
                FoldImageTransition(white, white, { progress.floatValue },
                    Modifier.fillMaxSize(), direction = direction)
            }
            for (p in listOf(.08f, .28f, .49f, .5f, .51f, .72f, .92f)) {
                runOnIdle { progress.floatValue = p }
                val pixels = onRoot().captureToImage().toPixelMap()
                for (x in 198..202) for (y in 40..260 step 20) {
                    val color = pixels[x, y]
                    assertTrue(color.red > .99f && color.green > .99f && color.blue > .99f,
                        "Dark hinge at $x,$y, progress=$p, direction=$direction: $color")
                }
            }
        }
    }

    @Test fun endpointsAndStationaryHalvesStayCorrectInBothDirections() {
        for (direction in FoldDirection.entries) runDesktopComposeUiTest(width = 400, height = 300) {
            val progress = mutableFloatStateOf(0f)
            setContent {
                FoldImageTransition(ColorPainter(Color.Red), ColorPainter(Color.Blue),
                    { progress.floatValue }, Modifier.fillMaxSize(), direction = direction)
            }
            fun verifyAt(p: Float, left: Color, right: Color) {
                runOnIdle { progress.floatValue = p }
                val pixels = onRoot().captureToImage().toPixelMap()
                assertColor(left, pixels[40, 150])
                assertColor(right, pixels[360, 150])
            }
            verifyAt(0f, Color.Red, Color.Red)
            val left = if (direction == FoldDirection.Forward) Color.Blue else Color.Red
            val right = if (direction == FoldDirection.Forward) Color.Red else Color.Blue
            // Check both sides of the face swap, not just the exact mathematical midpoint.
            verifyAt(.499f, left, right)
            verifyAt(.501f, left, right)
            verifyAt(1f, Color.Blue, Color.Blue)
            verifyAt(Float.NaN, Color.Red, Color.Red)
        }
    }

    @Test fun previewScrubbingBlurAndNextPreviousWork() = runDesktopComposeUiTest(width = 640, height = 560) {
        setContent { FoldImageDemoContent(foldPreviewPainters()) }
        onNodeWithTag("fold-image").assertContentDescriptionEquals("山脊 · Alpine ridge")
        screenshot("landscape-flat", onRoot().captureToImage())
        onNodeWithTag("fold-progress").performSemanticsAction(SemanticsActions.SetProgress) { it(.28f) }
        val blurred = onNodeWithTag("fold-image").captureToImage()
        screenshot("landscape-fold", onRoot().captureToImage())
        onNodeWithContentDescription("渐变模糊").performClick()
        val sharp = onNodeWithTag("fold-image").captureToImage()
        assertTrue(pixelDifference(blurred, sharp) > .0001f, "Blur switch must change the folded image pixels")
        onNodeWithContentDescription("渐变模糊").performClick()
        onNodeWithTag("fold-next").performClick()
        mainClock.advanceTimeBy(2000)
        waitForIdle()
        onNodeWithTag("fold-image").assertContentDescriptionEquals("湖畔 · Still water")
        onNodeWithTag("fold-previous").performClick()
        mainClock.advanceTimeBy(2000)
        waitForIdle()
        onNodeWithTag("fold-image").assertContentDescriptionEquals("山脊 · Alpine ridge")
        // Scrubbing cancels autoplay; virtual time must no longer move the slider.
        onNodeWithTag("fold-play").performClick()
        onNodeWithTag("fold-progress").performSemanticsAction(SemanticsActions.SetProgress) { it(.72f) }
        mainClock.advanceTimeBy(6000)
        waitForIdle()
        onNodeWithTag("fold-progress-label").assertTextEquals("72%")
        screenshot("landscape-opening", onRoot().captureToImage())
    }

    @Test fun portraitPreviewRendersOffline() {
        for ((width, height) in listOf(420 to 900, 890 to 400)) runDesktopComposeUiTest(width = width, height = height) {
            setContent { FoldImageDemoContent(foldPreviewPainters(), initialProgress = .72f) }
            onNodeWithTag("fold-image").assertIsDisplayed()
            onNodeWithTag("fold-fullscreen").assertIsDisplayed()
            val photo = onNodeWithTag("fold-image").fetchSemanticsNode().boundsInRoot
            val stage = onNodeWithTag("fold-preview-stage").fetchSemanticsNode().boundsInRoot
            assertTrue(photo.top - stage.top >= photo.height * .13f, "Top margin: photo=$photo, stage=$stage")
            assertTrue(stage.bottom - photo.bottom >= photo.height * .13f, "Bottom margin: photo=$photo, stage=$stage")
            screenshot(if (width < height) "portrait-fold" else "phone-landscape-fold", onRoot().captureToImage())
        }
    }

    @Test fun fullscreenHasNoControlsAndSwipesInBothDirections() = runDesktopComposeUiTest(width = 360, height = 640) {
        setContent { FoldImageDemoContent(foldPreviewPainters()) }
        onNodeWithTag("fold-fullscreen").performClick()
        onNodeWithTag("fold-controls").assertDoesNotExist()
        onNodeWithTag("fold-image").performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(2000)
        waitForIdle()
        onNodeWithTag("fold-image").assertContentDescriptionEquals("湖畔 · Still water")
        onNodeWithTag("fold-image").performTouchInput { swipeRight() }
        mainClock.advanceTimeBy(2000)
        waitForIdle()
        onNodeWithTag("fold-image").assertContentDescriptionEquals("山脊 · Alpine ridge")
        onNodeWithTag("fold-controls").assertDoesNotExist()
        screenshot("fullscreen", onRoot().captureToImage())
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertTrue(abs(expected.red - actual.red) < .03f && abs(expected.blue - actual.blue) < .03f && actual.alpha > .99f,
            "Expected $expected, got $actual")
    }

    private fun pixelDifference(a: ImageBitmap, b: ImageBitmap): Float {
        val first = a.toPixelMap()
        val second = b.toPixelMap()
        var sum = 0f
        var count = 0
        for (y in 0 until a.height step 4) for (x in 0 until a.width step 4) {
            sum += abs(first[x, y].red - second[x, y].red)
            count++
        }
        return sum / count
    }

    private fun screenshot(name: String, image: ImageBitmap) {
        val folder = File("build/fold-verification").apply { mkdirs() }
        org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).use { snapshot ->
            snapshot.encodeToData(EncodedImageFormat.PNG)!!.use { File(folder, "$name.png").writeBytes(it.bytes) }
        }
    }
}
