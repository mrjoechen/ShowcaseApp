package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.theme.AppThemeStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeStylePickerBehaviorTest {

    @Test
    fun selectedThemeDeterminesInitialVisibleItem() {
        assertEquals(
            10,
            themeStylePickerInitialIndex(AppThemeStyle.Signal),
        )
    }

    @Test
    fun draggingLeftScrollsTowardLaterThemes() {
        assertEquals(
            24f,
            themeStylePickerDragScrollDelta(dragAmount = -24f),
        )
    }

    @Test
    fun draggingRightScrollsTowardEarlierThemes() {
        assertEquals(
            -24f,
            themeStylePickerDragScrollDelta(dragAmount = 24f),
        )
    }
}
