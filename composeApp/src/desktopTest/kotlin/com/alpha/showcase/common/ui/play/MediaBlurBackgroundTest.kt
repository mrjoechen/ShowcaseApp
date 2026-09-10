package com.alpha.showcase.common.ui.play

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.Image
import coil3.PlatformContext
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class MediaBlurBackgroundTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun enlargedBackgroundPreservesRecognizableDetails() = runDesktopComposeUiTest {
        val source = object : Image {
            override val width = 512
            override val height = 1024
            override val size = 0L
            override val shareable = true
            override fun draw(canvas: Canvas) {
                Paint().use { paint ->
                    repeat(16) { stripe ->
                        paint.color = if (stripe % 2 == 0) 0xffffffff.toInt() else 0xff000000.toInt()
                        canvas.drawRect(Rect.makeXYWH(stripe * 32f, 0f, 32f, 1024f), paint)
                    }
                }
            }
        }
        setContent {
            MediaBlurBackground(source, Modifier.size(512.dp, 256.dp).testTag("detail"))
        }
        val pixels = onNodeWithTag("detail").captureToImage().toPixelMap()
        val light = pixels[pixels.width * 9 / 32, pixels.height / 2].red
        val dark = pixels[pixels.width * 11 / 32, pixels.height / 2].red
        assertTrue(light - dark > 0.65f, "Background detail contrast: ${light - dark}")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun backgroundFillsBoundsAndReusesRasterAcrossRecomposition() = runDesktopComposeUiTest {
        var draws = 0
        val tag = mutableStateOf("first")
        val source = object : Image by splitImage() {
            override fun draw(canvas: Canvas) {
                draws++
                splitImage().draw(canvas)
            }
        }
        setContent {
            MediaBlurBackground(source, Modifier.size(200.dp, 100.dp).testTag(tag.value))
        }
        val pixels = onNodeWithTag("first").captureToImage().toPixelMap()
        assertEquals(1f, pixels[0, 0].alpha)
        assertEquals(1f, pixels[pixels.width - 1, pixels.height - 1].alpha)
        runOnIdle { tag.value = "second" }
        onNodeWithTag("second").captureToImage()
        runOnIdle { assertEquals(1, draws) }
    }

    @Test fun fillsViewportWithoutDarkEdgesAndSoftensColorBoundary() {
        val source = splitImage()
        val background = createMediaBlur(source, PlatformContext.INSTANCE, 64, 36)
        assertEquals(64, background.width)
        assertEquals(36, background.height)
        val pixels = background.toPixelMap()
        assertEquals(1f, pixels[0, 0].alpha)
        assertEquals(1f, pixels[63, 35].alpha)
        assertEquals(1f, pixels[0, 18].red)
        assertEquals(1f, pixels[63, 18].blue)
        assertTrue(pixels[31, 18].red in 0.1f..0.9f)
        assertTrue(pixels[31, 18].blue in 0.1f..0.9f)
        assertFailsWith<IllegalArgumentException> {
            createMediaBlur(source, PlatformContext.INSTANCE, 3840, 2160)
        }
    }

    private fun splitImage() = object : Image {
        override val width = 100
        override val height = 200
        override val size = 0L
        override val shareable = true
        override fun draw(canvas: Canvas) {
            Paint().use { paint ->
                paint.color = 0xffff0000.toInt()
                canvas.drawRect(Rect.makeWH(50f, 200f), paint)
                paint.color = 0xff0000ff.toInt()
                canvas.drawRect(Rect.makeXYWH(50f, 0f, 50f, 200f), paint)
            }
        }
    }
}
