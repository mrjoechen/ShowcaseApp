package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.remote.MusicPlatform

internal actual suspend fun resolveAlbumRequestBackend(
    platform: String,
    configuredUrl: suspend () -> String,
    configuredAuth: suspend () -> String?,
): AlbumRequestBackend {
    if (platform == MusicPlatform.Apple.key) {
        return AlbumRequestBackend.AppleMusic
    }

    val baseUrl = configuredUrl().ifBlank {
        throw IllegalStateException("music_api_baseurl is not configured")
    }
    val auth = configuredAuth()?.takeIf { it.isNotBlank() }
    return AlbumRequestBackend.MusicApi(
        baseUrl = baseUrl,
        authorization = auth?.let { "Basic $it" },
    )
}
