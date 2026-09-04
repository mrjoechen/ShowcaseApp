package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.remote.MusicPlatform

internal actual suspend fun resolveAlbumRequestBackend(
    platform: String,
    configuredUrl: suspend () -> String,
    configuredAuth: suspend () -> String?,
): AlbumRequestBackend {
    if (platform == MusicPlatform.Apple.key) {
        throw AlbumPlatformUnavailableException(platform)
    }

    val baseUrl = configuredUrl().ifBlank {
        throw IllegalStateException("music_api_baseurl is not configured")
    }
    return AlbumRequestBackend.MusicApi(
        baseUrl = baseUrl,
        authorization = null,
    )
}
