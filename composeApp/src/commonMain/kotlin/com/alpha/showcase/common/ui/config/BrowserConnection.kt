package com.alpha.showcase.common.ui.config

import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.utils.ConnectionProbeTimeoutException

internal enum class BrowserConnectionProblem {
    MixedContent,
    BrowserAccess,
}

internal class BrowserConnectionException(
    val problem: BrowserConnectionProblem,
) : Exception(problem.name)

internal fun RemoteApi.browserRequestBaseUrl(): String? = when (this) {
    is WebDav -> url
    is ImmichSource -> url
    is MTPhotoSource -> url
    is RssSource -> url
    is S3Source -> {
        val explicitScheme = endpoint.substringBefore("://", missingDelimiterValue = "")
            .lowercase()
            .takeIf { it == "http" || it == "https" }
        if (explicitScheme != null) endpoint else "${if (useSSL) "https" else "http"}://$endpoint"
    }
    else -> null
}

internal fun classifyBrowserConnectionProblem(
    pageProtocol: String?,
    baseUrl: String?,
    error: Throwable? = null,
): BrowserConnectionProblem? {
    if (pageProtocol == null) return null
    if (
        pageProtocol.equals("https:", ignoreCase = true) &&
        baseUrl?.trim()?.startsWith("http://", ignoreCase = true) == true
    ) {
        return BrowserConnectionProblem.MixedContent
    }
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
    baseUrl: String?,
    error: Throwable? = null,
): BrowserConnectionProblem?
