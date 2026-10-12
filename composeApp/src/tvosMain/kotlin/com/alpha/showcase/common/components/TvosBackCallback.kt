package com.alpha.showcase.common.components

import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo

/** Adapts our Boolean callback contract to the window's navigation dispatcher. */
internal class TvosBackCallback(
    enabled: Boolean,
    var onBack: () -> Boolean,
    private val dispatchUnhandledBack: () -> Unit,
) : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = enabled) {
    override fun onBackCompleted() {
        if (onBack()) return
        // Let the next enabled handler (or the window fallback) handle an unconsumed event.
        // Temporarily disable only this callback so nested handlers retain their precedence.
        val wasEnabled = isBackEnabled
        isBackEnabled = false
        try {
            dispatchUnhandledBack()
        } finally {
            isBackEnabled = wasEnabled
        }
    }
}
