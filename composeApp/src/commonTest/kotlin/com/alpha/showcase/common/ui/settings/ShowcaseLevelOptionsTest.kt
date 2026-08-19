package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.ui.view.LevelOption
import kotlin.test.Test
import kotlin.test.assertEquals

class ShowcaseLevelOptionsTest {

    @Test
    fun `numeric levels use semantic labels`() {
        val options = levelOptions(
            values = listOf(1, 3, 5, 7, 10),
            labels = listOf("Very slow", "Slow", "Medium", "Fast", "Very fast")
        )

        assertEquals(
            listOf(
                LevelOption(1, "Very slow"),
                LevelOption(3, "Slow"),
                LevelOption(5, "Medium"),
                LevelOption(7, "Fast"),
                LevelOption(10, "Very fast")
            ),
            options
        )
    }
}
