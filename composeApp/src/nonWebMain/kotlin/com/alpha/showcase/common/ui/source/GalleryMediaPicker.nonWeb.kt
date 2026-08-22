package com.alpha.showcase.common.ui.source

import getPlatform
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.write
import isIos
import okio.ByteString.Companion.toByteString

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
    if (!isIos()) return fallbackUri

    return runCatching {
        val sourceRoot = PlatformFile(getPlatform().getConfigDirectory())
            .resolve(GALLERY_MEDIA_DIR)
            .resolve(sourceName.sanitizeAsPathSegment())
        sourceRoot.createDirectories()

        val bytes = readBytes()
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val hash = bytes.toByteString().sha256().hex()
        val fileName = if (extension.isNotBlank()) "$hash.$extension" else hash
        val target = sourceRoot.resolve(fileName)
        if (!target.exists()) {
            target write bytes
        }
        "$GALLERY_URI_PREFIX${sourceName.sanitizeAsPathSegment()}/$fileName"
    }.getOrElse {
        it.printStackTrace()
        null
    }
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

private fun String.sanitizeAsPathSegment(): String {
    return replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "gallery_source" }
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
