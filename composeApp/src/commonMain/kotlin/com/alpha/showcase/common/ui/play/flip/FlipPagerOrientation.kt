package com.alpha.showcase.common.ui.play.flip

sealed class FlipPagerOrientation(val value: Int) {
    data object Horizontal : FlipPagerOrientation(0)
    data object Vertical : FlipPagerOrientation(1)
}