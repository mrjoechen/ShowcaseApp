package com.alpha.showcase.common.ui.config

import com.alpha.showcase.common.utils.ConnectionProbeTimeoutException

internal enum class BrowserConnectionProblem {
    BrowserAccess,
}

internal fun classifyBrowserConnectionProblem(
    pageProtocol: String?,
    error: Throwable? = null,
): BrowserConnectionProblem? {
    if (pageProtocol == null) return null
    if (error is ConnectionProbeTimeoutException) {
        return BrowserConnectionProblem.BrowserAccess
    }

    val browserNetworkFailure = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.lowercase() }
        .any { message ->
            message.contains("failed to fetch") ||
                message.contains("networkerror") ||
                message.contains("network request failed") ||
                message.contains("load failed") ||
                message.contains("timeout") ||
                message.contains("timed out") ||
                message.contains("cors") ||
                message.contains("access-control") ||
                message.contains("private network")
        }
    return BrowserConnectionProblem.BrowserAccess.takeIf { browserNetworkFailure }
}

internal expect fun browserConnectionProblem(
    error: Throwable?,
): BrowserConnectionProblem?
