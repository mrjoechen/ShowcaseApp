package com.alpha.showcase.common.ui.source

import getPlatform
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import isIos

private const val GALLERY_MEDIA_DIR = "gallery_media"
private const val GALLERY_URI_PREFIX = "gallery://"

actual fun isGalleryLocalFileMissing(uri: String): Boolean {
    val normalized = uri.trim()
    if (normalized.isBlank()) return true
    if (normalized.startsWith("content://", ignoreCase = true)) return false

    val localPath = resolveGalleryLocalPath(normalized) ?: normalized.removePrefix("file://")
    return runCatching { !PlatformFile(localPath).exists() }.getOrDefault(true)
}

internal actual suspend fun PlatformFile.persistForGalleryIfNeeded(
    sourceName: String,
    displayName: String,
    fallbackUri: String,
): String? {
    // iOS selection must supply an asset identifier; never fall back to copying originals.
    return if (isIos()) null else fallbackUri
}

internal actual fun resolveGalleryLocalPath(uri: String): String? {
    val relativePath = extractGalleryRelativePath(uri)
    if (!relativePath.isNullOrBlank()) {
        return runCatching {
            PlatformFile(getPlatform().getConfigDirectory())
                .resolve(GALLERY_MEDIA_DIR)
                .resolve(relativePath)
                .path
        }.getOrNull()
    }

    val normalized = uri.trim()
    return when {
        normalized.startsWith("file://", ignoreCase = true) -> normalized.removePrefix("file://")
        normalized.startsWith("/") -> normalized
        else -> null
    }
}

private fun extractGalleryRelativePath(uri: String): String? {
    val normalized = uri.trim()
    if (normalized.startsWith(GALLERY_URI_PREFIX, ignoreCase = true)) {
        return normalized.removePrefix(GALLERY_URI_PREFIX).trimStart('/').takeIf { it.isNotBlank() }
    }

    val marker = "/$GALLERY_MEDIA_DIR/"
    val pathCandidate = normalized.removePrefix("file://")
    val markerIndex = pathCandidate.indexOf(marker)
    if (markerIndex < 0) return null
    return pathCandidate.substring(markerIndex + marker.length).takeIf { it.isNotBlank() }
}
