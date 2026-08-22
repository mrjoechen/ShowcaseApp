package com.alpha.showcase.common.cache

import com.alpha.showcase.common.utils.getMimeType
import getPlatform
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.resolve

private const val GALLERY_MEDIA_DIRECTORY = "gallery_media"
private const val GALLERY_URI_SCHEME = "gallery://"

internal actual suspend fun loadPersistedGalleryMediaInputs(sourceName: String): List<GalleryMediaInput> {
    val normalizedSource = sourceName.sanitizeAsPathSegment()
    val sourceDirectory = runCatching {
        PlatformFile(getPlatform().getConfigDirectory())
            .resolve(GALLERY_MEDIA_DIRECTORY)
            .resolve(normalizedSource)
    }.getOrNull() ?: return emptyList()

    val files = runCatching {
        if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
            emptyList()
        } else {
            sourceDirectory.list().filter { it.isRegularFile() }
        }
    }.getOrDefault(emptyList())

    return files.map { file ->
        GalleryMediaInput(
            mediaUri = "$GALLERY_URI_SCHEME$normalizedSource/${file.name}",
            displayName = file.name,
            mimeType = getMimeType(file.name).lowercase(),
        )
    }
}

internal actual suspend fun deletePersistedGalleryLocalFileIfNeeded(
    sourceName: String,
    mediaUri: String,
) {
    val relativePath = extractPersistedGalleryRelativePath(mediaUri) ?: return
    val normalizedSource = sourceName.sanitizeAsPathSegment()
    if (!relativePath.startsWith("$normalizedSource/")) return

    val target = runCatching {
        PlatformFile(getPlatform().getConfigDirectory())
            .resolve(GALLERY_MEDIA_DIRECTORY)
            .resolve(relativePath)
    }.getOrNull() ?: return
    runCatching { target.delete(mustExist = false) }
}

private fun String.sanitizeAsPathSegment(): String {
    return replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "gallery_source" }
}

private fun extractPersistedGalleryRelativePath(uri: String): String? {
    val normalized = uri.trim()
    if (normalized.startsWith(GALLERY_URI_SCHEME, ignoreCase = true)) {
        return normalized.removePrefix(GALLERY_URI_SCHEME).trimStart('/').takeIf { it.isNotBlank() }
    }

    val marker = "/$GALLERY_MEDIA_DIRECTORY/"
    val pathCandidate = normalized.removePrefix("file://")
    val markerIndex = pathCandidate.indexOf(marker)
    if (markerIndex < 0) return null
    return pathCandidate.substring(markerIndex + marker.length).takeIf { it.isNotBlank() }
}
