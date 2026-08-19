package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FRAME_WALL
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SQUARE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_WATERFALL
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShowcasePlaybackPolicyTest {
    @Test
    fun stylePreviewOnlyActivatesTheSelectedPageOutsideEditMode() {
        assertTrue(
            isStylePreviewContentActive(
                editMode = false,
                currentPage = 2,
                page = 2
            )
        )
        assertFalse(
            isStylePreviewContentActive(
                editMode = false,
                currentPage = 2,
                page = 1
            )
        )
        assertFalse(
            isStylePreviewContentActive(
                editMode = true,
                currentPage = 2,
                page = 2
            )
        )
    }

    @Test
    fun imageOnlyModesAreExcludedFromVideoQueries() {
        assertTrue(supportsVideoForShowcaseMode(SHOWCASE_MODE_SLIDE))
        assertFalse(supportsVideoForShowcaseMode(SHOWCASE_MODE_FRAME_WALL))
        assertFalse(supportsVideoForShowcaseMode(SHOWCASE_MODE_SQUARE))
        assertFalse(supportsVideoForShowcaseMode(SHOWCASE_MODE_WATERFALL))
    }
}
