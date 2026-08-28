package com.alpha.showcase.common.ui.play

private const val USER_SCROLL_PAUSE_MILLIS = 3_000L

internal class WaterfallAutoScrollPauseState(
    private val pauseDurationMillis: Long = USER_SCROLL_PAUSE_MILLIS
) {
    private var lastUserScrollMillis: Long? = null

    fun onUserScroll(nowMillis: Long) {
        lastUserScrollMillis = nowMillis
    }

    fun canAutoScroll(nowMillis: Long): Boolean {
        val lastScrollMillis = lastUserScrollMillis ?: return true
        return nowMillis - lastScrollMillis >= pauseDurationMillis
    }
}
