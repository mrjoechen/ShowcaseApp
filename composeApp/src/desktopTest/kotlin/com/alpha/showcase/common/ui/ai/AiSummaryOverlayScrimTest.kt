package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import coil3.asImage
import com.alpha.showcase.common.ai.AiGenerationTestTheme
import com.alpha.showcase.common.ai.AiSummaryContent
import com.alpha.showcase.common.ai.AiSummaryState
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color as SkiaColor

@OptIn(ExperimentalTestApi::class)
class AiSummaryOverlayScrimTest {
    @Test fun summaryScrimDarkensFromBottomLeftTowardTopRight() = runDesktopComposeUiTest(width = 600, height = 400) {
        val bitmap = Bitmap().apply { allocN32Pixels(600, 400); erase(SkiaColor.WHITE) }
        setContent {
            DisposableEffect(Unit) { onDispose { bitmap.close() } }
            val image = remember(bitmap) { bitmap.asImage() }
            AiGenerationTestTheme {
                Box(Modifier.fillMaxSize().background(Color.White).testTag("summary-overlay")) {
                    AiSummaryOverlay(
                        state = AiSummaryState(
                            content = AiSummaryContent(
                                summary = "Snow",
                                narration = "A bright snowy field under a clear sky",
                                tags = listOf("snow", "sky"),
                            ),
                        ),
                        image = image,
                        fit = false,
                        hasProfile = true,
                        regenerate = {},
                    )
                }
            }
        }
        waitForIdle()
        val pixels = onNodeWithTag("summary-overlay").captureToImage().toPixelMap()
        fun luma(x: Int, y: Int): Float {
            val color = pixels[x, y]
            return 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        }
        val inset = 8
        val bottomLeft = luma(inset, pixels.height - 1 - inset)
        val bottomRight = luma(pixels.width - 1 - inset, pixels.height - 1 - inset)
        val topLeft = luma(inset, inset)
        val topRight = luma(pixels.width - 1 - inset, inset)
        val upperLeft = luma(inset, pixels.height / 2)
        assertTrue(bottomLeft < 0.5f,
            "Bottom-left of the caption band should be darkened: BL=$bottomLeft")
        assertTrue(bottomLeft < topRight - 0.25f,
            "Bottom-left should be much darker than top-right: BL=$bottomLeft TR=$topRight")
        assertTrue(bottomLeft < bottomRight - 0.08f,
            "Scrim must still fade toward the right: BL=$bottomLeft BR=$bottomRight")
        assertTrue(topLeft > 0.85f,
            "A wide caption rectangle must not throw a heavy shadow up the image: TL=$topLeft")
        assertTrue(upperLeft > 0.85f,
            "The upper half of the image should stay clear: midLeft=$upperLeft")
        val endX = (pixels.width * 0.47f).toInt().coerceIn(0, pixels.width - 1)
        val endY = (pixels.height * 0.58f).toInt().coerceIn(0, pixels.height - 1)
        val gradientEnd = luma(endX, endY)
        assertTrue(gradientEnd > 0.9f,
            "Gradient end must be transparent or the scrim reads as a stacked band: end=$gradientEnd")
    }
}
