package com.alpha.showcase.common.ui.source

import com.alpha.showcase.common.cache.GalleryMediaInput
import com.alpha.showcase.common.utils.getMimeType
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.write
import isIos
import getPlatform
import okio.ByteString.Companion.toByteString

private const val GALLERY_MEDIA_DIR = "gallery_media"
private const val GALLERY_URI_PREFIX = "gallery://"

/**
 * Normalize picker output for long-term storage:
 * - iOS picker returns temporary files, so we copy to app private directory.
 * - Android keeps content uri and relies on persistable uri permission.
 */
suspend fun PlatformFile.toGalleryMediaInput(sourceName: String): GalleryMediaInput? {
    val rawUri = path.trim()
    if (rawUri.isBlank()) return null

    val displayName = name.ifBlank { rawUri.substringAfterLast('/') }
    val mimeType = resolveGalleryMimeType(this, displayName)
    val persistedUri = persistForGalleryIfNeeded(
        sourceName = sourceName,
        displayName = displayName,
        fallbackUri = rawUri,
    ) ?: return null

    return GalleryMediaInput(
        mediaUri = persistedUri,
        displayName = displayName,
        mimeType = mimeType,
    )
}

fun toGalleryDisplayUri(uri: String): String {
    val normalized = uri.trim()
    if (normalized.isBlank()) return normalized
    if (normalized.startsWith("content://", ignoreCase = true)) return normalized

    val resolvedLocalPath = resolveGalleryLocalPath(normalized)
    if (resolvedLocalPath != null) {
        return if (resolvedLocalPath.startsWith("/")) {
            "file://$resolvedLocalPath"
        } else {
            resolvedLocalPath
        }
    }

    if (normalized.startsWith("file://", ignoreCase = true)) return normalized
    return if (normalized.startsWith("/")) "file://$normalized" else normalized
}

fun isGalleryLocalFileMissing(uri: String): Boolean {
    val normalized = uri.trim()
    if (normalized.isBlank()) return true
    if (normalized.startsWith("content://", ignoreCase = true)) return false

    val localPath = resolveGalleryLocalPath(normalized) ?: normalized.removePrefix("file://")
    return runCatching { !PlatformFile(localPath).exists() }.getOrDefault(true)
}

private fun resolveGalleryMimeType(file: PlatformFile, displayName: String): String {
    val byPlatform = runCatching { file.mimeType()?.toString() }
        .getOrNull()
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()

    if (byPlatform.startsWith("image/")) return byPlatform

    val byName = getMimeType(displayName).lowercase()
    if (byName.startsWith("image/")) return byName

    return "image/jpeg"
}

private suspend fun PlatformFile.persistForGalleryIfNeeded(
    sourceName: String,
    displayName: String,
    fallbackUri: String,
): String? {
    if (!isIos()) {
        return fallbackUri
    }

    return runCatching {
        val sourceRoot = PlatformFile(getPlatform().getConfigDirectory())
            .resolve(GALLERY_MEDIA_DIR)
            .resolve(sourceName.sanitizeAsPathSegment())
        sourceRoot.createDirectories()

        val bytes = readBytes()
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val hash = bytes.toByteString().sha256().hex()
        val fileName = if (ext.isNotBlank()) "$hash.$ext" else hash
        val target = sourceRoot.resolve(fileName)

        if (!target.exists()) {
            target write bytes
        }
        buildGalleryStoredUri(sourceName, fileName)
    }.getOrElse {
        it.printStackTrace()
        null
    }
}

private fun String.sanitizeAsPathSegment(): String {
    val sanitized = replace(Regex("[^A-Za-z0-9._-]"), "_")
    return sanitized.ifBlank { "gallery_source" }
}

private fun buildGalleryStoredUri(sourceName: String, fileName: String): String {
    return "$GALLERY_URI_PREFIX${sourceName.sanitizeAsPathSegment()}/$fileName"
}

private fun resolveGalleryLocalPath(uri: String): String? {
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
    if (normalized.startsWith("file://", ignoreCase = true)) {
        return normalized.removePrefix("file://")
    }
    if (normalized.startsWith("/")) {
        return normalized
    }
    return null
}

private fun extractGalleryRelativePath(uri: String): String? {
    val normalized = uri.trim()

    if (normalized.startsWith(GALLERY_URI_PREFIX, ignoreCase = true)) {
        return normalized
            .removePrefix(GALLERY_URI_PREFIX)
            .trimStart('/')
            .takeIf { it.isNotBlank() }
    }

    val pathCandidate = normalized.removePrefix("file://")
    val marker = "/$GALLERY_MEDIA_DIR/"
    val markerIndex = pathCandidate.indexOf(marker)
    if (markerIndex < 0) return null

    return pathCandidate
        .substring(markerIndex + marker.length)
        .takeIf { it.isNotBlank() }
}
