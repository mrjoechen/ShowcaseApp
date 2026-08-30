package com.alpha.showcase.common.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class AppThemeStyleTest {

    @Test
    fun persistedValuesRestoreNewThemesWithPrintPalettePreviews() {
        val expectedThemes = listOf(
            ExpectedTheme(
                value = 7,
                name = "Atlas",
                previewColors = listOf(
                    Color(0xFFFAFAF7),
                    Color(0xFF2148B8),
                    Color(0xFFC65F38),
                ),
            ),
            ExpectedTheme(
                value = 8,
                name = "Herbarium",
                previewColors = listOf(
                    Color(0xFFF5F1E8),
                    Color(0xFF008A4B),
                    Color(0xFF8F3434),
                ),
            ),
            ExpectedTheme(
                value = 9,
                name = "Journal",
                previewColors = listOf(
                    Color(0xFFE9E9E5),
                    Color(0xFF5EB783),
                    Color(0xFF302D2E),
                ),
            ),
            ExpectedTheme(
                value = 10,
                name = "Signal",
                previewColors = listOf(
                    Color(0xFFE9E9E5),
                    Color(0xFF30343A),
                    Color(0xFFC83232),
                ),
            ),
        )

        expectedThemes.forEach { expected ->
            val actual = AppThemeStyle.fromValue(expected.value)

            assertEquals(expected.name, actual.name)
            assertEquals(expected.value, actual.value)
            assertEquals(expected.previewColors, actual.previewColors())
        }
    }

    @Test
    fun newThemesResolveToTheirApprovedLightAndDarkSubstrates() {
        val expectedBackgrounds = listOf(
            ExpectedBackgrounds(
                value = 7,
                light = Color(0xFFFAFAF7),
                dark = Color(0xFF1A1B28),
            ),
            ExpectedBackgrounds(
                value = 8,
                light = Color(0xFFF5F1E8),
                dark = Color(0xFF001C0F),
            ),
            ExpectedBackgrounds(
                value = 9,
                light = Color(0xFFE9E9E5),
                dark = Color(0xFF122018),
            ),
            ExpectedBackgrounds(
                value = 10,
                light = Color(0xFFE9E9E5),
                dark = Color(0xFF181C21),
            ),
        )

        expectedBackgrounds.forEach { expected ->
            val style = AppThemeStyle.fromValue(expected.value)

            assertEquals(expected.light, resolveThemeBackground(style, isDark = false))
            assertEquals(expected.dark, resolveThemeBackground(style, isDark = true))
        }
    }

    private data class ExpectedTheme(
        val value: Int,
        val name: String,
        val previewColors: List<Color>,
    )

    private data class ExpectedBackgrounds(
        val value: Int,
        val light: Color,
        val dark: Color,
    )
}
