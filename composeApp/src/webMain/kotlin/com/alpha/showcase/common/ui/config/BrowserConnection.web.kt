package com.alpha.showcase.common.ui.config

import kotlinx.browser.window

internal actual fun browserConnectionProblem(
    error: Throwable?,
): BrowserConnectionProblem? = classifyBrowserConnectionProblem(
    pageProtocol = window.location.protocol,
    error = error,
)
