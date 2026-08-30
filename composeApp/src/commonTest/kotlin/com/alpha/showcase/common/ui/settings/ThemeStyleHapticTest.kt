package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.theme.AppThemeStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeStyleHapticTest {

    @Test
    fun differentThemePerformsHapticFeedback() {
        var hapticCount = 0

        handleThemeStyleClick(
            currentThemeStyle = AppThemeStyle.Coast,
            selectedThemeStyle = AppThemeStyle.Forest,
            performHaptic = { hapticCount += 1 },
            onThemeSelected = {},
        )

        assertEquals(1, hapticCount)
    }

    @Test
    fun currentThemeSkipsHapticFeedback() {
        var hapticCount = 0

        handleThemeStyleClick(
            currentThemeStyle = AppThemeStyle.Coast,
            selectedThemeStyle = AppThemeStyle.Coast,
            performHaptic = { hapticCount += 1 },
            onThemeSelected = {},
        )

        assertEquals(0, hapticCount)
    }

    @Test
    fun themeClickStillSelectsTargetTheme() {
        var selectedTheme: AppThemeStyle? = null

        handleThemeStyleClick(
            currentThemeStyle = AppThemeStyle.Coast,
            selectedThemeStyle = AppThemeStyle.Forest,
            performHaptic = {},
            onThemeSelected = { selectedTheme = it },
        )

        assertEquals(AppThemeStyle.Forest, selectedTheme)
    }
}
