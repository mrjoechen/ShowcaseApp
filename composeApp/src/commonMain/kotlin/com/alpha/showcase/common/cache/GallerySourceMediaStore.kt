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
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve

data class GalleryMediaInput(
    val mediaUri: String,
    val displayName: String,
    val mimeType: String,
)

data class GalleryMediaRecord(
    val sourceName: String,
    val mediaUri: String,
    val displayName: String,
    val mimeType: String,
    val addedAt: Long,
)

class GallerySourceMediaStore(
    private val database: SourceCacheDatabase = SourceCacheDatabaseProvider.database
) {
    companion object {
        private const val GALLERY_MEDIA_DIR = "gallery_media"
        private const val GALLERY_URI_PREFIX = "gallery://"
    }

    private val dao = database.gallerySourceMediaDao()

    suspend fun addMedias(sourceName: String, medias: List<GalleryMediaInput>): Int {
        if (sourceName.isBlank() || medias.isEmpty()) return 0

        val normalized = medias
            .mapNotNull { input ->
                val mediaUri = input.mediaUri.trim()
                if (mediaUri.isBlank()) {
                    null
                } else {
                    com.alpha.showcase.common.cache.entity.GallerySourceMedia(
                        sourceName = sourceName,
                        mediaUri = mediaUri,
                        displayName = input.displayName.ifBlank { mediaUri.substringAfterLast('/') },
                        mimeType = input.mimeType.lowercase(),
                    )
                }
            }
            .distinctBy { it.mediaUri }

        if (normalized.isEmpty()) return 0

        return dao.insertOrIgnore(normalized).count { it != -1L }
    }

    suspend fun listMedias(sourceName: String): List<GalleryMediaRecord> {
        if (sourceName.isBlank()) return emptyList()
        return dao.getBySource(sourceName).map {
            GalleryMediaRecord(
                sourceName = it.sourceName,
                mediaUri = it.mediaUri,
                displayName = it.displayName,
                mimeType = it.mimeType,
                addedAt = it.addedAt,
            )
        }
    }

    suspend fun restoreMediasFromPersistedFiles(sourceName: String): Int {
        if (sourceName.isBlank()) return 0

        val existing = dao.getBySource(sourceName)
        if (existing.isNotEmpty()) return 0

        val sourceDir = runCatching {
            PlatformFile(getPlatform().getConfigDirectory())
                .resolve(GALLERY_MEDIA_DIR)
                .resolve(sourceName.sanitizeAsPathSegment())
        }.getOrNull() ?: return 0

        val files = runCatching {
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                emptyList()
            } else {
                sourceDir.list().filter { it.isRegularFile() }
            }
        }.getOrDefault(emptyList())

        if (files.isEmpty()) return 0

        val medias = files.map { file ->
            GalleryMediaInput(
                mediaUri = "$GALLERY_URI_PREFIX${sourceName.sanitizeAsPathSegment()}/${file.name}",
                displayName = file.name,
                mimeType = getMimeType(file.name).lowercase(),
            )
        }

        return addMedias(sourceName, medias)
    }

    suspend fun deleteMedias(sourceName: String, mediaUris: List<String>) {
        if (sourceName.isBlank() || mediaUris.isEmpty()) return
        val normalizedUris = mediaUris.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedUris.isEmpty()) return

        dao.deleteBySourceAndUris(sourceName, normalizedUris)

        normalizedUris.forEach { mediaUri ->
            deletePersistedGalleryLocalFileIfNeeded(sourceName, mediaUri)
        }
    }

    suspend fun deleteSource(sourceName: String) {
        if (sourceName.isBlank()) return
        dao.deleteBySource(sourceName)
    }
}

private fun String.sanitizeAsPathSegment(): String {
    return replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "gallery_source" }
}

private suspend fun deletePersistedGalleryLocalFileIfNeeded(sourceName: String, mediaUri: String) {
    val relativePath = extractPersistedGalleryRelativePath(mediaUri) ?: return
    val normalizedSource = sourceName.sanitizeAsPathSegment()
    if (!relativePath.startsWith("$normalizedSource/")) return

    val target = runCatching {
        PlatformFile(getPlatform().getConfigDirectory())
            .resolve("gallery_media")
            .resolve(relativePath)
    }.getOrNull() ?: return

    runCatching {
        target.delete(mustExist = false)
    }
}

private fun extractPersistedGalleryRelativePath(uri: String): String? {
    val normalized = uri.trim()

    val galleryPrefix = "gallery://"
    if (normalized.startsWith(galleryPrefix, ignoreCase = true)) {
        return normalized
            .removePrefix(galleryPrefix)
            .trimStart('/')
            .takeIf { it.isNotBlank() }
    }

    val pathCandidate = normalized.removePrefix("file://")
    val marker = "/gallery_media/"
    val markerIndex = pathCandidate.indexOf(marker)
    if (markerIndex < 0) return null

    return pathCandidate
        .substring(markerIndex + marker.length)
        .takeIf { it.isNotBlank() }
}
