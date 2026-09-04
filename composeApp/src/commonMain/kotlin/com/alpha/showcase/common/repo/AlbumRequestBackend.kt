package com.alpha.showcase.common.repo

internal sealed interface AlbumRequestBackend {
    data object AppleMusic : AlbumRequestBackend

    data class MusicApi(
        val baseUrl: String,
        val authorization: String?,
    ) : AlbumRequestBackend
}

internal class AlbumPlatformUnavailableException(
    val platform: String,
) : Exception("Album platform is unavailable in the current runtime: $platform")

internal expect suspend fun resolveAlbumRequestBackend(
    platform: String,
    configuredUrl: suspend () -> String,
    configuredAuth: suspend () -> String?,
): AlbumRequestBackend

internal fun Throwable.isAlbumPlatformUnavailable(): Boolean =
    generateSequence(this) { it.cause }
        .any { it is AlbumPlatformUnavailableException }
