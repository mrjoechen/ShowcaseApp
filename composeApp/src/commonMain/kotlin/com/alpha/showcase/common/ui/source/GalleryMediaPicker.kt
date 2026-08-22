package com.alpha.showcase.common.ui.source

import com.alpha.showcase.common.cache.GalleryMediaInput
import com.alpha.showcase.common.utils.getMimeType
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path

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

expect fun isGalleryLocalFileMissing(uri: String): Boolean

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

internal expect suspend fun PlatformFile.persistForGalleryIfNeeded(
    sourceName: String,
    displayName: String,
    fallbackUri: String,
): String?

internal expect fun resolveGalleryLocalPath(uri: String): String?
