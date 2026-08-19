package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FRAME_WALL
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SQUARE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_WATERFALL

fun isStylePreviewContentActive(
    editMode: Boolean,
    currentPage: Int,
    page: Int
): Boolean = !editMode && currentPage == page

fun supportsVideoForShowcaseMode(mode: Int): Boolean = mode !in setOf(
    SHOWCASE_MODE_FRAME_WALL,
    SHOWCASE_MODE_SQUARE,
    SHOWCASE_MODE_WATERFALL
)
