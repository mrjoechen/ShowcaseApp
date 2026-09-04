package com.alpha.showcase.common.ui.config

import kotlinx.browser.window

internal actual fun browserConnectionProblem(
    baseUrl: String?,
    error: Throwable?,
): BrowserConnectionProblem? = classifyBrowserConnectionProblem(
    pageProtocol = window.location.protocol,
    baseUrl = baseUrl,
    error = error,
)
