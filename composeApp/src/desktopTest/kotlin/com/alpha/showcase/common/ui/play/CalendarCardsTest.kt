package com.alpha.showcase.common.ui.play

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import com.alpha.showcase.common.theme.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CalendarCardsTest {
    @Test
    fun chineseCardsUseVerticalLunarCaptionAndThemeColors() =
        verifyPages(Locale.SIMPLIFIED_CHINESE, dark = true, width = 304, height = 360)

    @Test
    fun englishCardsFitInAShortPanel() =
        verifyPages(Locale.US, dark = false, width = 360, height = 216)

    @Test
    fun sixWeekEnglishMonthFitsDuringALunarLeapMonth() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            runDesktopComposeUiTest(width = 210, height = 145) {
                setContent { MaterialTheme { CalendarPager(LocalDateTime(2025, 8, 18, 23, 58)) } }
                onRoot().performTouchInput { swipeLeft() }
                onNodeWithText("Aug").assertIsDisplayed()
                onNodeWithText("Lunar", substring = true).assertDoesNotExist()
                onNodeWithText("31").assertIsDisplayed()
                checkTextFits()
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    private fun verifyPages(locale: Locale, dark: Boolean, width: Int, height: Int) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            runDesktopComposeUiTest(width = width, height = height) {
                val chinese = locale == Locale.SIMPLIFIED_CHINESE
                val colors = if (dark) darkColorScheme(primary = Green80, onPrimary = Green20,
                    surface = Grey10, onSurface = Grey80, onSurfaceVariant = BlueGrey80)
                else lightColorScheme(primary = Green40, surface = Grey99,
                    onSurface = Grey10, onSurfaceVariant = BlueGrey30)
                setContent {
                    MaterialTheme(colorScheme = colors, typography = materialTypography) {
                        CalendarPager(LocalDateTime(2026, 11, 18, 9, 7))
                    }
                }

                onNodeWithText(if (chinese) "周三" else "Wed").assertIsDisplayed()
                if (chinese) onNodeWithContentDescription("十月初十").assertIsDisplayed()
                else onNodeWithText("Lunar", substring = true).assertDoesNotExist()
                checkTextFits()
                assertEquals(colors.surface, onRoot().captureToImage().toPixelMap()[0, 0])
                screenshot(locale, "day")

                onRoot().performTouchInput { swipeLeft() }
                onNodeWithText(if (chinese) "11月" else "Nov").assertIsDisplayed()
                onNodeWithText(if (chinese) "六" else "Sat").assertIsDisplayed()
                if (chinese) onNodeWithText("十月初十").assertIsDisplayed()
                else onNodeWithText("Lunar", substring = true).assertDoesNotExist()
                onNodeWithText("18").assertIsSelected()
                onNodeWithText("30").assertIsDisplayed()
                // November 9 starts the lunar month. Its underline sits below the digit in this column.
                val ninth = onNodeWithText("9").fetchSemanticsNode().boundsInRoot.center
                val sixteenth = onNodeWithText("16").fetchSemanticsNode().boundsInRoot.center
                val grid = onRoot().captureToImage().toPixelMap()
                val underlined = (ninth.y.toInt() until sixteenth.y.toInt()).any { y ->
                    grid[ninth.x.toInt(), y] == colors.primary
                }
                assertEquals(chinese, underlined)
                checkTextFits()
                screenshot(locale, "month")

                onRoot().performTouchInput { swipeLeft() }
                onNodeWithText(if (chinese) "2026年11月18日" else "Nov 18, 2026").assertIsDisplayed()
                onNodeWithText("09:07").assertIsDisplayed()
                checkTextFits()
                screenshot(locale, "clock")
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    private fun ComposeUiTest.checkTextFits() {
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        for (node in onAllNodes(matcher).fetchSemanticsNodes()) {
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            results.forEach { layout ->
                val text = layout.layoutInput.text
                val last = layout.lineCount - 1
                assertEquals(text.length, layout.getLineEnd(last, visibleEnd = true), text.text)
                for (line in 0..last) {
                    val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
                    assertTrue(lineWidth <= layout.size.width + 1, "Cropped line in $text")
                }
                assertTrue(layout.getLineBottom(last) <= layout.size.height + 1, "Cropped baseline in $text")
            }
        }
    }

    private fun ComposeUiTest.screenshot(locale: Locale, page: String) {
        val directory = File("build/reports/calendar-cards").apply { mkdirs() }
        Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { data ->
                File(directory, "${locale.language}-$page.png").writeBytes(data.bytes)
            }
        }
    }
}
