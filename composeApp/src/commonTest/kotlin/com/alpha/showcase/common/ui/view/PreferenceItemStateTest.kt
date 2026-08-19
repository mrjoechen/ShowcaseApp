package com.alpha.showcase.common.ui.view

import kotlin.test.Test
import kotlin.test.assertEquals

class PreferenceItemStateTest {

    private val levels = listOf(
        LevelOption(value = 1, label = "Very slow"),
        LevelOption(value = 3, label = "Slow"),
        LevelOption(value = 5, label = "Medium"),
        LevelOption(value = 7, label = "Fast"),
        LevelOption(value = 10, label = "Very fast")
    )

    @Test
    fun `late external echo does not roll level slider back`() {
        val state = LevelSelectionState(value = 1, levels = levels)
        state.selectIndex(1)
        state.selectIndex(2)

        state.syncExternalValue(3)

        assertEquals(2, state.selectedIndex)
    }

    @Test
    fun `off-list value selects nearest semantic level`() {
        val state = LevelSelectionState(value = 1, levels = levels)

        state.syncExternalValue(8)

        assertEquals(3, state.selectedIndex)
    }

}
