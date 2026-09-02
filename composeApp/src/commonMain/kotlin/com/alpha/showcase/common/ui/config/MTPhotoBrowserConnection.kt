package com.alpha.showcase.common.ui.config

internal enum class MTPhotoBrowserConnectionProblem {
    MixedContent,
    BrowserAccess,
}

internal class MTPhotoBrowserConnectionException(
    val problem: MTPhotoBrowserConnectionProblem,
) : Exception(problem.name)

internal class MTPhotoAlbumLoadTimeoutException(timeoutMillis: Long) :
    Exception("MTPhoto album loading timed out after ${timeoutMillis / 1_000} seconds")

internal fun classifyMTPhotoBrowserConnectionProblem(
    pageProtocol: String?,
    baseUrl: String,
    error: Throwable? = null,
): MTPhotoBrowserConnectionProblem? {
    if (pageProtocol == null) return null
    if (
        pageProtocol.equals("https:", ignoreCase = true) &&
        baseUrl.trim().startsWith("http://", ignoreCase = true)
    ) {
        return MTPhotoBrowserConnectionProblem.MixedContent
    }
    if (error is MTPhotoAlbumLoadTimeoutException) {
        return MTPhotoBrowserConnectionProblem.BrowserAccess
    }

    val browserNetworkFailure = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.lowercase() }
        .any { message ->
            message.contains("failed to fetch") ||
                message.contains("networkerror") ||
                message.contains("network request failed") ||
                message.contains("load failed") ||
                message.contains("cors") ||
                message.contains("access-control") ||
                message.contains("private network")
        }
    return MTPhotoBrowserConnectionProblem.BrowserAccess.takeIf { browserNetworkFailure }
}

internal expect fun currentMTPhotoBrowserPageProtocol(): String?
